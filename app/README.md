# Terbian

Debian 12 terminal for Android (PRoot, no root). Multi-tab sessions, Material 3
interface, themes (black by default), extra keys row and a foreground service that
keeps sessions alive.

## Features

- Debian (PRoot) and local Android shell sessions
- Long-press a tab for Close this / Close other / Close all
- Themes: Black, Burnt espresso, Gruvbox, Solarized dark, Dracula, Nord, Light paper
- Adaptive vector launcher icon with monochrome layer

## Build

```
./gradlew assembleDebug
```

Options (Gradle properties): `-PskipNative`, `-ProotfsArch=arm64v8|arm32v7|amd64|i386`,
`-PprootAbis="arm64-v8a armeabi-v7a"`, `-PforceProot`.

PRoot can also be built with the GitHub Actions workflow in `.github/workflows/build-proot.yml`
or with `tools/build-proot.sh`. The Debian rootfs is fetched by `tools/fetch-rootfs.sh`.

Release signing: create `release.properties` (ignored by git) with `storeFile`,
`storePassword`, `keyAlias`, `keyPassword`. Never commit keystores.

## License

Copyright 2026 deepcodecreate

Licensed under the [Apache License, Version 2.0](../LICENSE). Third-party components are listed in
[NOTICE](../NOTICE).
