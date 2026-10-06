# Fetchreel — Native Android Media Downloader

<p align="center">
  <b>A 100% on-device Android video and audio downloader powered by yt-dlp & FFmpeg.</b><br>
  No external servers, no cloud tracking, completely private and offline-capable extraction.
</p>

<p align="center">
  <a href="assets/fetchreel-v1.0.0.apk"><b>⬇️ Download APK (v1.0.0)</b></a>
</p>

---

> [!WARNING]
> **DISCLAIMER: FOR EDUCATIONAL & RESEARCH PURPOSES ONLY**
> Fetchreel is developed strictly for educational, technical demonstration, and personal backup purposes. Please respect intellectual property laws, copyright regulations, and the Terms of Service of content providers. The developers assume no responsibility for any unauthorized usage.

---

## 📱 Features

- **⚡ 100% On-Device Extraction:**
  Runs the full modern `yt-dlp` and `FFmpeg` engines directly on your phone using an embedded Python 3.11+ and QuickJS runtime. No third-party proxy or web server is involved.
- **✨ Snaptube-Style Direct Share Popup:**
  Share any video from Instagram, YouTube, Facebook, TikTok, or X directly to Fetchreel. A sleek translucent popup slides up right over your current app with 1-tap options for **MP3 Audio**, **4K (2160p)**, **1080p FHD**, **720p HD**, etc. Tapping download starts the background service and immediately closes the popup so you stay in your feed!
- **📋 Auto-Capture Clipboard Links:**
  Copy a link from YouTube, Instagram, TikTok, or X, switch back to Fetchreel, and the app automatically captures and analyzes the media instantly. Includes a toggle to enable/disable auto-capture.
- **🔄 Background Download Engine:**
  Downloads run via an Android Foreground Service with continuous notification progress and a partial CPU `WakeLock` — preventing Android from pausing or killing your download when the screen turns off.
- **🎥 Multi-Resolution & MP3 Audio:**
  Choose between highest quality 1080p+, balanced 720p, lightweight 480p/360p, or extract direct high-bitrate MP3 audio.
- **📁 Media Management (Play, Share & Delete):**
  Downloaded media is organized into `Download/Fetchreel` on your device. Easily preview with internal/external players, share to messaging apps, or permanently delete items with a safe confirmation dialog.
- **📱 Built-in QR Code Sharing:**
  Tap the QR code button in the app header or floating action button to display a QR code allowing nearby devices to scan and download the APK directly.
- **🔄 In-App One-Click Engine Updates:**
  Tap the refresh icon in the header anytime to pull the newest yt-dlp extraction definitions over-the-air (OTA) without needing to reinstall the app.
- **🎨 Modern Dark UI:**
  Built with Jetpack Compose and Material 3 design for high performance, smooth animations, and edge-to-edge aesthetics.

---

## 📥 Download & Install

| Asset | Version | Architecture | Size | Requirements |
| :--- | :--- | :--- | :--- | :--- |
| [**fetchreel-v1.0.0.apk**](assets/fetchreel-v1.0.0.apk) | `1.0.0` | `arm64-v8a` | ~68 MB | Android 8.0+ (API 26+) |

### Installation Steps:
1. Download [`assets/fetchreel-v1.0.0.apk`](assets/fetchreel-v1.0.0.apk) directly to your Android device or scan the QR code within the app.
2. Tap the downloaded APK in your file manager or notification panel.
3. Allow "Install unknown apps" permission if prompted by Android.
4. Launch **Fetchreel** and start downloading!

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin 1.9.23
- **UI Toolkit:** Jetpack Compose + Material 3
- **Core Engine:**
  - `io.github.junkfood02.youtubedl-android:library:0.18.1` (Bundled Python 3.11+ & QuickJS engine)
  - `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1` (Mobile FFmpeg binary for audio/video muxing)
- **Background Service:** Android Foreground Service with `WAKE_LOCK` and notification channels
- **Storage:** Scoped Storage & MediaStore API (`Environment.DIRECTORY_DOWNLOADS/Fetchreel`)
- **QR Code:** ZXing (`com.google.zxing:core:3.5.3`)
- **Image Loading:** Coil Compose 2.6.0
- **Asynchronous Execution:** Kotlin Coroutines & StateFlow / SharedFlow

---

## 💻 Building from Source

### Prerequisites:
- Android SDK with API 34 installed
- JDK 17
- Gradle 8.7+

### Build Debug APK:
```bash
./gradlew assembleDebug
```
The compiled APK will be output to:
`app/build/outputs/apk/debug/app-debug.apk`

### Install directly to a connected ADB device:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 👨‍💻 Credits & Organization

- **Organization:** **Rohan Studio**
- **Developer:** **Rohan Arya**
- **Repository:** [rohan-studio/fetchreel](https://github.com/rohan-studio/fetchreel)

---

## 📄 License & Terms

This project is licensed for educational and personal use only. All trademarks, logos, and brand names are the property of their respective owners.
