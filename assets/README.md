# 📦 Fetchreel Release Assets Directory

This directory organizes all official release binaries separated by platform.

---

## 📁 Directory Layout

```text
assets/
├── android/
│   ├── fetchreel-v1.0.0.apk    # ⚪ Previous baseline release (arm64-v8a)
│   ├── fetchreel-v1.1.0.apk    # 🟢 Updated release with 1080p/4K fix & 1012p fix
│   └── README.md               # Detailed Android release notes & changelog
│
└── windows/
    ├── Fetchreel.exe           # 💻 Single standalone desktop app (Windows 10/11)
    └── README.md               # Detailed Windows release notes & changelog
```

---

## 📱 Android Releases ([`assets/android/`](android/))

| Version | File | Status | Highlights & Solved Issues |
| :--- | :--- | :--- | :--- |
| **v1.1.0** | [`fetchreel-v1.1.0.apk`](android/fetchreel-v1.1.0.apk) | 🟢 **Latest** | **Fixed 1012p non-standard label bug** (standardized into 1080p, 720p, 4K tiers); **Fixed YouTube 360p low-pixel capping** via desktop client args; Direct format bitrate binding. |
| **v1.0.0** | [`fetchreel-v1.0.0.apk`](android/fetchreel-v1.0.0.apk) | ⚪ **Legacy** | Initial baseline release with on-device engine, Snaptube-style direct overlay share sheet, and foreground download service. *(Preserved for backward compatibility)* |

---

## 💻 Windows Releases ([`assets/windows/`](windows/))

| Version | File | Status | Highlights & Solved Issues |
| :--- | :--- | :--- | :--- |
| **v1.1.0** | [`Fetchreel.exe`](windows/Fetchreel.exe) | 🟢 **Latest** | **Zero duplicate analyzing**; **Fixed progress percent jitter** (smooth monotonic progress & multi-stream stage weighting); **Fixed 1012p label**; **Uncapped 1080p/4K downloads**; 1-Tap Fast Video & Fast MP3 buttons; Standalone single-file executable. |
