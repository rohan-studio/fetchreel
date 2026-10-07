# 📝 Fetchreel Changelog

All notable changes to the Fetchreel project across Android and Windows will be documented in this file.

---

## [v1.1.0] - 2026-10-07

### 🌟 Key Highlights & Issues Solved
This release introduces high-definition unthrottled streaming across both platforms, fixes resolution mapping quirks, and eliminates redundant extraction overhead.

---

### 💻 Windows Desktop (v1.1.0)
- **⚡ Solved Duplicate Analyzing:**
  - Resolved the bug where probing a link and subsequently clicking "Download" caused a second full network scrape.
  - Implemented an in-memory cache and integrated `ydl.process_ie_result(raw_info, download=True)` to directly reuse probed stream metadata. Downloads now initiate in less than 5 milliseconds.
- **🎯 Solved "1012p" Non-Standard Resolution Displays:**
  - Videos with letterboxing (such as 1920×1012 widescreen movie trailers) and vertical reels (1080×1920) are now intelligently mapped into standard industry tiers: **4K (2160p Ultra HD)**, **2K (1440p Quad HD)**, **1080p (Full HD)**, **720p (HD)**, **480p (SD)**, and **360p**.
- **🚀 Fixed Low-Pixel (360p) YouTube Capping:**
  - Replaced mobile player client queries (`android`, `ios`) that were being throttled by YouTube's SABR experiment with desktop `web,tv` client requests.
  - Unlocked true 1080p, 1440p, and 4K stream extraction with clean FFmpeg audio muxing.
- **📈 Solved Progress Percentage Jitter (Increasing & Decreasing):**
  - **Root Cause:** yt-dlp downloads separate video and audio streams sequentially. In raw progress hooks, when the video stream reached 100%, the audio stream started from 0%, causing the progress percentage to drop backwards. Additionally, fluctuating `total_bytes_estimate` during DASH/chunked streaming caused percentage jumps.
  - **The Fix:**
    1. **Multi-Stream Stage Partitioning:** Video download maps smoothly from 0% to 80%, Audio download maps from 80% to 95%, and FFmpeg muxing maps from 95% to 99%.
    2. **Strict Monotonic Clamping:** Progress is mathematically guaranteed to only increase (`max_seen_percent`), completely preventing backward jumps.
    3. **Fragment-Aware Tracking:** Utilizes linear fragment counts (`fragment_index / fragment_count`) for DASH/HLS streams.
- **⚡ Added 1-Tap Fast Actions:**
  - Added dedicated `⚡ Fast Video` and `🎵 Fast MP3` buttons allowing users to start downloads instantly without waiting for the full format list to populate.
- **📑 Playlist & Channel Batch Downloader:**
  - Added flat playlist probing, item checkboxes, select all/none controls, and per-item download tracking.
- **📦 Standalone Executable:**
  - Bundled as a single, portable `Fetchreel.exe` requiring no external Python or FFmpeg installation.

---

### 📱 Android Mobile (v1.1.0)
- **🎯 Solved "1012p" Non-Standard Resolution Labels:**
  - Replaced raw format height strings (`"${height}p"`) with an intelligent aspect-ratio dimension classifier (`min(w, h)` and `max(w, h)`).
  - Letterboxed 1920×1012 videos and 1080×1920 vertical reels now cleanly display as **1080p (Full HD)**, **720p (HD)**, etc.
- **🚀 Fixed Low-Pixel (360p) Throttled YouTube Downloads:**
  - Switched extractor arguments from `player_client=android,ios` to `player_client=web,tv` to avoid SABR mobile 360p restrictions.
  - Restored high-bitrate 1080p and 4K video downloads.
- **💎 Exact Format ID & Bitrate Binding:**
  - Formats within each tier are evaluated for peak bitrate (`maxOf(tbr, vbr)`) and bound directly to `$fid+bestaudio` for maximum audiovisual fidelity.
- **🔢 Version Bump:**
  - Updated `versionCode = 2` and `versionName = "1.1.0"` in `android/app/build.gradle.kts`.

---

## [v1.0.0] - 2026-10-07

### 📱 Android Mobile (v1.0.0)
- Initial public release of Fetchreel for Android.
- 100% on-device media extraction powered by embedded Python 3.11 and QuickJS engine.
- Snaptube-style direct overlay share popup for quick downloads from Instagram, YouTube, TikTok, and X.
- Foreground download service with persistent notification and CPU WakeLock.
- Local media management in `Download/Fetchreel`.
- Release binary preserved as `assets/android/fetchreel-v1.0.0.apk`.
