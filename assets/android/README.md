# 📱 Fetchreel Android Releases

This directory houses the Android APK release packages for Fetchreel.

---

## 📦 Available Releases

### 🟢 [Updated] Fetchreel v1.1.0 (Latest Release)
- **File:** [`fetchreel-v1.1.0.apk`](fetchreel-v1.1.0.apk)
- **Version Code:** `2`
- **Version Name:** `1.1.0`
- **Target OS:** Android 8.0+ (API 26+)
- **Architecture:** `arm64-v8a`
- **Release Date:** October 2026

#### 🛠️ What Was Solved in v1.1.0:
1. **Fixed Non-Standard "1012p" Resolution Labels:**
   - Previous builds displayed raw video heights (e.g., `1012p` for 1920×1012 widescreen letterboxed videos or Instagram reels).
   - v1.1.0 introduces an aspect-ratio-aware dimension classifier that categorizes streams into standard industry tiers:
     - **4K (2160p Ultra HD)**
     - **2K (1440p Quad HD)**
     - **1080p (Full HD)** (correctly includes 1920×1012 widescreen and 1080×1920 vertical reels)
     - **720p (HD)**
     - **480p (SD)**
     - **360p**
2. **Fixed Low-Pixel (360p) Throttled Downloads:**
   - Addressed YouTube's recent SABR experiment which hard-capped mobile clients (`android`, `ios`) without PO-Tokens to 360p.
   - Updated extractor arguments to utilize unthrottled desktop clients (`web`, `tv`), restoring full 1080p, 2K, and 4K video downloads.
3. **Format ID Bitrate Binding:**
   - Stream picker now binds directly to the highest-bitrate video stream (`$fid+bestaudio`) instead of falling back to generic height queries, ensuring the crispest possible visual quality.
4. **Enhanced Stability & Performance:**
   - Improved FFmpeg muxing stability and memory efficiency during high-resolution remuxing.

---

### ⚪ [Previous Version] Fetchreel v1.0.0 (Legacy Baseline)
- **File:** [`fetchreel-v1.0.0.apk`](fetchreel-v1.0.0.apk)
- **Version Code:** `1`
- **Version Name:** `1.0.0`
- **Architecture:** `arm64-v8a`
- **Status:** Preserved for backward compatibility, historical testing, and legacy reference.

#### Features in v1.0.0:
- Initial release featuring 100% on-device extraction via bundled Python and QuickJS.
- Snaptube-style direct overlay share sheet with instant download triggers.
- Foreground download service with WakeLock and notification controls.
- Local media management in `Download/Fetchreel`.
