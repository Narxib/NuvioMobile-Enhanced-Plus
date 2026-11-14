<div align="center">

  <img src="https://nuvio.tv/assets/nuvio-app-logo-wordmark.webp" alt="Nuvio" width="320" />

  <h1>Nuvio Enhanced Plus</h1>

  <h2>ALL OF THIS HAS BEEN VIBECODED , EVEN THIS README
    
 IT MAY CONTAIN BUGS OR ERRORS </h2>

  <p>
    An unofficial fork of <a href="https://github.com/luqmanfadlli/NuvioMobile-Enhanced">Nuvio Enhanced</a>, itself a
    fork of <a href="https://github.com/NuvioMedia/NuvioMobile">Nuvio Mobile</a>.
    It tracks Enhanced and adds external magnet link support on top.
    <br /><br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  <p>
    <a href="https://github.com/Narxib/NuvioMobile-Enhanced-Plus/releases/latest">Releases</a> ·
    <a href="https://github.com/luqmanfadlli/NuvioMobile-Enhanced">Nuvio Enhanced</a> ·
    <a href="https://github.com/NuvioMedia/NuvioMobile">Nuvio Mobile</a> ·
    <a href="https://nuvio.tv/support">Support Nuvio</a>
  </p>

</div>

> **Unofficial.** This fork is not affiliated with or supported by the Nuvio team. Report bugs you find here to
> **this** repository, not upstream — unless you can reproduce them on Nuvio Enhanced or an official build too.

---


## What Enhanced Plus adds

Right now the only exclusive feature is the ability to open magnet links from external sources. More may come later.

- Opens `magnet:` links from browsers, other apps or the share sheet.
- Automatic TMDB matching: poster, backdrop, overview, year and episode info.
- Multi-language search (configured language, English and Spanish) with a sequel-suffix retry.
- Manual title search inside the magnet screen.
- P2P playback reusing the existing engine and player.

Everything else comes from [Nuvio Enhanced](https://github.com/luqmanfadlli/NuvioMobile-Enhanced) and
[Nuvio Mobile](https://github.com/NuvioMedia/NuvioMobile).



## Install


Download the APK from [Releases](https://github.com/Narxib/NuvioMobile-Enhanced-Plus/releases/latest).

- **Optimized build**: R8 minification and resource shrinking, signed with the debug key. Personal use, not for the
  Play Store.
- Installs as `com.nuviodebug.com`, so it can live alongside the official app. It replaces other debug builds of
  Enhanced or Plus, since they share the same application id.
- Shows up as **Nuvio+** on the launcher, with its own icon and splash screen.
- Requires a TMDB API key in **Settings → Content & Discovery → TMDB**.
- P2P is disabled by default. Enable it in Settings and accept the legal notice before playing.

## Build from source

```bash
git clone https://github.com/Narxib/NuvioMobile-Enhanced-Plus.git
cd NuvioMobile-Enhanced-Plus
git switch main
```

### Android

Requires Android Studio and the Android SDK.

```bash
./gradlew :androidApp:assembleFullOptimized        # sideload flavour (minified, debug key)
./gradlew :androidApp:assemblePlaystoreOptimized   # store flavour
```

`assembleFullDebug` still works for quick iteration, but it is **not** what gets published: without R8 the dex is about
4x larger and every per-ABI APK grows to ~80 MB instead of ~45 MB.

### iOS

Requires macOS and Xcode.

```bash
env NUVIO_IOS_DISTRIBUTION=full xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build/ios-derived-full-simulator \
  CODE_SIGNING_ALLOWED=NO \
  build
```

## Staying in sync

`main` is the only branch: everything from `upstream/enhanced` plus the magnet feature. To absorb an Enhanced update:

```powershell
git remote add upstream https://github.com/luqmanfadlli/NuvioMobile-Enhanced.git   # once
git switch main
.\scripts\sync-upstream.ps1 -Push
```

The script rebases the magnet commits onto `upstream/enhanced`, builds the APK to catch API drift, and pushes with
`--force-with-lease`. See [scripts/magnet-sync.md](./scripts/magnet-sync.md) for conflict handling and the integration
points with upstream code.

## Publishing a release

```powershell
.\gradlew.bat :androidApp:assembleFullOptimized "-Pnuvio.android.distribution=full" "-Pnuvio.ios.distribution=appstore"
.\scripts\publish-release.ps1 -Tag "0.6.11" -Name "0.6.11" -NotesFile notes.md
```

It creates the GitHub release from `main`, computes the SHA-256 and uploads each per-ABI APK with `curl` (handles the
~47 MB files without stalling).

Releases are signed with the local `~/.android/debug.keystore`. **Keep a backup of that file**: if it changes, the new
APK cannot update an existing install and users have to uninstall first.

---

## Credits

Nuvio is built by [NuvioMedia](https://github.com/NuvioMedia) — all credit for the app itself belongs to them and its
contributors. [Nuvio Enhanced](https://github.com/luqmanfadlli/NuvioMobile-Enhanced) adds the platform features this
fork builds on. If you enjoy Nuvio, [support the upstream project](https://nuvio.tv/support).

## License

[GNU General Public License v3.0](./LICENSE) — same as upstream.
