# 💻 Fetchreel Windows Releases

This directory houses the standalone desktop application builds for Windows.

---

## 📦 Available Releases

### 🟢 Fetchreel Desktop v1.1.0 (Latest Release)
- **File:** [`Fetchreel.exe`](Fetchreel.exe)
- **Version:** `1.1.0`
- **Architecture:** `x86_64` (Windows 10 / Windows 11)
- **Packaging:** Standalone single-file executable (PyInstaller)
- **Runtime Requirement:** Zero external dependencies (no Python or FFmpeg install required)

#### 🛠️ What Was Solved & Implemented in v1.1.0:
1. **Zero Duplicate Analyzing (Instant Downloads):**
   - Eliminated the redundant double network request bug.
   - Initial URL probe data is cached in-memory and passed directly to the downloader via `ydl.process_ie_result(raw_info, download=True)`.
   - Download starts in under 5 milliseconds without re-analyzing the URL over the network.
2. **Fixed "1012p" Non-Standard Resolution Displays:**
   - Automatically classifies widescreen cinematic streams (1920×1012, 21:9) and vertical social media reels (1080×1920) into clean standard tiers:
     - **4K (2160p Ultra HD)**
     - **2K (1440p Quad HD)**
     - **1080p (Full HD)**
     - **720p (HD)**
     - **480p (SD)**
     - **360p**
3. **Fixed Low-Pixel (360p) Throttled Downloads:**
   - Bypassed YouTube's SABR mobile client restrictions by switching extraction requests to desktop web/tv clients.
   - Downloads crisp 1080p and 4K streams with full audio muxing.
4. **Solved Download Percent Jitter & "Stuck at 80%/85%" Bug:**
   - Fixed fragment 0 truthiness evaluation bug in Python where init chunk (712 bytes) was evaluated as 100% of the stream, immediately setting progress to 85% and permanently locking it.
   - Fixed `.webm` VP9 video container classification so video streams are never misidentified as audio.
   - Monotonic progress guarantee ensures percentage smoothly increases from 0% → 85% (video) → 95% (audio) → 100% (FFmpeg).
5. **Real-Time Download Size, Speed & Clean ETA:**
   - Live download cards display exact real-time progress: `downloaded / total • speed` (e.g. `24.5 MB / 106.3 MB • 2.4 MB/s`) and clean integer ETA (`ETA: 18s`).
6. **Concurrent Multi-Download Architecture:**
   - Single-download blocking removed; users can start multiple concurrent single or fast downloads.
   - Downloads dynamically stack in the downloads list with individual thread workers and independent `✕ Cancel` controls.
7. **Eliminated HTTP 403 Forbidden:**
   - Removed forced `tv` client parameter that triggered YouTube PO-Token challenges and HTTP 403 errors.
8. **1-Tap Fast Actions:**
   - Added instant one-tap `⚡ Fast Video` and `🎵 Fast MP3` download triggers for rapid downloading without waiting for full stream parsing.
9. **Batch Playlist & Channel Downloads:**
   - Full playlist probing with selectable checkbox list, individual status indicators, and total progress tracking.
10. **Built-in Media Library & QR Sharing:**
   - View, play, reveal, or delete downloaded media directly from the GUI.
   - Share button opens an on-screen QR code for direct Android APK installation.
