param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("dev", "release", "baseline")]
    [string]$Channel,

    [Parameter(Mandatory = $true)]
    [string]$VersionName,

    [Parameter(Mandatory = $true)]
    [int]$VersionCode,

    [Parameter(Mandatory = $true)]
    [string]$ApkUrl,

    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [string]$PublishedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK"),

    [string[]]$ReleaseNotes = @("更新应用内测试包")
)

$root = Split-Path -Parent $PSScriptRoot
$jsonPath = Join-Path $root "docs\updates\$Channel.json"

if (-not (Test-Path $jsonPath)) {
    throw "找不到更新元数据文件：$jsonPath"
}

if (-not (Test-Path $ApkPath)) {
    throw "找不到 APK 文件：$ApkPath"
}

$appId = switch ($Channel) {
    "dev" { "com.narra.app.dev" }
    "release" { "com.narra.app" }
    "baseline" { "com.narra.app.baseline" }
}

$sha256 = (Get-FileHash -Path $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()

$payload = [ordered]@{
    app_id = $appId
    channel = $Channel
    latest_version_name = $VersionName
    latest_version_code = $VersionCode
    minimum_supported_version_code = $VersionCode
    apk_url = $ApkUrl
    apk_sha256 = $sha256
    published_at = $PublishedAt
    release_notes = @($ReleaseNotes)
}

$payload | ConvertTo-Json -Depth 5 | Set-Content -Path $jsonPath -Encoding UTF8

Write-Host "已更新 $jsonPath"
Write-Host "SHA256: $sha256"
