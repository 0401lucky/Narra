#requires -Version 7.2
[CmdletBinding()]
param([switch]$SkipEntryTests)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Import-Module (Join-Path $repository 'tools/dev-release.psm1') -Force -DisableNameChecking -Scope Global
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('narra-dev-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$passed = 0
$failed = 0

function Assert-True {
    param([bool]$Condition, [string]$Message = '断言失败')
    if (-not $Condition) { throw $Message }
}
function Assert-Throws {
    param([scriptblock]$Action, [string]$Pattern = '.')
    $failure = $null
    try { & $Action } catch { $failure = $_.Exception.Message }
    Assert-True ($null -ne $failure) '预期失败，但操作成功'
    Assert-True ($failure -match $Pattern) "失败原因不符合预期：$failure"
}
function Test-Case {
    param([string]$Name, [scriptblock]$Action)
    try { & $Action; $script:passed++; Write-Host "PASS $Name" }
    catch { $script:failed++; Write-Host "FAIL $Name : $($_.Exception.Message)" }
}
function Copy-Value { param($Value) $Value | ConvertTo-Json -Depth 10 | ConvertFrom-Json }

$apk = Join-Path $testRoot 'fixture.apk'
$apkBytes = [Text.Encoding]::UTF8.GetBytes('合成 APK 字节，仅用于离线发布顺序测试')
[IO.File]::WriteAllBytes($apk, $apkBytes)
$info = [pscustomobject]@{
    schema_version = 1; repository = '0401lucky/Narra'; source_sha = ('a' * 40)
    run_id = '123'; run_attempt = 1; run_url = 'https://github.com/0401lucky/Narra/actions/runs/123'
    version = '2.3.0'; version_name = '2.3.0-dev'; version_code = 20300
    application_id = 'com.narra.app.dev'; channel = 'dev'; apk_name = 'Narra-v2.3.0-dev-20300-dev.apk'
    apk_size = $apkBytes.Length; apk_sha256 = Get-DevSha256 $apk
    signing_certificate_sha256 = '3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8'
    built_at = '2026-11-06T00:00:00+00:00'; android_build_tools = '36.1.0'; release_notes = @('测试文字 `$() 不作为命令执行', '可选更新')
}
$candidate = New-DevMetadata $info $apk (Join-Path $testRoot 'candidate.json') '2026-11-06T00:00:00+00:00'
$old = Copy-Value $candidate
$old.latest_version_name = '2.2.12-dev'
$old.latest_version_code = 20212
$old.apk_url = Get-DevApkUrl '2.2.12'

function New-FakeR2 {
    # 动态模块不继承调用脚本的私有函数或上层变量，先把依赖绑定到当前局部作用域。
    $assertTrue = ${function:Assert-True}
    $fixtureBytes = [IO.File]::ReadAllBytes($apk)
    $expectedUrl = Get-DevApkUrl $info.version
    $state = @{
        Calls = [Collections.Generic.List[string]]::new(); Exists = $false; HeadError = ''; PutRace = $false
        WrongBytes = $false; FailGet = $false; FailPublic = $false; Puts = 0
    }
    $s3 = {
        param([string[]]$arguments)
        $operation = $arguments[[array]::IndexOf($arguments, 's3api') + 1]
        $state.Calls.Add($operation)
        & $assertTrue ($arguments[[array]::IndexOf($arguments, '--bucket') + 1] -ceq 'narra-updates')
        & $assertTrue ($arguments[[array]::IndexOf($arguments, '--key') + 1] -ceq 'dev/Narra-v2.3.0-dev-20300-dev.apk')
        switch ($operation) {
            'head-object' {
                if ($state.HeadError) { return [pscustomobject]@{ ExitCode = 255; Output = ''; Error = "An error occurred ($($state.HeadError)) when calling the HeadObject operation" } }
                if (-not $state.Exists) { return [pscustomobject]@{ ExitCode = 255; Output = ''; Error = 'An error occurred (404) when calling the HeadObject operation' } }
            }
            'put-object' {
                & $assertTrue ($arguments[[array]::IndexOf($arguments, '--if-none-match') + 1] -ceq '*') '缺少服务端原子防覆盖条件'
                $state.Puts++
                $state.Exists = $true
                if ($state.PutRace) { return [pscustomobject]@{ ExitCode = 255; Output = ''; Error = 'An error occurred (PreconditionFailed) when calling the PutObject operation' } }
            }
            'get-object' {
                if ($state.FailGet) { return [pscustomobject]@{ ExitCode = 255; Output = ''; Error = 'An error occurred (AccessDenied) when calling the GetObject operation' } }
                $bytes = if ($state.WrongBytes) { [Text.Encoding]::UTF8.GetBytes('不同的 APK') } else { $fixtureBytes }
                [IO.File]::WriteAllBytes($arguments[-1], $bytes)
            }
            default { throw "不应执行：$operation" }
        }
        [pscustomobject]@{ ExitCode = 0; Output = '{}'; Error = '' }
    }.GetNewClosure()
    $public = {
        param($url, $artifact, $path)
        $state.Calls.Add('public-verify')
        & $assertTrue ($url -ceq $expectedUrl)
        if ($state.FailPublic) { throw '公开 APK 验证失败' }
    }.GetNewClosure()
    [pscustomobject]@{ State = $state; S3 = $s3; Verify = $public }
}
function Invoke-FakeUpload {
    param($Fake, $Artifact = $info)
    Publish-DevApk $Artifact $apk 'https://aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.r2.cloudflarestorage.com' $testRoot -S3 $Fake.S3 -VerifyPublic $Fake.Verify
}

try {
    Test-Case 'canonical version and Gradle settings' {
        $propertiesPath = Join-Path $testRoot 'gradle.properties'
        @('appVersionMajorOverride=2', 'appVersionMinorOverride=3', 'appVersionPatchOverride=0', 'narraApplicationId=com.narra.app', 'appUpdateMetadataBaseUrl=https://0401lucky.github.io/Narra/updates') | Set-Content -LiteralPath $propertiesPath
        $version = Get-DevVersion '2.3.0' $propertiesPath
        Assert-True ($version.Code -eq 20300 -and $version.ApkName -ceq $info.apk_name)
        foreach ($bad in @('2.3.0-dev', '2.03.0', '2.100.0', '2.3.100', '2.3.0;echo nope', '0.0.0')) { Assert-Throws { Get-DevVersion $bad } }
        Assert-Throws { Get-DevVersion '2.3.1' $propertiesPath } '不一致'
    }
    Test-Case 'existing JSON generator produces exact optional metadata' {
        Assert-DevMetadata $candidate
        Assert-True ($candidate.apk_sha256 -ceq $info.apk_sha256 -and $candidate.minimum_supported_version_code -eq 0)
        Assert-True ($candidate.release_notes[0] -ceq $info.release_notes[0])
    }
    Test-Case 'metadata rejects other host, app, forced update and invalid hash' {
        foreach ($field in @('app_id', 'apk_url', 'minimum_supported_version_code', 'apk_sha256')) {
            $bad = Copy-Value $candidate
            $bad.$field = switch ($field) { 'app_id' { 'com.narra.app' } 'apk_url' { 'https://wrong.example/dev/file.apk' } 'minimum_supported_version_code' { 20300 } default { 'bad' } }
            Assert-Throws { Assert-DevMetadata $bad }
        }
    }
    Test-Case 'monotonic version and immutable same version' {
        Assert-DevMonotonic $old $candidate
        Assert-DevMonotonic $candidate $candidate
        Assert-Throws { Assert-DevMonotonic $candidate $old } '降级'
        $changed = Copy-Value $candidate
        $changed.apk_sha256 = 'b' * 64
        Assert-Throws { Assert-DevMonotonic $candidate $changed } '相同版本'
    }
    Test-Case 'APK identity requires dev app, exact version and one original signer' {
        $badging = "package: name='com.narra.app.dev' versionCode='20300' versionName='2.3.0-dev'"
        $signature = "Signer #1 certificate SHA-256 digest: $($info.signing_certificate_sha256)"
        Assert-DevApkIdentity $badging $signature '2.3.0'
        Assert-Throws { Assert-DevApkIdentity ($badging.Replace('.app.dev', '.app')) $signature '2.3.0' } '包名'
        Assert-Throws { Assert-DevApkIdentity $badging ($signature + "`n" + $signature.Replace('#1', '#2')) '2.3.0' } '签名'
        Assert-Throws { Assert-DevApkIdentity $badging ($signature.Replace($info.signing_certificate_sha256, ('b' * 64))) '2.3.0' } '签名'
    }
    Test-Case 'artifact corruption is rejected before any S3 call' {
        $fake = New-FakeR2
        $bad = Copy-Value $info
        $bad.signing_certificate_sha256 = 'b' * 64
        Assert-Throws { Invoke-FakeUpload $fake $bad } '证书'
        $bad = Copy-Value $info
        $bad.apk_sha256 = 'b' * 64
        Assert-Throws { Invoke-FakeUpload $fake $bad } 'SHA-256'
        Assert-True ($fake.State.Calls.Count -eq 0)
    }
    Test-Case 'first upload verifies remote and public bytes in order' {
        $fake = New-FakeR2
        Invoke-FakeUpload $fake
        Assert-True (($fake.State.Calls -join ',') -ceq 'head-object,put-object,get-object,public-verify')
    }
    Test-Case '403 cannot be treated as missing object' {
        $fake = New-FakeR2
        $fake.State.HeadError = '403'
        Assert-Throws { Invoke-FakeUpload $fake } '预检失败'
        Assert-True (($fake.State.Calls -join ',') -ceq 'head-object')
    }
    Test-Case 'existing identical APK is not overwritten' {
        $fake = New-FakeR2
        $fake.State.Exists = $true
        Invoke-FakeUpload $fake
        Assert-True ($fake.State.Puts -eq 0 -and $fake.State.Calls.Contains('public-verify'))
    }
    Test-Case 'existing different APK is rejected without public or metadata writes' {
        $fake = New-FakeR2
        $fake.State.Exists = $true
        $fake.State.WrongBytes = $true
        Assert-Throws { Invoke-FakeUpload $fake } '拒绝覆盖'
        Assert-True ($fake.State.Puts -eq 0 -and -not $fake.State.Calls.Contains('public-verify'))
    }
    Test-Case 'conditional write race validates winner instead of overwriting' {
        $fake = New-FakeR2
        $fake.State.PutRace = $true
        Invoke-FakeUpload $fake
        $fake = New-FakeR2
        $fake.State.PutRace = $true
        $fake.State.WrongBytes = $true
        Assert-Throws { Invoke-FakeUpload $fake } '拒绝覆盖'
    }
    Test-Case 'remote GET or public failure never commits metadata' {
        foreach ($failure in @('FailGet', 'FailPublic')) {
            $fake = New-FakeR2
            $fake.State[$failure] = $true
            $written = [Collections.Generic.List[string]]::new()
            $failurePattern = if ($failure -eq 'FailGet') { '远端回拉' } else { '公开 APK' }
            Assert-Throws { Invoke-DevPublication $candidate { $old } { Invoke-FakeUpload $fake } { $written.Add('commit') } } $failurePattern
            Assert-True ($written.Count -eq 0)
            Assert-True ($fake.State.Calls.Contains($(if ($failure -eq 'FailGet') { 'get-object' } else { 'public-verify' })))
        }
    }
    Test-Case 'publication rechecks version after upload' {
        $state = @{ Reads = 0; Commits = 0; Uploads = 0 }
        $previous = $old
        $newer = Copy-Value $candidate
        $newer.latest_version_name = '2.3.1-dev'; $newer.latest_version_code = 20301; $newer.apk_url = Get-DevApkUrl '2.3.1'
        $read = { $state.Reads++; if ($state.Reads -eq 1) { $previous } else { $newer } }.GetNewClosure()
        Assert-Throws { Invoke-DevPublication $candidate $read { $state.Uploads++ } { $state.Commits++ } } '降级'
        Assert-True ($state.Reads -eq 2 -and $state.Uploads -eq 1 -and $state.Commits -eq 0)
    }
    Test-Case 'failed commit retries verified original object without a second PUT' {
        $fake = New-FakeR2
        $state = @{ Published = $old; Commits = 0 }
        Assert-Throws { Invoke-DevPublication $candidate { $state.Published } { Invoke-FakeUpload $fake } { throw 'main push rejected' } } 'push rejected'
        Assert-True ($state.Published.latest_version_code -eq 20212 -and $fake.State.Puts -eq 1)
        Invoke-DevPublication $candidate { $state.Published } { Invoke-FakeUpload $fake } { $state.Published = $candidate; $state.Commits++ }
        Assert-True ($state.Commits -eq 1 -and $fake.State.Puts -eq 1 -and $state.Published.latest_version_code -eq 20300)
    }
    Test-Case 'R2 endpoint accepts only account and supported jurisdiction' {
        Assert-True ((Get-DevR2Endpoint ('a' * 32)) -ceq ('https://' + ('a' * 32) + '.r2.cloudflarestorage.com'))
        Assert-True ((Get-DevR2Endpoint ('a' * 32) 'eu').Contains('.eu.r2.'))
        Assert-Throws { Get-DevR2Endpoint 'account; invalid' }
    }
    Test-Case 'native errors do not disclose credential diagnostics' {
        $failure = $null
        try { Assert-DevCommand ([pscustomobject]@{ ExitCode = 255; Output = ''; Error = 'secret=DO_NOT_PRINT' }) '上传' }
        catch { $failure = $_.Exception.Message }
        Assert-True ($failure -match '上传.*255' -and -not $failure.Contains('DO_NOT_PRINT'))
    }
    Test-Case 'job outputs preserve SHA field and reject line injection' {
        $savedOutput = $env:GITHUB_OUTPUT
        try {
            $env:GITHUB_OUTPUT = Join-Path $testRoot 'job-output.txt'
            Set-DevOutput apk_sha256 $info.apk_sha256
            Assert-True ((Get-Content -LiteralPath $env:GITHUB_OUTPUT -Raw).Trim() -ceq "apk_sha256=$($info.apk_sha256)")
            Assert-Throws { Set-DevOutput artifact_name "valid`nother=invalid" } '无效'
            Assert-Throws { Set-DevOutput 'invalid=name' 'value' } '无效'
        } finally { $env:GITHUB_OUTPUT = $savedOutput }
    }
    Test-Case 'failed-job retry binds original artifact to same run and source' {
        $names = @('GITHUB_ACTIONS', 'GITHUB_EVENT_NAME', 'GITHUB_REPOSITORY', 'GITHUB_REF', 'GITHUB_SHA', 'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT')
        $saved = @{}
        foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name) }
        try {
            $env:GITHUB_ACTIONS = 'true'; $env:GITHUB_EVENT_NAME = 'workflow_dispatch'; $env:GITHUB_REPOSITORY = '0401lucky/Narra'
            $env:GITHUB_REF = 'refs/heads/main'; $env:GITHUB_SHA = $info.source_sha; $env:GITHUB_RUN_ID = '123'; $env:GITHUB_RUN_ATTEMPT = '2'
            Assert-DevContext
            Assert-DevArtifact $info $apk '2.3.0' -CheckRun
            $env:GITHUB_RUN_ID = '456'
            Assert-Throws { Assert-DevArtifact $info $apk '2.3.0' -CheckRun } '不属于'
            $env:GITHUB_RUN_ID = '123'; $env:GITHUB_SHA = 'b' * 40
            Assert-Throws { Assert-DevArtifact $info $apk '2.3.0' -CheckRun } '不属于'
            $env:GITHUB_REF = 'refs/heads/feature'
            Assert-Throws { Assert-DevContext } '仅允许'
        } finally { foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $saved[$name]) } }
    }
    Test-Case 'documents update only explicit release regions in isolated repository' {
        $copyRoot = Join-Path $testRoot 'repo'
        foreach ($relative in @('CHANGELOG.md', '版本功能添加/优化.md', 'docs/版本策略.md', 'docs/发布与更新约定.md')) {
            $destination = Join-Path $copyRoot $relative
            New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
            Copy-Item -LiteralPath (Join-Path $repository $relative) -Destination $destination
        }
        New-Item -ItemType Directory -Path (Join-Path $copyRoot 'docs/updates') | Out-Null
        $before = Get-Content -LiteralPath (Join-Path $copyRoot '版本功能添加/优化.md') -Raw
        $paths = @(Write-DevReleaseDocuments $copyRoot $info $candidate)
        $after = Get-Content -LiteralPath (Join-Path $copyRoot '版本功能添加/优化.md') -Raw
        $pattern = '(?s)<!-- NARRA_DEV_RELEASE_STATUS:START -->.*?<!-- NARRA_DEV_RELEASE_STATUS:END -->'
        Assert-True ([regex]::Replace($before, $pattern, '').TrimEnd() -ceq [regex]::Replace($after, $pattern, '').TrimEnd()) '改写了发布区域以外的历史内容'
        Assert-True ($paths.Count -eq 7)
        Assert-True ((Read-DevJson (Join-Path $copyRoot 'docs/updates/dev.json')).apk_sha256 -ceq $info.apk_sha256)
        Assert-True ((Read-DevJson (Join-Path $copyRoot 'docs/updates/dev-release-info.json')).source_sha -ceq $info.source_sha)
        $candidatePath = Join-Path $copyRoot 'docs/updates/dev.json'
        $beforeHash = Get-DevSha256 $candidatePath
        (Get-Content -LiteralPath (Join-Path $copyRoot 'CHANGELOG.md') -Raw).Replace('## [2.3.0-dev]', '## [missing]') | Set-Content -LiteralPath (Join-Path $copyRoot 'CHANGELOG.md')
        Assert-Throws { Write-DevReleaseDocuments $copyRoot $info $candidate } '定位不唯一'
        Assert-True ((Get-DevSha256 $candidatePath) -ceq $beforeHash)
    }
} finally {
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $allowedRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('/', '\') + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($resolved) -notmatch '^narra-dev-tests-[a-f0-9]{32}$') { throw '拒绝清理预期临时目录以外的路径。' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
Write-Host "Dev release tests: $passed passed, $failed failed."
if ($failed) { exit 1 }
if (-not $SkipEntryTests) {
    $entryTestRunner = Join-Path $PSHOME $(if ($IsWindows) { 'pwsh.exe' } else { 'pwsh' })
    & $entryTestRunner -NoProfile -NonInteractive -File (Join-Path $PSScriptRoot 'dev-release-entry.tests.ps1')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
