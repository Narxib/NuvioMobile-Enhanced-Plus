[CmdletBinding()]
param(
    [switch]$Push,
    [switch]$SkipBuild,
    [string]$UpstreamRemote = "upstream",
    [string]$UpstreamBranch = "enhanced",
    [string]$ForkRemote = "origin",
    [string]$ForkBranch = "main"
)

$ErrorActionPreference = "Stop"

function Invoke-Git {
    param([string[]]$GitArgs)
    & git @GitArgs
    if ($LASTEXITCODE -ne 0) { throw "git $($GitArgs -join ' ') failed with exit code $LASTEXITCODE" }
}

function Get-GitOutput {
    param([string[]]$GitArgs)
    $output = & git @GitArgs
    if ($LASTEXITCODE -ne 0) { throw "git $($GitArgs -join ' ') failed with exit code $LASTEXITCODE" }
    return ($output -join "`n").Trim()
}

Set-Location (Get-GitOutput @("rev-parse", "--show-toplevel"))

$branch = Get-GitOutput @("rev-parse", "--abbrev-ref", "HEAD")
if ($branch -eq "HEAD") { throw "Detached HEAD. Switch to $ForkBranch first." }
if ($branch -ne $ForkBranch) { throw "Expected branch $ForkBranch, found $branch." }
if (Get-GitOutput @("status", "--porcelain")) { throw "Working tree is dirty. Commit or stash first." }

Invoke-Git @("fetch", $UpstreamRemote, "--prune", "--tags")

$upstreamRef = "$UpstreamRemote/$UpstreamBranch"
& git merge-base --is-ancestor $upstreamRef HEAD
if ($LASTEXITCODE -eq 0) {
    Write-Host "Already up to date with $upstreamRef."
    return
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupBranch = "backup/pre-sync-$stamp"
Invoke-Git @("branch", $backupBranch)
Invoke-Git @("tag", "upstream-base-$stamp", $upstreamRef)
Write-Host "Backup branch: $backupBranch"
Write-Host "Upstream base tag: upstream-base-$stamp"

& git rebase $upstreamRef
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Rebase stopped. Resolve the conflicts, then 'git rebase --continue'." -ForegroundColor Yellow
    Write-Host "To discard everything: git rebase --abort" -ForegroundColor Yellow
    exit 1
}

if (-not $SkipBuild) {
    $env:ANDROID_HOME = "D:\Android\Sdk"
    $env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
    $env:GRADLE_USER_HOME = "D:\gradle-home"
    $env:RUSTUP_HOME = "D:\rustup"
    $env:CARGO_HOME = "D:\cargo"
    $env:Path = "D:\mingw64\bin;D:\cargo\bin;$env:Path"

    & .\gradlew.bat ":androidApp:assembleFullDebug" "-Pnuvio.android.distribution=full" "-Pnuvio.ios.distribution=appstore" --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed. Restore the previous state with: git reset --hard $backupBranch"
    }
}

if ($Push) {
    Invoke-Git @("push", "--force-with-lease", $ForkRemote, $branch)
    Write-Host "Pushed $branch to $ForkRemote."
} else {
    Write-Host "Rebase done. Push with: git push --force-with-lease $ForkRemote $branch"
}
