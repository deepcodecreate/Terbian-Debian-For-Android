<p align="center">
  <img src="https://img.shields.io/badge/Terbian-Debian%20%2B%20Termux-1e1e2e?style=for-the-badge&logo=debian&logoColor=A81D33" alt="Terbian"/>
</p>

<p align="center">
  <strong>A polished, native-feeling fusion of Termux terminal emulator and a full Debian userland on Android</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Min%20SDK-26-blue?style=flat-square"/>
  <img src="https://img.shields.io/badge/Target%20SDK-36-blue?style=flat-square"/>
  <img src="https://img.shields.io/badge/License-Apache%202.0-green?style=flat-square"/>
  <img src="https://img.shields.io/badge/Architecture-arm64%20%7C%20armv7-orange?style=flat-square"/>
</p>

---

## Overview

**Terbian** is a carefully crafted Android application that delivers a seamless combination of a high-quality terminal emulator (derived from Termux’s excellent terminal stack) and a complete Debian environment running via **PRoot**.

It is not a simple wrapper. It embeds a pre-built Debian rootfs, extracts it on first launch with progress feedback, and provides a modern multi-session UI with themes, extra keys, and a foreground service that keeps long-running processes (apt, builds, servers…) alive even when the app is in the background.

---

## Key Features

| Feature | Description |
|---------|-------------|
| **Full Debian Userland** | Pre-packaged rootfs (`rootfs.tb`) extracted into app-private storage |
| **PRoot Isolation** | Real Debian binaries running without root via PRoot |
| **Multi-Session Tabs** | Create, switch, and close Debian or pure shell sessions with a ViewPager2 + TabLayout |
| **Native Terminal Emulator** | High-performance terminal view based on Termux terminal-emulator |
| **Extra Keys Bar** | Configurable extra keys (Ctrl, Alt, Esc, arrows, etc.) |
| **Themes & Customization** | Multiple color schemes + runtime theme application |
| **Foreground Service** | Keeps sessions alive for background package operations |
| **Architecture Detection** | Automatic detection of device ABI |
| **Clean First-Run Experience** | Progress overlay during rootfs extraction & verification |
| **Modern Android UX** | Edge-to-edge, Material components, ViewBinding, MVVM |

---

## Screenshots / Visual Identity

> *(Add your own screenshots here)*

```
┌─────────────────────────────────────┐
│  Terbian          [+]  [☰]                │
├─────────────────────────────────────┤
│  Debian  │  Shell  │  +                   │
├─────────────────────────────────────┤
│                                           │
│  root@localhost:~# apt update             │
│  ...                                      │
│                                           │
├─────────────────────────────────────┤
│  Esc Ctrl Alt Tab ← ↑ ↓ →                │
└─────────────────────────────────────┘
```

---

## Architecture

```
Terbian
├── UI Layer
│   ├── MainActivity (sessions + toolbar)
│   ├── TerminalFragment
│   ├── SettingsActivity
│   └── ExtraKeysView
├── Terminal Stack
│   ├── TerbianTerminalView
│   ├── SessionRegistry
│   └── ProotSessionFactory
├── Debian Layer
│   ├── RootfsInstaller (extraction + verification)
│   ├── RootfsManager
│   ├── ProotExec
│   └── ArchitectureDetector
└── Service
    └── TerminalService (foreground, specialUse)
```

---

## Requirements

- Android 8.0 (API 26) or higher
- ~150–250 MB free storage (for rootfs + packages)
- Recommended: arm64-v8a device

---

## Building from Source

```bash
git clone https://github.com/deepcodecreate/Terbian-Debian-For-Android.git
cd Terbian-Debian-For-Android

# Optional: configure signing
# Edit release.properties with your keystore details

./gradlew assembleRelease
```

The signed (or debug) APK will be generated under `app/build/outputs/apk/`.

---

## First Launch Flow

1. App starts → checks whether Debian rootfs already exists
2. If missing → shows a live progress overlay while extracting `assets/debian/rootfs.tb`
3. After successful verification → first Debian session is created automatically
4. You can create additional **Debian** or pure **Shell** sessions at any time

---

## Session Types

| Type | Description |
|------|-------------|
| **Debian** | Full PRoot Debian environment (`/bin/bash` inside rootfs) |
| **Shell** | Lightweight native shell (no Debian rootfs) |

---

## License

```
Apache License
Version 2.0, January 2004
http://www.apache.org/licenses/

Copyright 2026 deepcodecreate

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

Full license text is available in [`app/LICENSE`](app/LICENSE).

---

## Credits & Inspiration

- Terminal emulator core inspired by **Termux**
- PRoot technology for user-space chroot
- Debian project for the excellent rootfs

---

## Contributing

Pull requests, issue reports, and suggestions are welcome.  
Please keep the code style consistent and test on both arm64 and armv7 if possible.

---

<p align="center">
  <strong>Terbian</strong> — Debian power, Termux elegance, Android native.
</p>

<p align="center">
  Made with ❤️ by <a href="https://github.com/deepcodecreate">deepcodecreate</a>
</p>
