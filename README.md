Terbian

Terbian is an open-source Debian terminal environment for Android, inspired by Termux and built with Java. It brings the power of Linux to your Android device through PRoot, without requiring root access.

Designed for developers, Linux enthusiasts, and power users, Terbian combines a modern Material 3 interface with persistent terminal sessions, customizable themes, and a portable Debian environment.

Features

- Debian on Android — Run Debian 12 in a PRoot environment without root access.
- Local Shell — Access the native Android shell alongside Debian sessions.
- Multi-Tab Terminal — Manage multiple terminal sessions in one interface.
- Session Management — Long-press tabs to close the current session, other sessions, or all sessions.
- Material 3 UI — A modern Android interface with customizable terminal themes.
- Custom Themes — Black, Burnt Espresso, Gruvbox, Solarized Dark, Dracula, Nord, and Light Paper.
- Extra Keys Row — Quick access to essential terminal keys.
- Persistent Sessions — A foreground service helps keep terminal sessions alive while using other apps.
- Adaptive Launcher Icon — Includes a monochrome icon layer for supported Android launchers.

Built With


<h2 align="center">Built With</h2>

<p align="center">
  <a href="https://www.java.com/">
    <img src="https://cdn.simpleicons.org/openjdk/ED8B00" width="48" height="48" alt="Java"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://developer.android.com/">
    <img src="https://cdn.simpleicons.org/android/3DDC84" width="48" height="48" alt="Android"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://www.linux.org/">
    <img src="https://cdn.simpleicons.org/linux/FCC624" width="48" height="48" alt="Linux"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://www.debian.org/">
    <img src="https://cdn.simpleicons.org/debian/D70A53" width="48" height="48" alt="Debian"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://gradle.org/">
    <img src="https://cdn.simpleicons.org/gradle/02303A" width="48" height="48" alt="Gradle"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://m3.material.io/">
    <img src="https://cdn.simpleicons.org/materialdesign/757575" width="48" height="48" alt="Material Design"/>
  </a>
</p>

<p align="center">
  Java · Android · Linux · Debian · Gradle · Material 3
</p>


Build

Build the debug APK using Gradle:

./gradlew assembleDebug

Build Options

Customize the build using Gradle properties:

Property| Description
"-PskipNative"| Skip native component builds
"-ProotfsArch=arm64v8"| Select the Debian root filesystem architecture
"-PprootAbis="arm64-v8a armeabi-v7a""| Specify target Android ABIs
"-PforceProot"| Force the PRoot build configuration

Supported root filesystem architectures: "arm64v8", "arm32v7", "amd64", and "i386".

PRoot can also be built using the GitHub Actions workflow at ".github/workflows/build-proot.yml" or the local script at "tools/build-proot.sh".

The Debian root filesystem is downloaded using "tools/fetch-rootfs.sh".

Release Signing

Create a local "release.properties" file containing "storeFile", "storePassword", "keyAlias", and "keyPassword".

Keep your signing configuration and keystore private. Never commit them to version control.

Project Philosophy

Terbian aims to make Linux more accessible on Android by combining the flexibility of a Debian environment with the usability of a native Android application.

No root required. Just your Android device and a terminal.

License

Copyright 2026 deepcodecreate

Licensed under the "Apache License, Version 2.0" (../LICENSE).

Third-party components and their respective licenses are documented in "NOTICE" (../NOTICE).

Author

Created by deepcodecreate.

Explore the source code, contribute, and help make Linux on Android better.
