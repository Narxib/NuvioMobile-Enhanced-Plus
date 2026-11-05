[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Tag,
    [Parameter(Mandatory)][string]$Name,
    [string]$NotesFile,
    [string]$ApkPath = "androidApp/build/outputs/apk/full/debug/androidApp-full-debug.apk",
    [string]$ForkRepo = "Narxib/NuvioMobile-Enhanced-Plus",
    [string]$TargetCommitish = "feature/magnet-links",
    [switch]$Prerelease
)

$ErrorActionPreference = "Stop"
Set-Location (git rev-parse --show-toplevel)

if (-not (Test-Path $ApkPath)) { throw "APK not found: $ApkPath. Build it first." }

$notes = if ($NotesFile) {
    Get-Content $NotesFile -Raw
} else {
    "Build $Tag from $TargetCommitish."
}

$credential = ("protocol=https`nhost=github.com`n`n" | & git credential fill 2>$null)
$passwordLine = $credential | Where-Object { $_ -like "password=*" }
if (-not $passwordLine) { throw "No stored GitHub credential. Run any push first so Git Credential Manager stores one." }

$headers = @{
    Authorization = "Bearer $($passwordLine.Substring("password=".Length))"
    "User-Agent" = "opencode"
    Accept = "application/vnd.github+json"
}

$sha256 = (Get-FileHash $ApkPath -Algorithm SHA256).Hash
$assetName = "NuvioPlus-$Tag.apk"
$apkSizeMb = [math]::Round((Get-Item $ApkPath).Length / 1MB, 1)

$payload = @{
    tag_name = $Tag
    name = $Name
    target_commitish = $TargetCommitish
    body = "$notes`n`n---`nAPK ($apkSizeMb MB) · SHA-256: ``$sha256``"
    draft = $false
    prerelease = [bool]$Prerelease
} | ConvertTo-Json -Depth 4

$release = Invoke-RestMethod -Method Post `
    -Uri "https://api.github.com/repos/$ForkRepo/releases" `
    -Headers $headers -ContentType "application/json" `
    -Body ([Text.Encoding]::UTF8.GetBytes($payload))

$uploadUrl = $release.upload_url -replace '\{\?name,label\}', ''
Invoke-RestMethod -Method Post `
    -Uri "$uploadUrl?name=$assetName" `
    -Headers $headers -ContentType "application/vnd.android.package-archive" `
    -InFile $ApkPath -TimeoutSec 900 | Out-Null

Write-Host "Release published: $($release.html_url)"
