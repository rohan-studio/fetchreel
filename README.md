# Fetchreel — Native Media Downloader (Android & Windows)

<p align="center">
  <b>100% on-device video and audio downloader powered by yt-dlp & FFmpeg.</b><br>
  No external servers, no cloud tracking, completely private and offline-capable extraction.
</p>

<p align="center">
  <a href="#-windows-desktop-app"><b>💻 Windows Desktop (Fetchreel.exe)</b></a> &nbsp;•&nbsp;
  <a href="#-android-mobile-app"><b>📱 Android App (v1.1.0 & v1.0.0)</b></a> &nbsp;•&nbsp;
  <a href="#-release-assets-overview"><b>📦 Release Assets</b></a> &nbsp;•&nbsp;
  <a href="CHANGELOG.md"><b>📝 Changelog</b></a>
</p>

---

> [!WARNING]
> **DISCLAIMER: FOR EDUCATIONAL & RESEARCH PURPOSES ONLY**
> Fetchreel is developed strictly for educational, technical demonstration, and personal backup purposes. Please respect intellectual property laws, copyright regulations, and the Terms of Service of content providers. The developers assume no responsibility for any unauthorized usage.

---

## 📁 Repository Structure

The project is structured into platform-specific modules with binaries categorized under `assets/`:

```text
fetchreel/
├── android/                 # 📱 Native Android App Project (Kotlin, Jetpack Compose, Material 3)
│   ├── app/                 # App source code, resources, manifest
│   ├── gradle/              # Gradle wrapper
│   └── build.gradle.kts     # Root build script
│
├── windows/                 # 💻 Modern Windows Desktop GUI (Python, CustomTkinter)
│   ├── src/fetchreel/       # Application source (engine & gui components)
│   ├── assets/              # App icons
│   ├── Fetchreel.exe        # 🚀 Standalone, single double-clickable executable
│   ├── build.bat            # One-click PyInstaller build script
│   └── requirements.txt     # Python dependencies
│
├── assets/                  # 📦 Release Binaries (Separated by Platform)
│   ├── android/             # 📱 Android APKs
│   │   ├── fetchreel-v1.0.0.apk  # ⚪ Previous baseline release (Preserved)
│   │   ├── fetchreel-v1.1.0.apk  # 🟢 Updated release (1080p/4K unthrottled & 1012p fix)
│   │   └── README.md             # Android release documentation
│   └── windows/             # 💻 Windows Standalone Executables
│       ├── Fetchreel.exe         # 🟢 v1.1.0 Single-file standalone executable
│       └── README.md             # Windows release documentation
│
├── CHANGELOG.md             # Full list of solved issues and version history
└── README.md                # Project documentation
```

---

## 📦 Release Assets Overview

Release binaries are organized into separate folders inside [`assets/`](assets/):

### 📱 Android Releases ([`assets/android/`](assets/android/))
- 🟢 **v1.1.0 (Latest):** [`assets/android/fetchreel-v1.1.0.apk`](assets/android/fetchreel-v1.1.0.apk)
  - **Solved 1012p Bug:** Standardized pixel tiers (1080p Full HD, 720p HD, 4K Ultra HD) for letterboxed and vertical reels.
  - **Solved 360p Throttling:** Bypassed YouTube's SABR mobile capping by switching to desktop extraction clients (`web,tv`).
  - **Peak Bitrate Binding:** Direct stream format binding (`$fid+bestaudio`).
- ⚪ **v1.0.0 (Legacy Baseline):** [`assets/android/fetchreel-v1.0.0.apk`](assets/android/fetchreel-v1.0.0.apk)
  - Initial baseline release preserved for compatibility and testing.

### 💻 Windows Releases ([`assets/windows/`](assets/windows/))
- 🟢 **v1.1.0 (Latest):** [`assets/windows/Fetchreel.exe`](assets/windows/Fetchreel.exe)
  - **Zero Duplicate Analyzing:** Instant download using cached probe data (`process_ie_result`).
  - **Clean Standard Tiers:** Fixed odd dimensions like `1012p`.
  - **Uncapped High Resolution:** Full 1080p/4K unthrottled downloads.
  - **1-Tap Fast Downloads:** Instant `⚡ Fast Video` and `🎵 Fast MP3` buttons.
  - **Self-Contained:** No Python or FFmpeg installation needed.

---

## 💻 Windows Desktop App

A fast, lightweight, and modern desktop application built with **CustomTkinter** featuring the exact same Material Dark theme as the Android app.

### 🌟 Key Windows Features:
- **📦 Single Standalone Executable (`Fetchreel.exe`):** Double-click and run immediately without installing Python or packages.
- **⚡ Native yt-dlp & FFmpeg Integration:** Direct stream probing, 4K/1080p video muxing, and high-bitrate MP3 audio extraction.
- **📋 Auto-Capture Clipboard:** Automatically detects copied YouTube, Instagram, TikTok, and X links.
- **🎬 Single Video & Reel Mode:** Thumbnail preview, duration badge, multi-resolution selection (Best, 4K, 1440p, 1080p, 720p, 480p, MP3).
- **📑 Playlist & Channel Batch Downloader:** Flat playlist scan, individual checkbox selection, Select All / Deselect All, and overall batch progress.
- **📁 Media Library Manager:** View recent downloads saved to `Downloads\Fetchreel` with 1-click playback in your default media player, reveal in Explorer, or delete.
- **📱 Built-in QR Code Sharing:** 1-click modal displaying QR code to scan and download the Android APK directly onto your phone.
- **🔄 Over-The-Air (OTA) Engine Updates:** Check and update yt-dlp extraction rules directly from within the app.

### 🚀 Running on Windows:
- **Executable:** Simply double-click [`assets/windows/Fetchreel.exe`](assets/windows/Fetchreel.exe) or `windows/Fetchreel.exe`!
- **From Source:**
  ```powershell
  cd windows
  pip install -r requirements.txt
  python main.py
  ```

---

## 📱 Android Mobile App

A native Android application built with **Kotlin** and **Jetpack Compose + Material 3**.

### 🌟 Key Android Features:
- **⚡ 100% On-Device Extraction:** Bundled Python 3.11+ and QuickJS runtime running on-device.
- **✨ Snaptube-Style Direct Share Popup:** Share directly from any app with a translucent overlay dialog.
- **📋 Auto-Capture Clipboard Links:** Automatically captures copied links when opening the app.
- **🔄 Foreground Download Service:** Continuous background downloads with CPU WakeLock and notification controls.
- **📁 Media Management:** Saves to `Download/Fetchreel` with media playback, sharing, and deletion.

### 📥 Android Download & Install:
1. Download either release from [`assets/android/`](assets/android/):
   - **[Recommended] Latest v1.1.0:** [`assets/android/fetchreel-v1.1.0.apk`](assets/android/fetchreel-v1.1.0.apk)
   - **Legacy Baseline v1.0.0:** [`assets/android/fetchreel-v1.0.0.apk`](assets/android/fetchreel-v1.0.0.apk)
2. Install the APK on your device (Android 8.0+ / arm64-v8a).

---

## 👨‍💻 Credits & Author

- **Organization:** **Rohan Studio**
- **Developer:** **Rohan Arya**
- **Repository:** [rohan-studio/fetchreel](https://github.com/rohan-studio/fetchreel)

---

## 📄 License & Terms

This project is licensed for educational and personal use only. All trademarks, logos, and brand names are the property of their respective owners.
