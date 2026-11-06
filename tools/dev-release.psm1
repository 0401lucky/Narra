#requires -Version 7.2
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:DevCertificate = '3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8'
$script:MetadataUrl = 'https://0401lucky.github.io/Narra/updates/dev.json'
$script:DownloadBase = 'https://download.lsa1230.dpdns.org/dev/'
$script:ApkContentType = 'application/vnd.android.package-archive'

function Get-DevVersion {
    param([Parameter(Mandatory)][string]$Version, [string]$PropertiesPath)
    if ($Version -cnotmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)$') {
        throw '版本必须是规范的 major.minor.patch，minor / patch 必须小于 100。'
    }
    $parts = $Version.Split('.')
    $code = [long]$parts[0] * 10000 + [int]$parts[1] * 100 + [int]$parts[2]
    if ($code -le 0 -or $code -gt 2100000000) { throw 'versionCode 超出 Android 范围。' }
    if ($PropertiesPath) {
        $properties = @{}
        foreach ($line in Get-Content -LiteralPath $PropertiesPath) {
            if ($line -match '^\s*([^#!\s][^=]*)=(.*)$') {
                $name = $Matches[1].Trim()
                if ($properties.ContainsKey($name)) { throw "重复的 Gradle 属性：$name" }
                $properties[$name] = $Matches[2].Trim()
            }
        }
        $configured = '{0}.{1}.{2}' -f $properties.appVersionMajorOverride, $properties.appVersionMinorOverride, $properties.appVersionPatchOverride
        if ($configured -cne $Version -or $properties.narraApplicationId -cne 'com.narra.app' -or
            $properties.appUpdateMetadataBaseUrl -cne 'https://0401lucky.github.io/Narra/updates' -or
            ($properties.ContainsKey('appVersionSuffixOverride') -and $properties.appVersionSuffixOverride)) {
            throw '输入版本、包名或更新入口与已提交的 Gradle 配置不一致。'
        }
    }
    [pscustomobject]@{ Version = $Version; Name = "$Version-dev"; Code = [int]$code; ApkName = "Narra-v$Version-dev-$code-dev.apk" }
}

function Assert-DevContext {
    if ($env:GITHUB_ACTIONS -cne 'true' -or $env:GITHUB_EVENT_NAME -cne 'workflow_dispatch' -or $env:GITHUB_REPOSITORY -cne '0401lucky/Narra' -or
        $env:GITHUB_REF -cne 'refs/heads/main' -or $env:GITHUB_SHA -cnotmatch '^[a-f0-9]{40}$' -or
        $env:GITHUB_RUN_ID -cnotmatch '^[1-9][0-9]*$' -or $env:GITHUB_RUN_ATTEMPT -cnotmatch '^[1-9][0-9]*$') {
        throw '发版仅允许本仓库 main 的 GitHub Actions 运行。'
    }
}

function Invoke-DevCommand {
    param([Parameter(Mandatory)][string]$FilePath, [string[]]$Arguments = @())
    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $FilePath
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $start
    try {
        if (-not $process.Start()) { throw '启动失败' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(300000)) { $process.Kill($true); throw '执行超时' }
        [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $stdout.GetAwaiter().GetResult(); Error = $stderr.GetAwaiter().GetResult() }
    } finally { $process.Dispose() }
}

function Assert-DevCommand {
    param($Result, [string]$Stage)
    # 外部 stderr 可能携带认证上下文，不原样写入 Actions 日志。
    if ($Result.ExitCode -ne 0) { throw "$Stage 失败（退出码 $($Result.ExitCode)）。" }
    $Result.Output.Trim()
}

function Get-DevSha256 {
    param([string]$Path)
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Read-DevJson {
    param([string]$Path)
    Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Get-DevApkUrl {
    param([string]$Version)
    $target = Get-DevVersion $Version
    "$script:DownloadBase$($target.ApkName)?v=$($target.Code)"
}

function Assert-DevMetadata {
    param($Metadata)
    if ($Metadata.app_id -cne 'com.narra.app.dev' -or $Metadata.channel -cne 'dev' -or
        $Metadata.latest_version_name -cnotmatch '^([0-9]+\.[0-9]+\.[0-9]+)-dev$') { throw '更新清单的应用、渠道或版本名无效。' }
    $target = Get-DevVersion $Matches[1]
    if ($Metadata.latest_version_code -ne $target.Code -or $Metadata.minimum_supported_version_code -ne 0 -or
        $Metadata.apk_sha256 -cnotmatch '^[a-f0-9]{64}$') { throw '更新清单的版本、可选更新或 SHA-256 无效。' }
    $uri = $null
    if (-not [uri]::TryCreate($Metadata.apk_url, [UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -cne 'https' -or $uri.Host -cne 'download.lsa1230.dpdns.org' -or
        $uri.Port -ne 443 -or $uri.UserInfo -or $uri.Fragment -or
        $uri.AbsolutePath -cne "/dev/$($target.ApkName)") { throw 'APK 地址不符合既有客户端白名单。' }
    if ($Metadata.release_notes -isnot [array] -or @($Metadata.release_notes).Count -eq 0 -or @($Metadata.release_notes | Where-Object { $_ -isnot [string] -or [string]::IsNullOrWhiteSpace($_) }).Count) {
        throw '发行说明必须包含非空文本。'
    }
}

function Assert-DevMonotonic {
    param($Current, $Candidate)
    Assert-DevMetadata $Current
    Assert-DevMetadata $Candidate
    if ($Current.latest_version_code -gt $Candidate.latest_version_code) { throw '拒绝降级已发布版本。' }
    if ($Current.latest_version_code -eq $Candidate.latest_version_code -and
        ($Current.apk_sha256 -cne $Candidate.apk_sha256 -or $Current.apk_url -cne $Candidate.apk_url)) {
        throw '拒绝用不同 APK 替换相同版本。'
    }
}

function Get-DevPublicMetadata {
    param([ValidateRange(1, 4)][int]$Attempts = 4)
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri $script:MetadataUrl -TimeoutSec 30 -MaximumRedirection 0
            if ([int]$response.StatusCode -ne 200) { throw 'HTTP 失败' }
            $metadata = $response.Content | ConvertFrom-Json
            Assert-DevMetadata $metadata
            return $metadata
        } catch {
            if ($attempt -eq $Attempts) { throw '无法读取有效的公网 dev 更新清单。' }
            Start-Sleep -Seconds (2 * $attempt)
        }
    }
}

function Assert-DevArtifact {
    param($Info, [string]$ApkPath, [string]$Version, [switch]$CheckRun)
    $target = Get-DevVersion $Version
    if ($Info.schema_version -ne 1 -or $Info.repository -cne '0401lucky/Narra' -or
        $Info.source_sha -cnotmatch '^[a-f0-9]{40}$' -or $Info.run_id -cnotmatch '^[1-9][0-9]*$' -or
        $Info.run_attempt -lt 1 -or $Info.application_id -cne 'com.narra.app.dev' -or $Info.channel -cne 'dev' -or
        $Info.version -cne $Version -or $Info.version_name -cne $target.Name -or $Info.version_code -ne $target.Code -or
        $Info.apk_name -cne $target.ApkName -or $Info.apk_sha256 -cnotmatch '^[a-f0-9]{64}$' -or
        $Info.signing_certificate_sha256 -cne $script:DevCertificate) { throw '构建来源、包信息或原 dev 证书不匹配。' }
    if ($Info.run_url -cne "https://github.com/0401lucky/Narra/actions/runs/$($Info.run_id)") { throw '来源 run URL 无效。' }
    if ($CheckRun -and ($Info.source_sha -cne $env:GITHUB_SHA -or [string]$Info.run_id -cne $env:GITHUB_RUN_ID -or
        $Info.run_attempt -gt [int]$env:GITHUB_RUN_ATTEMPT)) { throw 'artifact 不属于本次 run 的已完成构建。' }
    if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf) -or (Get-Item -LiteralPath $ApkPath).Length -ne $Info.apk_size -or
        (Get-DevSha256 $ApkPath) -cne $Info.apk_sha256) { throw 'artifact APK 大小或 SHA-256 不匹配。' }
    if ($Info.release_notes -isnot [array] -or @($Info.release_notes).Count -eq 0 -or @($Info.release_notes | Where-Object { $_ -isnot [string] -or [string]::IsNullOrWhiteSpace($_) }).Count) { throw 'artifact 缺少发行说明。' }
}

function Test-DevKeystore {
    param([string]$Path)
    $oldPassword = $env:NARRA_DEV_STORE_PASSWORD
    try {
        $env:NARRA_DEV_STORE_PASSWORD = 'android'
        $result = Invoke-DevCommand keytool @('-J-Duser.language=en', '-J-Duser.country=US', '-list', '-v', '-keystore', $Path, '-alias', 'androiddebugkey', '-storepass:env', 'NARRA_DEV_STORE_PASSWORD')
        $details = Assert-DevCommand $result '读取 dev 签名'
        if ($details -notmatch 'SHA256:\s*([0-9A-Fa-f:]+)' -or $Matches[1].Replace(':', '').ToLowerInvariant() -cne $script:DevCertificate) {
            throw '还原的 keystore 不是既有 dev 证书。'
        }
    } finally { $env:NARRA_DEV_STORE_PASSWORD = $oldPassword }
}

function Assert-DevApkIdentity {
    param([string]$Badging, [string]$Signature, [string]$Version)
    $target = Get-DevVersion $Version
    if ($Badging -notmatch "(?m)^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'" -or
        $Matches[1] -cne 'com.narra.app.dev' -or $Matches[2] -ne [string]$target.Code -or $Matches[3] -cne $target.Name) { throw '实际 APK 包名或版本不正确。' }
    $certificates = [regex]::Matches($Signature, 'Signer #[0-9]+ certificate SHA-256 digest:\s*([a-fA-F0-9]+)')
    if ($certificates.Count -ne 1 -or $certificates[0].Groups[1].Value.ToLowerInvariant() -cne $script:DevCertificate) { throw 'APK 签名证书与既有 dev 证书不一致。' }
}

function New-DevArtifact {
    param([string]$RepoRoot, [string]$Version, [string]$ArtifactDirectory)
    $source = Assert-DevCommand (Invoke-DevCommand git @('-C', $RepoRoot, 'rev-parse', 'HEAD')) '检查实际构建提交'
    $dirty = Assert-DevCommand (Invoke-DevCommand git @('-C', $RepoRoot, '-c', 'core.fileMode=false', 'status', '--porcelain', '--untracked-files=no')) '检查构建源码未漂移'
    if ($source -cne $env:GITHUB_SHA -or $dirty) { throw '构建来源与锁定源码不一致。' }
    $target = Get-DevVersion $Version (Join-Path $RepoRoot 'gradle.properties')
    $notes = Read-DevJson (Join-Path $RepoRoot 'docs/updates/dev-release-notes.json')
    if ($notes.version -cne $Version) { throw '发行说明版本与构建版本不一致。' }
    $apk = Join-Path $RepoRoot "app/build/outputs/apk/debug/$($target.ApkName)"
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
        Where-Object Name -Match '^\d+\.\d+\.\d+$' | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (-not $buildTools) { throw '缺少 Android build-tools。' }
    $aapt = Join-Path $buildTools.FullName $(if ($IsWindows) { 'aapt2.exe' } else { 'aapt2' })
    $badging = Assert-DevCommand (Invoke-DevCommand $aapt @('dump', 'badging', $apk)) '检查 APK 包信息'
    $signature = Assert-DevCommand (Invoke-DevCommand java @('-jar', (Join-Path $buildTools.FullName 'lib/apksigner.jar'), 'verify', '--verbose', '--print-certs', $apk)) '校验 APK 签名'
    Assert-DevApkIdentity $badging $signature $Version
    $javaVersion = Assert-DevCommand (Invoke-DevCommand java @('--version')) '读取 JDK 版本'
    $info = [ordered]@{
        schema_version = 1; repository = '0401lucky/Narra'; source_sha = $env:GITHUB_SHA
        run_id = $env:GITHUB_RUN_ID; run_attempt = [int]$env:GITHUB_RUN_ATTEMPT
        run_url = "https://github.com/0401lucky/Narra/actions/runs/$env:GITHUB_RUN_ID"
        version = $Version; version_name = $target.Name; version_code = $target.Code
        application_id = 'com.narra.app.dev'; channel = 'dev'; apk_name = $target.ApkName
        apk_size = (Get-Item -LiteralPath $apk).Length; apk_sha256 = Get-DevSha256 $apk
        signing_certificate_sha256 = $script:DevCertificate; built_at = [DateTimeOffset]::UtcNow.ToString('o')
        apk_verification_build_tools = $buildTools.Name; java_version = ($javaVersion -split "`n")[0].Trim()
        release_notes = @($notes.release_notes)
    }
    Assert-DevArtifact ([pscustomobject]$info) $apk $Version -CheckRun
    New-Item -ItemType Directory -Path $ArtifactDirectory -Force | Out-Null
    Copy-Item -LiteralPath $apk -Destination (Join-Path $ArtifactDirectory $target.ApkName)
    $info | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'release-info.json') -Encoding utf8
    [pscustomobject]$info
}

function New-DevMetadata {
    param($Info, [string]$ApkPath, [string]$OutputPath, [string]$PublishedAt)
    & (Join-Path $PSScriptRoot 'update-app-update-json.ps1') -Channel dev -VersionName $Info.version_name -VersionCode $Info.version_code `
        -ApkUrl (Get-DevApkUrl $Info.version) -ApkPath $ApkPath -MinimumSupportedVersionCode 0 -PublishedAt $PublishedAt `
        -ReleaseNotes @($Info.release_notes) -OutputPath $OutputPath
    $metadata = Read-DevJson $OutputPath
    Assert-DevMetadata $metadata
    $metadata
}

function Get-DevR2Endpoint {
    param([string]$AccountId, [ValidateSet('default', 'eu', 'us', 'fedramp')][string]$Jurisdiction = 'default')
    if ($AccountId -cnotmatch '^[a-f0-9]{32}$') { throw 'Cloudflare Account ID 无效或缺失。' }
    $suffix = if ($Jurisdiction -eq 'default') { '' } else { ".$Jurisdiction" }
    "https://$AccountId$suffix.r2.cloudflarestorage.com"
}

function Get-DevS3ErrorCode {
    param($Result)
    if ($Result.Error -match 'An error occurred \(([A-Za-z0-9]+)\)') { return $Matches[1] }
    'Unknown'
}

function Test-DevPublicApk {
    param([string]$Url, $Info, [string]$DownloadPath)
    for ($attempt = 1; $attempt -le 4; $attempt++) {
        try {
            $head = Invoke-WebRequest -Method Head -Uri $Url -TimeoutSec 60 -MaximumRedirection 0
            if ([int]$head.StatusCode -ne 200 -or [long]$head.Headers['Content-Length'][0] -ne $Info.apk_size -or
                [string]$head.Headers['Content-Type'][0] -cne $script:ApkContentType) { throw '公开 HEAD 不匹配' }
            Invoke-WebRequest -Uri $Url -OutFile $DownloadPath -TimeoutSec 180 -MaximumRedirection 0
            if ((Get-Item -LiteralPath $DownloadPath).Length -ne $Info.apk_size -or (Get-DevSha256 $DownloadPath) -cne $Info.apk_sha256) { throw '公开内容哈希不匹配' }
            return
        } catch {
            if ($attempt -eq 4) { throw '公开 APK 的 HTTP、类型、大小或 SHA-256 验证失败。' }
            Start-Sleep -Seconds (2 * $attempt)
        }
    }
}

function Publish-DevApk {
    param($Info, [string]$ApkPath, [string]$Endpoint, [string]$ScratchDirectory,
        [scriptblock]$S3 = { param($arguments) Invoke-DevCommand aws $arguments },
        [scriptblock]$VerifyPublic = { param($url, $info, $path) Test-DevPublicApk $url $info $path })
    Assert-DevArtifact $Info $ApkPath $Info.version
    $key = "dev/$($Info.apk_name)"
    $common = @('--endpoint-url', $Endpoint, '--region', 'auto', '--no-cli-pager', '--cli-connect-timeout', '20', '--cli-read-timeout', '120')
    $object = @('--bucket', 'narra-updates', '--key', $key)
    $head = & $S3 ($common + @('s3api', 'head-object') + $object)
    if ($head.ExitCode -ne 0) {
        $code = Get-DevS3ErrorCode $head
        if ($code -notin @('404', 'NoSuchKey', 'NotFound')) { throw "R2 对象预检失败（$code），没有执行上传。" }
        # 服务端原子条件写入，防止 HEAD 后另一发布者抢先写入同名对象。
        $put = & $S3 ($common + @('s3api', 'put-object') + $object + @('--body', $ApkPath, '--content-type', $script:ApkContentType, '--if-none-match', '*'))
        if ($put.ExitCode -ne 0 -and (Get-DevS3ErrorCode $put) -notin @('412', 'PreconditionFailed')) { throw 'R2 上传失败，未推进更新清单。' }
    }
    $download = Join-Path $ScratchDirectory 'r2-readback.apk'
    $get = & $S3 ($common + @('s3api', 'get-object') + $object + @($download))
    $null = Assert-DevCommand $get 'R2 远端回拉'
    if ((Get-Item -LiteralPath $download).Length -ne $Info.apk_size -or (Get-DevSha256 $download) -cne $Info.apk_sha256) { throw 'R2 已存在对象与本次 APK 不同，拒绝覆盖或发布。' }
    & $VerifyPublic (Get-DevApkUrl $Info.version) $Info (Join-Path $ScratchDirectory 'public-readback.apk')
}

function Invoke-DevPublication {
    param($Candidate, [scriptblock]$ReadCurrent, [scriptblock]$UploadAndVerify, [scriptblock]$Commit)
    Assert-DevMonotonic (& $ReadCurrent) $Candidate
    & $UploadAndVerify
    # 下载期间另一发布者可能已经推进清单，提交前必须再检查。
    Assert-DevMonotonic (& $ReadCurrent) $Candidate
    & $Commit
}

function Edit-DevSingleRegion {
    param([string]$Text, [string]$Pattern, [string]$Replacement)
    $regex = [regex]::new($Pattern)
    if ($regex.Matches($Text).Count -ne 1) { throw '发布文档定位不唯一；停止自动改写。' }
    $regex.Replace($Text, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $Replacement })
}

function Write-DevReleaseDocuments {
    param([string]$RepoRoot, $Info, $Metadata)
    $date = ([DateTimeOffset]::Parse($Metadata.published_at)).ToString('yyyy-MM-dd')
    $files = [ordered]@{}
    $changelog = Get-Content -LiteralPath (Join-Path $RepoRoot 'CHANGELOG.md') -Raw
    $files['CHANGELOG.md'] = Edit-DevSingleRegion $changelog "(?m)^## \[$([regex]::Escape($Info.version_name))\] - (?:待发布|[0-9]{4}-[0-9]{2}-[0-9]{2})\r?$" "## [$($Info.version_name)] - $date"
    $strategy = Get-Content -LiteralPath (Join-Path $RepoRoot 'docs/版本策略.md') -Raw
    $strategy = Edit-DevSingleRegion $strategy '(?m)^- 当前开发分发基线版本：.*\r?$' ('- 当前开发分发基线版本：`' + $Info.version + '`')
    $files['docs/版本策略.md'] = Edit-DevSingleRegion $strategy '(?m)^- 当前开发分发 `versionCode`：.*\r?$' ('- 当前开发分发 `versionCode`：`' + $Info.version_code + '`')
    $policy = Get-Content -LiteralPath (Join-Path $RepoRoot 'docs/发布与更新约定.md') -Raw
    $policy = Edit-DevSingleRegion $policy '(?m)^- 当前开发分发包示例：.*\r?$' ('- 当前开发分发包示例：`' + $Info.apk_name + '`')
    $files['docs/发布与更新约定.md'] = Edit-DevSingleRegion $policy '(?m)^当前 dev 版本为.*\r?$' ('当前 dev 版本为 `' + $Info.version_name + ' / ' + $Info.version_code + '`，由 GitHub Actions 构建，Cloudflare R2 分发 APK，GitHub Pages 发布更新清单；构建、签名、校验及下载信息见 `发布候选记录.md`。')
    $versions = Get-Content -LiteralPath (Join-Path $RepoRoot '版本功能添加/优化.md') -Raw
    $files['版本功能添加/优化.md'] = Edit-DevSingleRegion $versions '(?s)(?<=<!-- NARRA_DEV_RELEASE_STATUS:START -->).*?(?=<!-- NARRA_DEV_RELEASE_STATUS:END -->)' ("`n`n- $date：$($Info.version_name) / $($Info.version_code) 已完成 Actions 构建、原签名校验、R2 回拉与公开下载验证；更新清单已提交，公网生效由对应 [Actions run]($($Info.run_url)) 的 Pages / 最终检查结果确认。`n`n")
    $files['docs/发布候选记录.md'] = @"
# 发布候选记录

## 当前候选包

- 构建时间：$($Info.built_at)
- 版本：$($Info.version_name) ($($Info.version_code))
- 包名：$($Info.application_id)
- 文件名：$($Info.apk_name)
- 大小：$($Info.apk_size) 字节
- SHA256：$($Info.apk_sha256)
- 签名证书 SHA-256：$($Info.signing_certificate_sha256)
- 源码提交：$($Info.source_sha)
- Actions 构建：$($Info.run_url)（构建 attempt $($Info.run_attempt)）

## 发布信息

- R2 对象：narra-updates/dev/$($Info.apk_name)
- APK 下载链接：$($Metadata.apk_url)
- 清单生成时间：$($Metadata.published_at)
- 更新清单：$script:MetadataUrl
- 更新类型：可选更新，minimum_supported_version_code 为 0。

## 验证

- 云端 JVM、Lint、debug 构建与截图 verify 完成后才生成发布 artifact。
- 实际 APK 包名、版本及原 dev 签名均已校验。
- R2 远端回拉与公开下载的 SHA-256 均与构建 APK 一致，公开 HEAD 为 200，大小及 MIME 匹配。
- Pages 部署和最终公网清单验证在本提交后执行，其实际结果以 Actions run 为准；本文件不提前宣称公网清单已生效。
- 真机覆盖安装与数据保留尚未由该自动流程实测，不把云端检查等同设备验收。
"@
    $files['docs/updates/dev.json'] = $Metadata | ConvertTo-Json -Depth 8
    $report = [ordered]@{}
    foreach ($property in $Info.PSObject.Properties) { $report[$property.Name] = $property.Value }
    $report.published_at = $Metadata.published_at
    $report.apk_url = $Metadata.apk_url
    $report.r2_object = "narra-updates/dev/$($Info.apk_name)"
    $files['docs/updates/dev-release-info.json'] = $report | ConvertTo-Json -Depth 8
    # 所有模板定位成功后才落盘，不留下半套本地文档。
    foreach ($relative in $files.Keys) { [IO.File]::WriteAllText((Join-Path $RepoRoot $relative), $files[$relative].TrimEnd() + "`n", [Text.UTF8Encoding]::new($false)) }
    @($files.Keys)
}

function Set-DevOutput {
    param([string]$Name, [string]$Value)
    if ($Name -cnotmatch '^[a-z][a-z0-9_]*$' -or $Value.Contains("`n") -or $Value.Contains("`r")) { throw '无效的 job output。' }
    if ($env:GITHUB_OUTPUT) { [IO.File]::AppendAllText($env:GITHUB_OUTPUT, "$Name=$Value`n", [Text.UTF8Encoding]::new($false)) }
}

Export-ModuleMember -Function *-Dev*
