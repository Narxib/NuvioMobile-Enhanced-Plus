<div align="center">

  <img src="https://nuvio.tv/assets/nuvio-app-logo-wordmark.webp" alt="Nuvio" width="320" />

  <h1>Nuvio Enhanced Plus</h1>

  <p>
    An unofficial fork of <a href="https://github.com/luqmanfadlli/NuvioMobile-Enhanced.git"> Nuvio Mobile-Enhanced </a> wich is a fork of the origina <a href="https://github.com/NuvioMedia/NuvioMobile.git"> Nuvio Mobile </a> that keeps pace with
    upstream and adds the features and platform polish on top of it.
    <br /><br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  <p>
    <a href="https://github.com/Narxib/NuvioMobile-Enhanced-Plus/releases/latest">Releases</a> ·
    <a href="https://github.com/NuvioMedia/NuvioMobile">Upstream project</a> ·
    <a href="https://nuvio.tv">nuvio.tv</a> ·
    <a href="https://nuvio.tv/support">Support Nuvio</a>
  </p>

</div>

> **Unofficial.** This fork is not affiliated with or supported by the Nuvio team. Please report bugs you find here to
> **this** repository, not upstream — unless you can reproduce them on an official build too.

---

## What Enhanced PLus
At the momment the only exclusive feature is the ability to parse magnet links from external sources. I may or may not add more features if i feel like

## Install
Just download the apk from <a href="https://github.com/Narxib/NuvioMobile-Enhanced-Plus/releases/latest"> releases</a>

## Build from source

```bash
git clone https://github.com/Narxib/NuvioMobile-Enhanced-Plus.git
cd NuvioMobile-Enhanced-Plus
git checkout enhanced
```

### Android

Requires Android Studio and the Android SDK.

```bash
./gradlew :androidApp:assembleFullDebug        # sideload flavour
./gradlew :androidApp:assemblePlaystoreDebug   # store flavour
```

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

This fork tracks `upstream/cmp-rewrite` and merges upstream releases as they land. If you are working on the fork:

```bash
git remote add upstream https://github.com/NuvioMedia/NuvioMobile.git
git fetch upstream
git merge upstream/cmp-rewrite
```

---

## Credits

Nuvio is built by [NuvioMedia](https://github.com/NuvioMedia) — all credit for the app itself belongs to them and its
contributors. This repository only adds to their work. If you enjoy Nuvio, [support the upstream project](https://nuvio.tv/support).

## License

[GNU General Public License v3.0](./LICENSE) — same as upstream.
