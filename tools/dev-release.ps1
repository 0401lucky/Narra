#requires -Version 7.2
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Preflight', 'VerifyKeystore', 'FreezeArtifact', 'Publish', 'PreparePages', 'VerifyPublished')][string]$Mode,
    [string]$Version,
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$ArtifactDirectory,
    [string]$KeystorePath,
    [string]$ExpectedSha256,
    [string]$ExpectedCommit
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# GetNewClosure 使用独立动态模块；显式导出到全局，保证 Actions wrapper 下也能解析发布函数。
Import-Module (Join-Path $PSScriptRoot 'dev-release.psm1') -Force -DisableNameChecking -Scope Global

switch ($Mode) {
    'Preflight' {
        Assert-DevContext
        $target = Get-DevVersion $Version (Join-Path $RepoRoot 'gradle.properties')
        $notes = Read-DevJson (Join-Path $RepoRoot 'docs/updates/dev-release-notes.json')
        if ($notes.version -cne $Version -or @($notes.release_notes).Count -eq 0) { throw '缺少当前版本的发行说明。' }
        $current = Read-DevJson (Join-Path $RepoRoot 'docs/updates/dev.json')
        Assert-DevMetadata $current
        if ($current.latest_version_code -gt $target.Code) { throw '源码版本低于已提交发布版本。' }
        Write-Host "准备从 $env:GITHUB_SHA 构建 $($target.Name) / $($target.Code)。"
    }
    'VerifyKeystore' {
        Assert-DevContext
        Test-DevKeystore $KeystorePath
        Write-Host '已确认原 dev 签名证书。'
    }
    'FreezeArtifact' {
        Assert-DevContext
        $info = New-DevArtifact $RepoRoot $Version $ArtifactDirectory
        Set-DevOutput artifact_name "dev-$Version-$env:GITHUB_RUN_ID-$env:GITHUB_RUN_ATTEMPT"
        Set-DevOutput apk_name $info.apk_name
        Set-DevOutput apk_sha256 $info.apk_sha256
        Set-DevOutput version_code ([string]$info.version_code)
    }
    'Publish' {
        Assert-DevContext
        if ([IO.Path]::GetFullPath($RepoRoot).TrimEnd('/', '\') -ne [IO.Path]::GetFullPath($env:GITHUB_WORKSPACE).TrimEnd('/', '\')) {
            throw '发布只能在 GitHub runner 的独立工作区执行。'
        }
        if (-not $env:AWS_ACCESS_KEY_ID -or -not $env:AWS_SECRET_ACCESS_KEY) { throw '缺少 R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY；原构建 artifact 可用于重跑失败 job。' }
        $jurisdiction = if ($env:R2_JURISDICTION) { $env:R2_JURISDICTION } else { 'default' }
        $endpoint = Get-DevR2Endpoint $env:CLOUDFLARE_ACCOUNT_ID $jurisdiction
        $awsVersion = Assert-DevCommand (Invoke-DevCommand aws @('--version')) '读取 AWS CLI 版本'
        if ($awsVersion -notmatch '^aws-cli/2\.31\.19\s') { throw '发布要求已固定的 AWS CLI 2.31.19。' }
        $target = Get-DevVersion $Version
        $info = Read-DevJson (Join-Path $ArtifactDirectory 'release-info.json')
        $apk = Join-Path $ArtifactDirectory $target.ApkName
        Assert-DevArtifact $info $apk $Version -CheckRun
        $git = { param($arguments) Invoke-DevCommand git (@('-C', $RepoRoot) + $arguments) }.GetNewClosure()
        $status = Assert-DevCommand (& $git @('status', '--porcelain')) '检查发布工作区'
        if ($status) { throw '发布工作区不干净，拒绝覆盖本地变动。' }
        $null = Assert-DevCommand (& $git @('fetch', 'origin', 'main')) '读取最新 main'
        $null = Assert-DevCommand (& $git @('merge-base', '--is-ancestor', $info.source_sha, 'origin/main')) '检查源码仍属于 main'
        $null = Assert-DevCommand (& $git @('switch', '--detach', 'origin/main')) '切换最新发布文档'
        $baseCommit = Assert-DevCommand (& $git @('rev-parse', 'HEAD')) '读取 main 提交'
        $null = Get-DevVersion $Version (Join-Path $RepoRoot 'gradle.properties')
        $scratch = Join-Path $env:RUNNER_TEMP 'narra-dev-publish'
        New-Item -ItemType Directory -Path $scratch -Force | Out-Null
        $current = Read-DevJson (Join-Path $RepoRoot 'docs/updates/dev.json')
        $publishedAt = if ($current.latest_version_code -eq $target.Code) { $current.published_at } else { [DateTimeOffset]::UtcNow.ToString('o') }
        $candidate = New-DevMetadata $info $apk (Join-Path $scratch 'dev.json') $publishedAt
        $readCurrent = {
            $null = Assert-DevCommand (& $git @('fetch', 'origin', 'main')) '提交前复查 main'
            $latest = Assert-DevCommand (& $git @('rev-parse', 'origin/main')) '复查 main 提交'
            if ($latest -cne $baseCommit) { throw '发布期间 main 已改变；请重跑失败 job，以原 artifact 重新核对。' }
            Assert-DevMonotonic (Read-DevJson (Join-Path $RepoRoot 'docs/updates/dev.json')) $candidate
            Get-DevPublicMetadata
        }.GetNewClosure()
        $upload = { Publish-DevApk $info $apk $endpoint $scratch }.GetNewClosure()
        $commit = {
            $paths = @(Write-DevReleaseDocuments $RepoRoot $info $candidate)
            $null = Assert-DevCommand (& $git (@('add', '--') + $paths)) '暂存发布文档'
            $staged = Assert-DevCommand (& $git @('-c', 'core.quotepath=false', 'diff', '--cached', '--name-only')) '检查暂存范围'
            foreach ($path in ($staged -split "`n" | Where-Object { $_ })) {
                if ($path.Trim() -notin $paths) { throw '暂存区包含发布清单以外的文件。' }
            }
            if ($staged) {
                $null = Assert-DevCommand (& $git @('-c', 'user.name=github-actions[bot]', '-c', 'user.email=41898282+github-actions[bot]@users.noreply.github.com', 'commit', '-m', "chore(release): publish $($info.version_name)")) '提交发布文档'
                $push = & $git @('push', 'origin', 'HEAD:main')
                if ($push.ExitCode -ne 0) { throw '清单提交未推送成功；可能存在 main 竞争或权限问题。请重跑失败 job，禁止强制推送。' }
            }
            $commitSha = Assert-DevCommand (& $git @('rev-parse', 'HEAD')) '读取清单提交'
            Set-DevOutput publication_commit $commitSha
            Set-DevOutput apk_url $candidate.apk_url
            Write-Host "R2 和公开 APK 验证通过，清单提交：$commitSha。下一步部署 Pages。"
        }.GetNewClosure()
        Invoke-DevPublication $candidate $readCurrent $upload $commit
    }
    'PreparePages' {
        if ($env:GITHUB_REPOSITORY -cne '0401lucky/Narra' -or $env:GITHUB_REF -cne 'refs/heads/main') { throw 'Pages 只发布本仓库 main。' }
        if ($ExpectedCommit) {
            if ($ExpectedCommit -cnotmatch '^[a-f0-9]{40}$') { throw '发布提交标识无效。' }
            $null = Assert-DevCommand (Invoke-DevCommand git @('-C', $RepoRoot, 'merge-base', '--is-ancestor', $ExpectedCommit, 'HEAD')) '检查 Pages 包含发布提交'
        }
        $candidate = Read-DevJson (Join-Path $RepoRoot 'docs/updates/dev.json')
        Assert-DevMetadata $candidate
        if ($Version) {
            $target = Get-DevVersion $Version
            if ($candidate.latest_version_code -ne $target.Code -or $candidate.apk_sha256 -cne $ExpectedSha256) { throw '最新 main 清单与本次发布目标不同。' }
        }
        Assert-DevMonotonic (Get-DevPublicMetadata) $candidate
        Write-Host "Pages 版本预检通过：$($candidate.latest_version_name)。"
    }
    'VerifyPublished' {
        $target = Get-DevVersion $Version
        if ($ExpectedSha256 -cnotmatch '^[a-f0-9]{64}$') { throw '缺少构建 APK SHA-256。' }
        $matched = $false
        for ($attempt = 1; $attempt -le 24; $attempt++) {
            try {
                $live = Get-DevPublicMetadata -Attempts 1
                $matched = $live.latest_version_code -eq $target.Code -and $live.latest_version_name -ceq $target.Name -and
                    $live.apk_sha256 -ceq $ExpectedSha256 -and $live.apk_url -ceq (Get-DevApkUrl $Version)
                if ($matched) { break }
                if ($live.latest_version_code -gt $target.Code) { throw '公网已是更高版本。' }
            } catch { Write-Host "公网清单第 $attempt 次检查尚未成功。" }
            if ($attempt -lt 24) { Start-Sleep -Seconds 15 }
        }
        if (-not $matched) { throw 'Pages 公网清单未在时限内与本次 APK 一致；发版尚未完成。' }
        Write-Host "发版完成：$($target.Name) / $($target.Code)，旧更新入口已返回正确清单。"
        if ($env:GITHUB_STEP_SUMMARY) {
            @("## $($target.Name) 发布完成", '', "- APK：$(Get-DevApkUrl $Version)", "- SHA-256：$ExpectedSha256", '- 更新清单：https://0401lucky.github.io/Narra/updates/dev.json', '- R2 回拉、公网 APK 和旧入口清单均已验证；保持可选更新。') | Add-Content -LiteralPath $env:GITHUB_STEP_SUMMARY -Encoding utf8
        }
    }
}
