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
- **📈 Solved Progress Percentage Jitter & "Stuck at 80%/85%" Bug:**
  - **Root Cause 1 (Fragment 0 Truthiness):** In DASH/fragmented streaming, yt-dlp starts at fragment 0. The Python check `if frag_index and frag_count:` evaluated `0` as falsy, bypassing fragment calculation and falling back to `downloaded / total`. For fragment 0 (the init segment of 712 bytes), `downloaded == total`, which set stream fraction to 1.0 (85.0%) on the very first event, locking the monotonic progress clamp at 85% for the entire duration!
  - **Root Cause 2 (.webm Container Classification):** YouTube VP9 video streams in `.webm` containers were mistakenly matching audio extension rules, causing video downloads to be flagged as audio (starting at 85%).
  - **The Fix:**
    1. Replaced with `if frag_index is not None and frag_count and frag_count > 0:`, ensuring fragment 0 yields `0.0%` and climbs linearly (0% → 85%).
    2. Restricted audio extension matching strictly to `.m4a`, `.opus`, `.aac`, `.mp3` and prioritized `vcodec != "none"` detection.
    3. Added dynamic estimation of total file size for DASH streams (`(downloaded_bytes / frag_index) * frag_count`) so total size is always displayed accurately.
- **📊 Real-Time Download Size, Speed & Clean ETA:**
  - Progress card displays exact real-time download metrics: `downloaded / total • speed` (e.g. `24.5 MB / 106.3 MB • 2.4 MB/s`) alongside clean integer ETA (`ETA: 18s`).
- **🚀 Concurrent Multi-Download Architecture:**
  - Replaced single-download blocking with a dynamic multi-download manager.
  - Users can trigger multiple concurrent single or fast downloads simultaneously; each active download gets a dedicated `DownloadProgressCard` with individual thread workers and independent `✕ Cancel` controls.
- **🛡️ Eliminated HTTP 403 Forbidden Errors:**
  - Removed forced `tv` player client argument that triggered YouTube's PO-token requirement. Clean default yt-dlp extractor orchestration now extracts up to 4K streams smoothly without 403 errors.
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
- **📈 Multi-Stage Monotonic Download Progress:**
  - Implemented multi-stage progress scaling in `DownloaderManager.kt` (Video stream 0–85%, Audio track 85–95%, FFmpeg muxing 98%).
  - Removed `.webm` audio false-detection and eliminated progress jumping backwards when switching streams.
- **🛡️ Eliminated HTTP 403 Forbidden:**
  - Removed forced `tv` client parameter that triggered YouTube PO-Token challenges and HTTP 403 errors.
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
