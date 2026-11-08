[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Tag,
    [Parameter(Mandatory)][string]$Name,
    [string]$NotesFile,
    [string[]]$ApkPath,
    [string]$ApkDir = "androidApp/build/outputs/apk/full/debug",
    [string]$ForkRepo = "Narxib/NuvioMobile-Enhanced-Plus",
    [string]$TargetCommitish = "main",
    [switch]$Prerelease
)

$ErrorActionPreference = "Stop"
Set-Location (git rev-parse --show-toplevel)

$abiNames = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

function Get-AbiFromName {
    param([string]$FileName)
    foreach ($abi in $abiNames) {
        if ($FileName -like "*$abi*") { return $abi }
    }
    return "universal"
}

$apkFiles = if ($ApkPath) {
    $ApkPath | ForEach-Object { Get-Item -LiteralPath $_ }
} else {
    Get-ChildItem -LiteralPath $ApkDir -Filter "*.apk" -File | Sort-Object Name
}
if (-not $apkFiles) {
    throw "No APKs found. Build them first with -Pnuvio.android.splitAbis=true."
}

$credential = ("protocol=https`nhost=github.com`n`n" | & git credential fill 2>$null)
$passwordLine = $credential | Where-Object { $_ -like "password=*" }
if (-not $passwordLine) { throw "No stored GitHub credential. Run any push first so Git Credential Manager stores one." }

$token = $passwordLine.Substring("password=".Length)
$headers = @{
    Authorization = "Bearer $token"
    "User-Agent" = "opencode"
    Accept = "application/vnd.github+json"
}

$rows = @()
foreach ($apk in $apkFiles) {
    $abi = Get-AbiFromName $apk.Name
    $assetName = $apk.Name -replace "-debug\.apk$", "-release.apk"
    $sha256 = (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash
    $sizeMb = [math]::Round($apk.Length / 1MB, 1)
    $rows += [pscustomobject]@{
        Abi     = $abi
        File    = $assetName
        SizeMb  = $sizeMb
        Sha256  = $sha256
        Path    = $apk.FullName
    }
}

$table = $rows |
    ForEach-Object { "| ``$($_.Abi)`` | ``$($_.File)`` | $($_.SizeMb) MB | ``$($_.Sha256)`` |" } |
    Out-String

$notes = if ($NotesFile) {
    [IO.File]::ReadAllText((Resolve-Path -LiteralPath $NotesFile), [Text.Encoding]::UTF8)
} else {
    "Build $Tag from $TargetCommitish."
}
$body = "$notes`n`n---`n`n### Downloads`n`n| ABI | File | Size | SHA-256 |`n|---|---|---:|---|`n$table"

$payload = @{
    tag_name         = $Tag
    name             = $Name
    target_commitish = $TargetCommitish
    body             = $body
    draft            = $false
    prerelease       = [bool]$Prerelease
} | ConvertTo-Json -Depth 4

$release = Invoke-RestMethod -Method Post `
    -Uri "https://api.github.com/repos/$ForkRepo/releases" `
    -Headers $headers -ContentType "application/json" `
    -Body ([Text.Encoding]::UTF8.GetBytes($payload))

$uploadUrl = $release.upload_url -replace '\{\?name,label\}', ''

foreach ($row in $rows) {
    Write-Host "Uploading $($row.File) ($($row.SizeMb) MB)..."
    $responseFile = Join-Path $env:TEMP "github-upload-$($row.Abi).json"
    & curl.exe -sS --fail-with-body --retry 3 --retry-all-errors --max-time 1800 -X POST `
        -H "Authorization: Bearer $token" `
        -H "Accept: application/vnd.github+json" -H "User-Agent: opencode" `
        -H "Content-Type: application/vnd.android.package-archive" `
        --data-binary "@$($row.Path)" "${uploadUrl}?name=$($row.File)" -o $responseFile
    if ($LASTEXITCODE -ne 0) { throw "Upload failed for $($row.File) with exit code $LASTEXITCODE" }

    $uploaded = Get-Content $responseFile -Raw | ConvertFrom-Json
    Remove-Item $responseFile -Force
    if (-not $uploaded.browser_download_url) { throw "Upload response for $($row.File) has no download URL" }
    Write-Host "  $($uploaded.browser_download_url)"
}

Write-Host "Release published: $($release.html_url)"
