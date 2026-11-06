#requires -Version 7.2
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$runtime = Join-Path $PSHOME $(if ($IsWindows) { 'pwsh.exe' } else { 'pwsh' })
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('narra-dev-startup-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$failed = 0
$suites = @(
    @{ Name = 'module'; File = 'dev-release.tests.ps1'; Arguments = @('-SkipEntryTests') },
    @{ Name = 'entry'; File = 'dev-release-entry.tests.ps1'; Arguments = @() }
)

try {
    foreach ($mode in @('File', 'GitHubWrapper')) {
        foreach ($suite in $suites) {
            Write-Host "PowerShell startup: $mode / $($suite.Name)"
            $suitePath = Join-Path $PSScriptRoot $suite.File
            if ($mode -eq 'File') {
                & $runtime -NoLogo -NoProfile -NonInteractive -File $suitePath @($suite.Arguments)
            } else {
                # GitHub pwsh 使用 -command ". '{0}'"；wrapper 再调用脚本，会多一层脚本作用域。
                $wrapperPath = Join-Path $testRoot ($suite.Name + '-actions-step.ps1')
                $invocation = "& '" + $suitePath.Replace("'", "''") + "' " + ($suite.Arguments -join ' ')
                $body = @('$ErrorActionPreference = ''stop''', $invocation, 'if (Test-Path -LiteralPath variable:\LASTEXITCODE) { exit $LASTEXITCODE }') -join "`n"
                [IO.File]::WriteAllText($wrapperPath, $body, [Text.UTF8Encoding]::new($false))
                $command = ". '" + $wrapperPath.Replace("'", "''") + "'"
                & $runtime -NoLogo -NoProfile -NonInteractive -Command $command
            }
            if ($LASTEXITCODE -ne 0) { $failed++ }
        }
    }
} finally {
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $allowedRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('/', '\') + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($resolved) -notmatch '^narra-dev-startup-tests-[a-f0-9]{32}$') { throw '拒绝清理启动测试目录以外的路径。' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
if ($failed) { throw "$failed 个 PowerShell 启动检查未通过。" }
Write-Host 'Dev release startup tests: File and GitHub wrapper suites passed.'
