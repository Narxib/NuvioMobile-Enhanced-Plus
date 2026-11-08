# Keeping the magnet fork in sync

## Remotes

| Remote | Repository | Purpose |
|---|---|---|
| `upstream` | `luqmanfadlli/NuvioMobile-Enhanced` | Original project, never written to |
| `origin` | `Narxib/NuvioMobile-Enhanced-Plus` | This fork |

The fork has a single branch, `main`: upstream plus the magnet feature. There is no mirror branch; `upstream/enhanced` is the reference.

## Syncing with an upstream update

```powershell
git switch main
.\scripts\sync-upstream.ps1
```

The script:

1. Requires a clean tree on `main`.
2. Runs `git fetch upstream --prune --tags`.
3. Creates a `backup/pre-sync-<date>` branch and an `upstream-base-<date>` tag.
4. Reapplies the magnet commits with `git rebase upstream/enhanced`.
5. Builds the debug APK to catch API drift.
6. With `-Push`, publishes using `--force-with-lease`.

Options: `-Push`, `-SkipBuild`.

If the rebase stops on conflicts: resolve them, `git add`, `git rebase --continue`. To undo everything, `git rebase --abort`. If the build fails after the rebase, `git reset --hard backup/pre-sync-<date>`.

`git rerere` is enabled, so conflicts resolved once are reapplied automatically on the next sync.

## Why rebase and not merge

A merge drags in sync commits and brings already-resolved conflicts back. Rebasing keeps `main` as a few clean commits on top of upstream, with a minimal, easy-to-audit diff. The branch is only yours, so rewriting its history is safe (`--force-with-lease`).

## Integration points with upstream code

The whole feature lives in `composeApp/src/commonMain/kotlin/com/nuvio/app/features/magnet/`. Outside of it there is only a handful of minimal hooks:

| File | Change | Conflict risk |
|---|---|---|
| `AndroidManifest.xml` | `magnet` and `SEND` intent filters | low |
| `LinkActivity.kt` | forwards magnet intents | low |
| `AppUrlBridge.kt` | `AppDeepLink.Magnet` | low |
| `MainAppContent.kt` | consumes the deep link | low |
| `Routes.kt`, `AppNavigationSupport.kt` | route and `entry<MagnetRoute>` | low |
| `iosApp/iosApp/Info.plist` | `magnet` URL scheme | low |
| `values/magnet_strings.xml`, `values-es/magnet_strings.xml` | feature strings | none |

Rules that keep the sync cheap:

- Do not reformat or clean up upstream files. Every line touched outside `features/magnet` is a potential conflict.
- New strings go in `magnet_strings.xml`, never in `strings.xml`.
- Feature tests live in `commonTest/.../features/magnet/`.
- After every sync, install the APK and try one movie and one series magnet.

## Publishing an update

Compile first with ABI splits, so each release asset only carries one architecture:

```powershell
.\gradlew.bat ":androidApp:assembleFullDebug" "-Pnuvio.android.distribution=full" "-Pnuvio.ios.distribution=appstore" "-Pnuvio.android.splitAbis=true"
```

That produces four APKs in `androidApp/build/outputs/apk/full/debug/`: `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`. Then:

```powershell
.\scripts\publish-release.ps1 -Tag "0.6.9" -Name "0.6.9" -NotesFile notes.md
```

The script picks up every APK in that directory, creates the release from `main`, computes each SHA-256 and uploads them as separate assets. It uses the token already stored by Git Credential Manager.

Releases follow the official app's naming: the tag and title are the plain version (`0.6.9`, no `v`), and the assets keep the Gradle names with `-release` instead of `-debug` (`androidApp-full-arm64-v8a-release.apk`, `androidApp-full-armeabi-v7a-release.apk`, `androidApp-full-x86-release.apk`, `androidApp-full-x86_64-release.apk`).

APKs are **debug** builds (debug signature, `com.nuviodebug.com`) despite the `-release` file name: meant for personal use and installable alongside the official app. Debug APKs are still larger than real release ones because R8 is not applied; the split only removes the other three architectures.

## If you ever want to upstream it

With the feature isolated in a few commits, it can be sent as a PR to `upstream/enhanced`. Until then, rebasing is enough.
