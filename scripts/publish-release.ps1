[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Tag,
    [string]$Name,
    [string]$NotesFile,
    [string]$NotesFrom,
    [string]$NotesOut,
    [string[]]$ApkPath,
    [string]$ApkDir = "androidApp/build/outputs/apk/full/optimized",
    [string]$ForkRepo = "Narxib/NuvioMobile-Enhanced-Plus",
    [string]$TargetCommitish = "main",
    [switch]$Prerelease,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
Set-Location (git rev-parse --show-toplevel)

if (-not $Name) { $Name = $Tag }

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

# Release notes are generated from the commits since the previous published release so the
# in-app What's New screen has real content. Sections named "What's Changed" are rendered as
# the changelog list, while "Downloads" is ignored by the app.
$separator = [char]31

function Resolve-TagCommit {
    param([string]$TagName)
    $local = git rev-parse --verify --quiet "$TagName^{commit}"
    if ($LASTEXITCODE -eq 0 -and $local) { return $local }

    $remoteTags = git ls-remote --tags "https://github.com/$ForkRepo.git"
    $entries = $remoteTags | ForEach-Object {
        $parts = $_ -split "`t"
        if ($parts.Length -eq 2) { [pscustomobject]@{ Sha = $parts[0]; Ref = $parts[1] } }
    }
    $peeled = $entries | Where-Object { $_.Ref -eq "refs/tags/$TagName^{}" } | Select-Object -First 1
    if ($peeled) { return $peeled.Sha }
    $direct = $entries | Where-Object { $_.Ref -eq "refs/tags/$TagName" } | Select-Object -First 1
    if ($direct) { return $direct.Sha }
    return $null
}

function Get-PreviousReleaseTag {
    $releases = Invoke-RestMethod -Method Get `
        -Uri "https://api.github.com/repos/$ForkRepo/releases?per_page=20" `
        -Headers $headers
    $releases |
        Where-Object { $_.tag_name -and $_.tag_name -ne $Tag -and -not $_.draft } |
        Select-Object -First 1 -ExpandProperty tag_name
}

function Test-ReleaseNoteSubject {
    param([string]$Subject)
    $subjectLower = $Subject.ToLowerInvariant()
    if ($subjectLower.Contains("[skip release notes]")) { return $false }
    if ($subjectLower -match '^(bump(\s.*)?version|version\s+bump)(\s.*)?$') { return $false }
    if ($subjectLower -match '^cleanup([\s\p{P}].*)?$') { return $false }
    if ($subjectLower -match '^(build|chore|ci|docs|style|test)(\([^)]*\))?:') { return $false }
    return $true
}

$script:authorCache = @{}
$script:localUserEmail = (git config user.email).Trim().ToLowerInvariant()
$script:forkOwner = $ForkRepo.Split('/')[0]

function Resolve-CommitAuthor {
    param([string]$Sha, [string]$AuthorName, [string]$AuthorEmail)

    # Commits made from this clone are attributed to the fork owner so the notes never ping an
    # unrelated account that happens to own the local git email address.
    if ($script:localUserEmail -and $AuthorEmail.Trim().ToLowerInvariant() -eq $script:localUserEmail) {
        return $script:forkOwner
    }
    if ($AuthorEmail -match '^(?:\d+\+)?([^@]+)@users\.noreply\.github\.com$') {
        return $Matches[1]
    }
    if ($script:authorCache.ContainsKey($AuthorEmail)) { return $script:authorCache[$AuthorEmail] }

    $login = ""
    try {
        $login = (Invoke-RestMethod -Method Get `
            -Uri "https://api.github.com/repos/$ForkRepo/commits/$Sha" `
            -Headers $headers).author.login
    } catch {
        $login = ""
    }
    if (-not $login) {
        $login = ($AuthorName -replace '[^A-Za-z0-9_-]', '')
        if (-not $login) { $login = "unknown" }
    }
    $script:authorCache[$AuthorEmail] = $login
    return $login
}

function Get-NotesFromCommits {
    param([string]$FromRef)

    $format = "%H$separator%s$separator%an$separator%ae"
    $lines = git log "$FromRef..$TargetCommitish" --no-merges "--format=$format"
    if ($LASTEXITCODE -ne 0) { throw "git log failed for range $FromRef..$TargetCommitish" }

    $seen = New-Object 'System.Collections.Generic.HashSet[string]'
    $bullets = New-Object 'System.Collections.Generic.List[string]'
    foreach ($line in $lines) {
        if (-not $line) { continue }
        $parts = $line -split [regex]::Escape([string]$separator)
        if ($parts.Length -lt 4) { continue }
        $sha, $subject, $authorName, $authorEmail = $parts[0], $parts[1], $parts[2], $parts[3]

        if (-not (Test-ReleaseNoteSubject $subject)) { continue }
        $normalizedSubject = ($subject.ToLowerInvariant() -replace '\s+', ' ' -replace '[\s.]+$', '')
        if (-not $seen.Add($normalizedSubject)) { continue }

        $displaySubject = $subject -replace '\s+$', '' -replace '\.$', ''
        $author = Resolve-CommitAuthor $sha $authorName $authorEmail
        $bullets.Add("- $displaySubject @$author")
    }
    return $bullets
}

$notes = if ($NotesFile) {
    [IO.File]::ReadAllText((Resolve-Path -LiteralPath $NotesFile), [Text.Encoding]::UTF8)
} else {
    $fromRef = $NotesFrom
    if (-not $fromRef) {
        $previousTag = Get-PreviousReleaseTag
        if ($previousTag) {
            $fromRef = Resolve-TagCommit $previousTag
            Write-Host "Generating release notes from $previousTag ($fromRef) to $TargetCommitish"
        }
    }
    if (-not $fromRef) {
        Write-Host "No previous release found; release notes cover the last 50 commits."
        $fromRef = "$TargetCommitish~50"
    }

    $bullets = Get-NotesFromCommits $fromRef
    if ($bullets.Count -eq 0) {
        "## What's Changed`n`nMaintenance release built from $TargetCommitish."
    } else {
        "## What's Changed`n`n" + ($bullets -join "`n")
    }
}

if ($NotesOut) {
    [IO.File]::WriteAllText(
        [IO.Path]::GetFullPath((Join-Path (Get-Location) $NotesOut)),
        $notes,
        [Text.UTF8Encoding]::new($false)
    )
    Write-Host "Release notes written to $NotesOut"
}

$rows = @()
foreach ($apk in $apkFiles) {
    $abi = Get-AbiFromName $apk.Name
    $assetName = $apk.Name -replace "-(debug|optimized)\.apk$", "-release.apk"
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

$body = "$notes`n`n---`n`n### Downloads`n`n| ABI | File | Size | SHA-256 |`n|---|---|---:|---|`n$table"

if ($DryRun) {
    Write-Host "--- Release notes for $Tag ---"
    Write-Host $notes
    Write-Host ""
    Write-Host "--- Assets ---"
    foreach ($row in $rows) { Write-Host "$($row.Abi)  $($row.File)  $($row.SizeMb) MB" }
    Write-Host ""
    Write-Host "Dry run: nothing was published."
    exit 0
}

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
