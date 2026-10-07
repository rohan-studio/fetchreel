# Fetchreel for Windows — Modern Desktop Media Downloader

<p align="center">
  <b>A 100% on-device Windows video and audio downloader powered by yt-dlp & FFmpeg.</b><br>
  No external servers, no cloud tracking, completely private and offline-capable extraction.
</p>

<p align="center">
  <b>Standalone Binary:</b> <code>windows/Fetchreel.exe</code> &nbsp;•&nbsp;
  <b>Framework:</b> CustomTkinter (Material Dark UI) &nbsp;•&nbsp;
  <b>Engine:</b> yt-dlp + FFmpeg
</p>

---

## 💻 Features

- **⚡ 100% On-Device Native Processing:**
  Direct integration with `yt-dlp` and `FFmpeg` without intermediate web proxies, tracking, or cloud bottlenecks.
- **🎨 Modern Dark Material UI:**
  Designed with CustomTkinter to match the Fetchreel Android app palette (`#0F1117` background, `#161B26` surface, `#6366F1` indigo accents, and `#06B6D4` cyan highlights).
- **📋 Auto-Capture Clipboard Links:**
  Copy any video or playlist link from your browser, and Fetchreel automatically analyzes the media stream and prepares formats. Includes a toggle to enable/disable.
- **🎬 Single Video & Reel Downloader:**
  Probes metadata and available video heights (4K, 1440p, 1080p FHD, 720p HD, 480p, 360p) or extracts high-bitrate MP3 audio with cover metadata.
- **📑 Playlist & Channel Batch Downloader:**
  Analyzes complete YouTube playlists and albums with checkbox item selection, Select All/Deselect All shortcuts, format selection, and batch download progress.
- **📁 Downloaded Media Library:**
  Organized in your local `Downloads\Fetchreel` folder. Includes 1-click playback in your default Windows media player, "Show in Explorer", and safe file deletion.
- **📱 Built-in QR Code Sharing:**
  Click the "Mobile APK" button in the header to display a QR code allowing nearby Android devices to scan and download the companion Android APK.
- **🔄 One-Click OTA Engine Updates:**
  Click "Check Updates" in the header anytime to refresh yt-dlp extraction definitions over-the-air.
- **📦 Single Standalone Executable:**
  Packaged with PyInstaller into a single `Fetchreel.exe` file that runs instantly without Python or package installation.

---

## 🚀 Running Fetchreel

### Method 1: Standalone Binary (Recommended)
Simply double-click:
```text
windows\Fetchreel.exe
```
No installation or dependencies required!

### Method 2: From Source
```powershell
cd windows
pip install -r requirements.txt
python main.py
```

---

## 🛠️ Building from Source

To compile the single standalone `Fetchreel.exe`:

1. Run the one-click build script:
```powershell
cd windows
.\build.bat
```
or manually with PyInstaller:
```powershell
cd windows
pyinstaller --clean fetchreel.spec
```

The resulting `Fetchreel.exe` will be located in `windows\dist\Fetchreel.exe` and copied to `windows\Fetchreel.exe`.

---

## 📂 Project Architecture

```text
windows/
├── assets/
│   ├── icon.ico             # Multi-resolution application icon
│   └── icon.png             # Modern 512x512 logo
├── src/
│   └── fetchreel/
│       ├── engine/
│       │   ├── downloader.py       # Core yt-dlp & FFmpeg engine with progress hooks
│       │   ├── playlist.py         # Batch and playlist extractor & downloader
│       │   ├── storage.py          # Config, downloads directory & media history
│       │   └── updater.py          # In-app OTA engine updater
│       └── gui/
│           ├── theme.py            # Material Dark colors & typography tokens
│           ├── app.py              # Main CustomTkinter Application Window
│           └── components/
│               ├── header.py       # AppHeader with badges, OTA update, QR code, folder
│               ├── url_card.py     # Paste/clear URL input & analyze button
│               ├── media_card.py   # Video preview card with thumbnail & quality selector
│               ├── progress_card.py# Realtime download progress card with metrics
│               ├── playlist_view.py# Playlist batch selection & progress view
│               ├── recent_view.py  # Downloaded library manager (play, reveal, delete)
│               └── qr_dialog.py    # Scan & download mobile APK popup
├── main.py                         # High-DPI Windows application entry point
├── requirements.txt                # Python dependencies
├── build.bat                       # One-click PyInstaller build batch file
├── fetchreel.spec                  # PyInstaller spec configuration
└── README.md                       # Windows documentation
```

---

## 👨‍💻 Credits

- **Organization:** **Rohan Studio**
- **Developer:** **Rohan Arya**
- **Repository:** [rohan-studio/fetchreel](https://github.com/rohan-studio/fetchreel)
