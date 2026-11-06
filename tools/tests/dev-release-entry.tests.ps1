#requires -Version 7.2
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$entryPath = Join-Path $repository 'tools/dev-release.ps1'
$modulePath = Join-Path $repository 'tools/dev-release.psm1'
$releaseModule = Import-Module $modulePath -Force -DisableNameChecking -PassThru
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('narra-dev-entry-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$passed = 0
$failed = 0
$documentPaths = @('CHANGELOG.md', '版本功能添加/优化.md', 'docs/版本策略.md', 'docs/发布与更新约定.md')
$allowedPaths = @($documentPaths + @('docs/发布候选记录.md', 'docs/updates/dev.json', 'docs/updates/dev-release-info.json'))
$documentTemplates = @{}
foreach ($relative in $documentPaths) { $documentTemplates[$relative] = Get-Content -LiteralPath (Join-Path $repository $relative) -Raw }
$environmentNames = @('GITHUB_ACTIONS', 'GITHUB_EVENT_NAME', 'GITHUB_REPOSITORY', 'GITHUB_REF', 'GITHUB_SHA', 'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'GITHUB_WORKSPACE', 'RUNNER_TEMP', 'GITHUB_OUTPUT', 'GITHUB_STEP_SUMMARY', 'AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'CLOUDFLARE_ACCOUNT_ID', 'R2_JURISDICTION')
$savedEnvironment = @{}
foreach ($name in $environmentNames) { $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name) }

function Assert-EntryTrue {
    param([bool]$Condition, [string]$Message = '入口断言失败')
    if (-not $Condition) { throw $Message }
}
function Assert-EntryThrows {
    param([scriptblock]$Action, [string]$Pattern)
    $failure = $null
    try { & $Action } catch { $failure = $_.Exception.Message }
    Assert-EntryTrue ($null -ne $failure -and $failure -match $Pattern) "失败原因不符合预期：$failure"
}
function Test-EntryCase {
    param([string]$Name, [scriptblock]$Action)
    try { & $Action; $script:passed++; Write-Host "PASS entry: $Name" }
    catch { $script:failed++; Write-Host "FAIL entry: $Name : $($_.Exception.Message)" }
}
function Copy-EntryValue { param($Value) $Value | ConvertTo-Json -Depth 10 | ConvertFrom-Json }

# 只在本测试进程替换外部边界；真实入口、闭包、元数据生成和文档提交编排均照常执行。
# 同时替换模块内和导出的命令，避免 GetNewClosure 的动态模块绕过测试桩。
$nativeStub = {
    param([string]$FilePath, [string[]]$Arguments = @())
    $state = $global:NarraDevEntryState
    $result = [pscustomobject]@{ ExitCode = 0; Output = ''; Error = '' }
    if ($FilePath -ceq 'git') {
        if ($Arguments.Count -lt 3 -or $Arguments[0] -cne '-C' -or $Arguments[1] -cne $state.RepoRoot) { throw 'Git 调用越过隔离目录。' }
        $operation = $Arguments[2..($Arguments.Count - 1)]
        if ($operation -contains '--force' -or $operation -contains '--force-with-lease' -or $operation -contains 'reset') { throw '禁止强制改写 Git。' }
        $state.Calls.Add('git:' + ($operation -join ' '))
        switch ($operation[0]) {
            'status' { $result.Output = $state.Dirty }
            'fetch' { $state.Fetches++ }
            'merge-base' { if (-not $state.Ancestor) { $result.ExitCode = 1 } }
            'switch' { if (($operation -join ' ') -cne 'switch --detach origin/main') { throw '未切换最新 main。' } }
            'rev-parse' {
                $result.Output = if ($operation[1] -ceq 'origin/main' -and $state.AdvanceAfterFetch -gt 0 -and $state.Fetches -ge $state.AdvanceAfterFetch) { 'd' * 40 } else { $state.Head }
            }
            'add' {
                $state.Staged = @($operation[2..($operation.Count - 1)])
                if ($state.Staged.Count -ne 7 -or @($state.Staged | Where-Object { $_ -notin $state.AllowedPaths }).Count) { throw '自动提交超出七个允许路径。' }
            }
            '-c' {
                if ($operation -contains 'diff') { $result.Output = $state.Staged -join "`n" }
                elseif ($operation -contains 'commit') { $state.Commits++; $state.Head = 'c' * 40 }
                else { throw '未预期的 Git 配置调用。' }
            }
            'push' {
                if (($operation -join ' ') -cne 'push origin HEAD:main') { throw '不是普通 main 推送。' }
                $state.Pushes++
                if ($state.FailPush) { $result.ExitCode = 1 }
                else { $state.PushedMetadata = Read-DevJson (Join-Path $state.RepoRoot 'docs/updates/dev.json') }
            }
            default { throw "未预期的 Git 操作：$($operation[0])" }
        }
        return $result
    }
    if ($FilePath -cne 'aws') { throw "离线测试禁止外部进程：$FilePath" }
    if ($Arguments.Count -eq 1 -and $Arguments[0] -ceq '--version') { $result.Output = 'aws-cli/2.31.19 Python/3'; return $result }
    $operation = $Arguments[[array]::IndexOf($Arguments, 's3api') + 1]
    $state.Calls.Add('s3:' + $operation)
    if ($Arguments[[array]::IndexOf($Arguments, '--bucket') + 1] -cne 'narra-updates' -or
        $Arguments[[array]::IndexOf($Arguments, '--key') + 1] -cne ('dev/' + $state.Info.apk_name)) { throw 'S3 调用越过目标对象。' }
    switch ($operation) {
        'head-object' {
            if ($state.HeadError) { $result.ExitCode = 255; $result.Error = "An error occurred ($($state.HeadError)) when calling the HeadObject operation" }
            elseif (-not $state.ObjectExists) { $result.ExitCode = 255; $result.Error = 'An error occurred (404) when calling the HeadObject operation' }
        }
        'put-object' {
            if ($Arguments[[array]::IndexOf($Arguments, '--if-none-match') + 1] -cne '*') { throw '缺少原子条件写入。' }
            $state.Puts++; $state.ObjectExists = $true
        }
        'get-object' { [IO.File]::WriteAllBytes($Arguments[-1], $state.ApkBytes) }
        default { throw "未预期的 S3 操作：$operation" }
    }
    $result
}
$httpStub = {
    param([uri]$Uri, [string]$Method = 'Get', [int]$TimeoutSec, [int]$MaximumRedirection, [string]$OutFile)
    $state = $global:NarraDevEntryState
    if ($MaximumRedirection -ne 0 -or $TimeoutSec -le 0) { throw 'HTTP 请求未限制跳转或超时。' }
    if ($Uri.AbsoluteUri -ceq 'https://0401lucky.github.io/Narra/updates/dev.json') {
        $state.Calls.Add('http:metadata')
        $state.MetadataReads++
        $index = [Math]::Min($state.MetadataReads - 1, $state.PublicMetadata.Count - 1)
        return [pscustomobject]@{ StatusCode = 200; Content = ($state.PublicMetadata[$index] | ConvertTo-Json -Depth 8) }
    }
    if ($Uri.AbsoluteUri -cne (Get-DevApkUrl '2.3.0')) { throw 'HTTP 请求越过固定发布地址。' }
    $state.Calls.Add('http:apk-' + $Method.ToLowerInvariant())
    if ($Method -ceq 'Head') {
        return [pscustomobject]@{ StatusCode = 200; Headers = @{ 'Content-Length' = @([string]$state.ApkBytes.Length); 'Content-Type' = @('application/vnd.android.package-archive') } }
    }
    if (-not $OutFile) { throw '公开 APK 必须下载到隔离目录。' }
    $bytes = if ($state.CorruptPublic) { [Text.Encoding]::UTF8.GetBytes('损坏的公开下载') } else { $state.ApkBytes }
    [IO.File]::WriteAllBytes($OutFile, $bytes)
}
$sleepStub = { param([int]$Seconds) $global:NarraDevEntryState.Sleeps++ }
& $releaseModule {
    param($Native, $Http, $Sleep)
    Set-Item -Path function:script:Invoke-DevCommand -Value $Native
    Set-Item -Path function:script:Invoke-WebRequest -Value $Http
    Set-Item -Path function:script:Start-Sleep -Value $Sleep
} $nativeStub $httpStub $sleepStub
Set-Item -Path function:global:Invoke-DevCommand -Value $nativeStub
Set-Item -Path function:global:Invoke-WebRequest -Value $httpStub
Set-Item -Path function:global:Start-Sleep -Value $sleepStub
$global:NarraDevEntryModulePath = $modulePath
function global:Import-Module {
    param([string]$Name, [switch]$Force, [switch]$DisableNameChecking)
    if ($Name -cne $global:NarraDevEntryModulePath) { throw '入口试图载入其他模块。' }
}

function New-EntryFixture {
    $fixture = Join-Path $testRoot ([guid]::NewGuid().ToString('N'))
    $repoRoot = Join-Path $fixture 'repo'
    foreach ($relative in $documentPaths) {
        $path = Join-Path $repoRoot $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
        [IO.File]::WriteAllText($path, $documentTemplates[$relative])
    }
    New-Item -ItemType Directory -Path (Join-Path $repoRoot 'docs/updates') -Force | Out-Null
    foreach ($relative in @('artifact', 'scratch')) { New-Item -ItemType Directory -Path (Join-Path $fixture $relative) -Force | Out-Null }
    @('appVersionMajorOverride=2', 'appVersionMinorOverride=3', 'appVersionPatchOverride=0', 'narraApplicationId=com.narra.app', 'appUpdateMetadataBaseUrl=https://0401lucky.github.io/Narra/updates') | Set-Content -LiteralPath (Join-Path $repoRoot 'gradle.properties')
    $bytes = [Text.Encoding]::UTF8.GetBytes('用于真实入口离线测试的固定 APK 字节')
    $artifactDirectory = Join-Path $fixture 'artifact'
    $apk = Join-Path $artifactDirectory 'Narra-v2.3.0-dev-20300-dev.apk'
    [IO.File]::WriteAllBytes($apk, $bytes)
    $info = [pscustomobject]@{
        schema_version = 1; repository = '0401lucky/Narra'; source_sha = ('a' * 40)
        run_id = '123'; run_attempt = 1; run_url = 'https://github.com/0401lucky/Narra/actions/runs/123'
        version = '2.3.0'; version_name = '2.3.0-dev'; version_code = 20300
        application_id = 'com.narra.app.dev'; channel = 'dev'; apk_name = [IO.Path]::GetFileName($apk)
        apk_size = $bytes.Length; apk_sha256 = Get-DevSha256 $apk
        signing_certificate_sha256 = '3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8'
        built_at = '2026-11-06T00:00:00+00:00'; apk_verification_build_tools = '36.1.0'; java_version = 'openjdk 17'
        release_notes = @('原 artifact 重试，保留 `$() 字面量', '可选更新')
    }
    $info | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $artifactDirectory 'release-info.json')
    $candidate = New-DevMetadata $info $apk (Join-Path $fixture 'candidate.json') '2026-11-06T00:00:00+00:00' 6>$null
    $old = Copy-EntryValue $candidate
    $old.latest_version_name = '2.2.12-dev'; $old.latest_version_code = 20212; $old.apk_url = Get-DevApkUrl '2.2.12'
    $old | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $repoRoot 'docs/updates/dev.json')
    $global:NarraDevEntryState = @{
        RepoRoot = $repoRoot; ArtifactDirectory = $artifactDirectory; Info = $info; ApkBytes = $bytes; Old = $old; Candidate = $candidate
        Calls = [Collections.Generic.List[string]]::new(); AllowedPaths = $allowedPaths; Staged = @(); Dirty = ''; Head = ('b' * 40)
        Ancestor = $true; Fetches = 0; AdvanceAfterFetch = 0; Commits = 0; Pushes = 0; FailPush = $false; PushedMetadata = $old
        HeadError = ''; ObjectExists = $false; Puts = 0; CorruptPublic = $false; PublicMetadata = @($old); MetadataReads = 0; Sleeps = 0
    }
    $env:GITHUB_ACTIONS = 'true'; $env:GITHUB_EVENT_NAME = 'workflow_dispatch'; $env:GITHUB_REPOSITORY = '0401lucky/Narra'
    $env:GITHUB_REF = 'refs/heads/main'; $env:GITHUB_SHA = $info.source_sha; $env:GITHUB_RUN_ID = '123'; $env:GITHUB_RUN_ATTEMPT = '1'
    $env:GITHUB_WORKSPACE = $repoRoot; $env:RUNNER_TEMP = Join-Path $fixture 'scratch'
    $env:GITHUB_OUTPUT = Join-Path $fixture 'job-output.txt'; $env:GITHUB_STEP_SUMMARY = Join-Path $fixture 'summary.md'
    $env:AWS_ACCESS_KEY_ID = 'offline-test'; $env:AWS_SECRET_ACCESS_KEY = 'offline-test'; $env:CLOUDFLARE_ACCOUNT_ID = 'a' * 32; $env:R2_JURISDICTION = 'default'
    $global:NarraDevEntryState
}
function Invoke-EntryPublish {
    $state = $global:NarraDevEntryState
    & $entryPath -Mode Publish -Version '2.3.0' -RepoRoot $state.RepoRoot -ArtifactDirectory $state.ArtifactDirectory 6>$null
}
function Assert-EntryUnpublished {
    param($State)
    Assert-EntryTrue ($State.Commits -eq 0 -and $State.Pushes -eq 0 -and $State.Staged.Count -eq 0) '验证失败后仍执行了 Git 写入。'
    Assert-EntryTrue ((Read-DevJson (Join-Path $State.RepoRoot 'docs/updates/dev.json')).latest_version_code -eq 20212) '验证前改写了仓库清单。'
}

try {
    Test-EntryCase 'Publish runs real closures and writes only verified release documents' {
        $state = New-EntryFixture
        Invoke-EntryPublish
        Assert-EntryTrue ($state.Puts -eq 1 -and $state.Commits -eq 1 -and $state.Pushes -eq 1)
        Assert-EntryTrue ($state.Fetches -eq 3 -and $state.MetadataReads -eq 2)
        $calls = $state.Calls -join "`n"
        Assert-EntryTrue ($calls -match '(?s)s3:put-object.*s3:get-object.*http:apk-head.*http:apk-get.*http:metadata.*git:add.*git:.*commit.*git:push') '副作用顺序不正确。'
        Assert-EntryTrue ($state.PushedMetadata.apk_sha256 -ceq $state.Info.apk_sha256)
        Assert-EntryTrue ((Get-Content -LiteralPath $env:GITHUB_OUTPUT -Raw) -match ('publication_commit=' + ('c' * 40)))
    }
    Test-EntryCase 'Publish missing credentials never accesses storage or changes metadata' {
        $state = New-EntryFixture
        $env:AWS_SECRET_ACCESS_KEY = ''
        Assert-EntryThrows { Invoke-EntryPublish } '缺少 R2'
        Assert-EntryTrue ($state.Calls.Count -eq 0)
        Assert-EntryUnpublished $state
    }
    Test-EntryCase 'Publish rejects foreign source, foreign run and future attempt before S3' {
        foreach ($field in @('source_sha', 'run_id', 'run_attempt')) {
            $state = New-EntryFixture
            $bad = Copy-EntryValue $state.Info
            $bad.$field = switch ($field) { 'source_sha' { 'e' * 40 } 'run_id' { '456' } default { 2 } }
            if ($field -eq 'run_id') { $bad.run_url = 'https://github.com/0401lucky/Narra/actions/runs/456' }
            $bad | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $state.ArtifactDirectory 'release-info.json')
            Assert-EntryThrows { Invoke-EntryPublish } '不属于'
            Assert-EntryTrue ($state.Calls.Count -eq 0)
            Assert-EntryUnpublished $state
        }
    }
    Test-EntryCase 'Publish refuses version changes on latest main before R2' {
        $state = New-EntryFixture
        $properties = Join-Path $state.RepoRoot 'gradle.properties'
        (Get-Content -LiteralPath $properties -Raw).Replace('appVersionPatchOverride=0', 'appVersionPatchOverride=1') | Set-Content -LiteralPath $properties
        Assert-EntryThrows { Invoke-EntryPublish } '不一致'
        Assert-EntryTrue (@($state.Calls | Where-Object { $_ -like 's3:*' }).Count -eq 0)
        Assert-EntryUnpublished $state
    }
    Test-EntryCase 'Publish 403 and corrupt public bytes preserve old repository metadata' {
        foreach ($failure in @('HeadError', 'CorruptPublic')) {
            $state = New-EntryFixture
            if ($failure -eq 'HeadError') { $state.HeadError = '403' } else { $state.CorruptPublic = $true }
            Assert-EntryThrows { Invoke-EntryPublish } '预检失败|公开 APK'
            Assert-EntryUnpublished $state
        }
    }
    Test-EntryCase 'Publish main advance after upload leaves verified object and no metadata write' {
        $state = New-EntryFixture
        $state.AdvanceAfterFetch = 3
        Assert-EntryThrows { Invoke-EntryPublish } 'main 已改变'
        Assert-EntryTrue ($state.Puts -eq 1 -and $state.ObjectExists)
        Assert-EntryUnpublished $state
    }
    Test-EntryCase 'Publish push conflict retries original artifact without another PUT' {
        $state = New-EntryFixture
        $state.FailPush = $true
        Assert-EntryThrows { Invoke-EntryPublish } '未推送成功'
        Assert-EntryTrue ($state.PushedMetadata.latest_version_code -eq 20212 -and $state.Puts -eq 1)
        $state = New-EntryFixture
        $state.ObjectExists = $true
        $env:GITHUB_RUN_ATTEMPT = '2'
        Invoke-EntryPublish
        Assert-EntryTrue ($state.Puts -eq 0 -and $state.PushedMetadata.latest_version_code -eq 20300)
        $report = Read-DevJson (Join-Path $state.RepoRoot 'docs/updates/dev-release-info.json')
        Assert-EntryTrue ($report.run_attempt -eq 1 -and $report.source_sha -ceq $env:GITHUB_SHA)
    }
    Test-EntryCase 'PreparePages accepts latest main and requires publication ancestry' {
        $state = New-EntryFixture
        $state.Candidate | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $state.RepoRoot 'docs/updates/dev.json')
        & $entryPath -Mode PreparePages -RepoRoot $state.RepoRoot -Version '2.3.0' -ExpectedSha256 $state.Info.apk_sha256 -ExpectedCommit ('c' * 40) 6>$null
        $state.Ancestor = $false
        Assert-EntryThrows { & $entryPath -Mode PreparePages -RepoRoot $state.RepoRoot -Version '2.3.0' -ExpectedSha256 $state.Info.apk_sha256 -ExpectedCommit ('e' * 40) } '包含发布提交'
    }
    Test-EntryCase 'PreparePages rejects stale and same-version conflicting manifests' {
        foreach ($sameVersion in @($false, $true)) {
            $state = New-EntryFixture
            $state.PublicMetadata = @($state.Candidate)
            if ($sameVersion) {
                $bad = Copy-EntryValue $state.Candidate; $bad.apk_sha256 = 'e' * 64
                $bad | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $state.RepoRoot 'docs/updates/dev.json')
            }
            Assert-EntryThrows { & $entryPath -Mode PreparePages -RepoRoot $state.RepoRoot } '降级|相同版本'
        }
    }
    Test-EntryCase 'VerifyPublished retries stale metadata then confirms the original public contract' {
        $state = New-EntryFixture
        $state.PublicMetadata = @($state.Old, $state.Old, $state.Candidate)
        & $entryPath -Mode VerifyPublished -Version '2.3.0' -ExpectedSha256 $state.Info.apk_sha256 6>$null
        Assert-EntryTrue ($state.MetadataReads -eq 3 -and $state.Sleeps -eq 2)
        $summary = Get-Content -LiteralPath $env:GITHUB_STEP_SUMMARY -Raw
        Assert-EntryTrue ($summary.Contains('发布完成') -and $summary.Contains($state.Info.apk_sha256))
    }
    Test-EntryCase 'VerifyPublished never completes for wrong hash or forced-update metadata' {
        foreach ($field in @('apk_sha256', 'minimum_supported_version_code')) {
            $state = New-EntryFixture
            $bad = Copy-EntryValue $state.Candidate
            $bad.$field = if ($field -eq 'apk_sha256') { 'e' * 64 } else { 20300 }
            $state.PublicMetadata = @($bad)
            Assert-EntryThrows { & $entryPath -Mode VerifyPublished -Version '2.3.0' -ExpectedSha256 $state.Info.apk_sha256 6>$null } '发版尚未完成'
            Assert-EntryTrue ($state.MetadataReads -eq 24 -and $state.Sleeps -eq 23)
            Assert-EntryTrue (-not (Test-Path -LiteralPath $env:GITHUB_STEP_SUMMARY)) '失败时错误报告发版完成。'
        }
    }
} finally {
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name]) }
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $allowedRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('/', '\') + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($resolved) -notmatch '^narra-dev-entry-tests-[a-f0-9]{32}$') { throw '拒绝清理隔离测试目录以外的路径。' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
Write-Host "Dev release entry tests: $passed passed, $failed failed."
if ($failed) { exit 1 }
