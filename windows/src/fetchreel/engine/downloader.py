"""
Core Downloader Engine for Fetchreel Windows.
Integrates natively with yt-dlp and FFmpeg.
"""

import os
import sys
import shutil
import re
import threading
from typing import Callable, Optional, Dict, Any, List

import yt_dlp


def get_ffmpeg_path() -> Optional[str]:
    """
    Locates FFmpeg executable in bundled PyInstaller directory,
    app folder, system PATH, or common Windows locations.
    """
    # 1. Check PyInstaller temp directory (_MEIPASS)
    if hasattr(sys, "_MEIPASS"):
        bundled = os.path.join(sys._MEIPASS, "ffmpeg.exe")
        if os.path.exists(bundled):
            return bundled

    # 2. Check next to executable or script
    app_dir = os.path.dirname(os.path.abspath(sys.executable if getattr(sys, "frozen", False) else __file__))
    local = os.path.join(app_dir, "ffmpeg.exe")
    if os.path.exists(local):
        return local

    bin_local = os.path.join(app_dir, "bin", "ffmpeg.exe")
    if os.path.exists(bin_local):
        return bin_local

    # 3. Check system PATH
    system_path = shutil.which("ffmpeg")
    if system_path:
        return system_path

    # 4. Check typical Windows WinGet install locations
    local_app_data = os.environ.get("LOCALAPPDATA", "")
    if local_app_data:
        winget_link = os.path.join(local_app_data, "Microsoft", "WinGet", "Links", "ffmpeg.exe")
        if os.path.exists(winget_link):
            return winget_link

    return None


def format_bytes(bytes_num: Optional[float]) -> str:
    """Format bytes to human-readable string (KiB, MiB, GiB)."""
    if bytes_num is None or bytes_num <= 0:
        return "0 B"
    for unit in ["B", "KB", "MB", "GB", "TB"]:
        if bytes_num < 1024.0:
            return f"{bytes_num:.1f} {unit}"
        bytes_num /= 1024.0
    return f"{bytes_num:.1f} PB"


def format_seconds(seconds: Optional[int]) -> str:
    """Format seconds into HH:MM:SS or MM:SS."""
    if seconds is None or seconds <= 0:
        return ""
    mins, secs = divmod(int(seconds), 60)
    hours, mins = divmod(mins, 60)
    if hours > 0:
        return f"{hours:02d}:{mins:02d}:{secs:02d}"
    return f"{mins:02d}:{secs:02d}"


class DownloadCancelled(Exception):
    """Raised when user cancels a running download."""
    pass


class DownloaderEngine:
    """
    Native yt-dlp downloader engine for Fetchreel.
    """

    def __init__(self):
        self.ffmpeg_path = get_ffmpeg_path()
        self.ffmpeg_dir = os.path.dirname(self.ffmpeg_path) if self.ffmpeg_path else None
        self._probe_cache: Dict[str, Dict[str, Any]] = {}
        self._cache_lock = threading.Lock()
        self._in_flight: Dict[str, threading.Event] = {}

    def get_engine_version(self) -> str:
        """Returns the current yt-dlp version."""
        return yt_dlp.version.__version__

    def has_ffmpeg(self) -> bool:
        """Returns True if FFmpeg is available."""
        return self.ffmpeg_path is not None and os.path.exists(self.ffmpeg_path)

    @staticmethod
    def _normalize_url(url: str) -> str:
        """Normalizes URL for caching (strips whitespace and tracking query params where safe)."""
        clean = url.strip()
        # Remove trailing slash
        if clean.endswith("/"):
            clean = clean[:-1]
        return clean

    def probe_url(self, url: str) -> Dict[str, Any]:
        """
        Probes media metadata and available qualities without downloading.
        Features memory caching and in-flight deduplication to prevent analyzing twice!
        """
        clean_url = self._normalize_url(url)

        # 1. Check in-memory cache
        with self._cache_lock:
            if clean_url in self._probe_cache:
                return self._probe_cache[clean_url]

            # 2. Check if already being probed in another thread
            event = self._in_flight.get(clean_url)
            if event is not None:
                # Another probe is in flight, wait for it
                is_first = False
            else:
                event = threading.Event()
                self._in_flight[clean_url] = event
                is_first = True

        if not is_first:
            # Wait up to 30 seconds for the first probe to complete
            event.wait(timeout=30)
            with self._cache_lock:
                if clean_url in self._probe_cache:
                    return self._probe_cache[clean_url]

        # 3. Perform network probe
        try:
            result = self._do_probe_url(clean_url)
            with self._cache_lock:
                self._probe_cache[clean_url] = result
            return result
        finally:
            with self._cache_lock:
                ev = self._in_flight.pop(clean_url, None)
                if ev:
                    ev.set()

    def _do_probe_url(self, url: str) -> Dict[str, Any]:
        is_youtube = any(d in url.lower() for d in ["youtube.com", "youtu.be"])
        is_instagram = "instagram.com" in url.lower()

        # Use standard yt-dlp client orchestration (provides full 1080p, 1440p, 4K streams without 403 errors)
        client_fallbacks = [None]
        last_error = None

        # Standard industry resolution tiers (handles 16:9 landscape, letterboxed 1012p, and 9:16 vertical Reels)
        tier_defs = [
            {"tier": "4K (2160p Ultra HD)", "height": 2160, "width": 3840, "match": lambda s, l: s >= 2000 or l >= 3500},
            {"tier": "2K (1440p Quad HD)",  "height": 1440, "width": 2560, "match": lambda s, l: s >= 1350 or l >= 2400},
            {"tier": "1080p (Full HD)",     "height": 1080, "width": 1920, "match": lambda s, l: s >= 900 or l >= 1700},
            {"tier": "720p (HD)",           "height": 720,  "width": 1280, "match": lambda s, l: s >= 650 or l >= 1150},
            {"tier": "480p (SD)",           "height": 480,  "width": 854,  "match": lambda s, l: s >= 420 or l >= 800},
            {"tier": "360p",                "height": 360,  "width": 640,  "match": lambda s, l: s >= 300 or l >= 550},
            {"tier": "240p",                "height": 240,  "width": 426,  "match": lambda s, l: s < 300 and l < 550},
        ]

        for client in client_fallbacks:
            ydl_opts: Dict[str, Any] = {
                "quiet": True,
                "no_warnings": True,
                "skip_download": True,
                "noplaylist": True,
                "socket_timeout": 25,
                "retries": 3,
                "nocheckcertificate": True,
                "user_agent": (
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                ),
            }

            if self.ffmpeg_dir:
                ydl_opts["ffmpeg_location"] = self.ffmpeg_dir

            if client:
                ydl_opts["extractor_args"] = {"youtube": {"player_client": [client]}}

            if is_instagram:
                ydl_opts["http_headers"] = {"Accept-Language": "en-US,en;q=0.9"}

            try:
                with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                    info = ydl.extract_info(url, download=False)
                    if not info:
                        raise ValueError("No video metadata returned from server.")

                    title = info.get("title") or "Untitled Media"
                    thumbnail = info.get("thumbnail") or ""
                    uploader = info.get("uploader") or info.get("channel") or info.get("extractor_key") or "Unknown"
                    duration = info.get("duration")
                    duration_str = format_seconds(duration)

                    # Group video streams into clean, standard industry resolution tiers
                    formats = info.get("formats") or []
                    tier_formats: Dict[str, List[Any]] = {}

                    for f in formats:
                        if f.get("vcodec") == "none":
                            continue
                        w = f.get("width") or 0
                        h = f.get("height") or 0
                        if w == 0 and h == 0:
                            continue
                        short_dim = min(w, h) if w > 0 and h > 0 else h
                        long_dim = max(w, h) if w > 0 and h > 0 else h

                        for tdef in tier_defs:
                            if tdef["match"](short_dim, long_dim):
                                tier_name = tdef["tier"]
                                tier_formats.setdefault(tier_name, []).append((tdef, f))
                                break

                    quality_options: List[Dict[str, Any]] = []

                    # Add Best Available (Highest Quality • Original) at top
                    quality_options.append({
                        "label": "Best available (Highest Quality • Original)",
                        "format_spec": "bestvideo+bestaudio/bestvideo*+bestaudio/best",
                        "height": None,
                        "is_audio": False,
                    })

                    # Add standard resolution tiers
                    for tdef in tier_defs:
                        tname = tdef["tier"]
                        if tname in tier_formats:
                            items = tier_formats[tname]
                            # Select highest quality stream for this tier
                            best_fmt = max(
                                items,
                                key=lambda item: (item[1].get("tbr") or item[1].get("vbr") or 0)
                            )[1]
                            best_fid = best_fmt.get("format_id")
                            th = tdef["height"]
                            tw = tdef["width"]

                            # Robust format spec: exact format_id + audio, or height/width bound, then bestvideo
                            fmt_spec = (
                                f"{best_fid}+bestaudio/"
                                f"bestvideo[height<={th}]+bestaudio/"
                                f"bestvideo[width<={tw}]+bestaudio/"
                                f"bestvideo+bestaudio/best"
                            )

                            quality_options.append({
                                "label": tname,
                                "format_spec": fmt_spec,
                                "height": th,
                                "is_audio": False,
                            })

                    # Add High-Quality MP3 Audio
                    quality_options.append({
                        "label": "Audio only (High Quality MP3 • 192kbps)",
                        "format_spec": "bestaudio/best",
                        "height": None,
                        "is_audio": True,
                    })

                    return {
                        "url": url,
                        "title": title,
                        "thumbnail": thumbnail,
                        "uploader": uploader,
                        "duration": duration,
                        "duration_str": duration_str,
                        "options": quality_options,
                        "_raw_info": info,  # Cache raw info for zero-reanalysis download!
                    }


            except Exception as e:
                last_error = e
                err_msg = str(e).lower()
                is_bot_check = "sign in" in err_msg or "bot" in err_msg or "confirm" in err_msg
                if not is_bot_check or client == client_fallbacks[-1]:
                    break

        raise last_error or RuntimeError("Failed to analyze media link.")

    def download_media(
        self,
        url: str,
        format_spec: str,
        is_audio: bool,
        output_dir: str,
        progress_callback: Callable[[Dict[str, Any]], None],
        cancel_event: Optional[threading.Event] = None,
        raw_info: Optional[Dict[str, Any]] = None,
    ) -> str:
        """
        Downloads media with realtime progress callbacks and FFmpeg postprocessing.
        If raw_info is provided from probe_url, reuses it with process_ie_result
        to completely eliminate analyzing twice!
        """
        os.makedirs(output_dir, exist_ok=True)
        outtmpl = os.path.join(output_dir, "%(title).180B [%(id)s].%(ext)s")

        downloaded_filepath = None
        current_stage = "video" if not is_audio else "audio"
        max_seen_percent = 0.0

        def hook(d: Dict[str, Any]):
            nonlocal downloaded_filepath, current_stage, max_seen_percent

            if cancel_event and cancel_event.is_set():
                raise DownloadCancelled("Download cancelled by user.")

            status = d.get("status")
            if status == "downloading":
                filename = d.get("filename", "")
                info_dict = d.get("info_dict") or {}
                vcodec = info_dict.get("vcodec")
                acodec = info_dict.get("acodec")

                # Accurate stream type detection:
                # Video files can be .mp4, .webm, .mkv, etc.
                # Audio-only streams are .m4a, .opus, .aac, .mp3, .ogg, .flac, .wav
                audio_exts = (".m4a", ".opus", ".aac", ".mp3", ".ogg", ".flac", ".wav")
                if is_audio:
                    current_stage = "audio"
                elif vcodec and vcodec != "none":
                    current_stage = "video"
                elif (vcodec == "none" or not vcodec) and (acodec and acodec != "none"):
                    current_stage = "audio"
                elif any(filename.lower().endswith(ext) or (ext + ".") in filename.lower() for ext in audio_exts):
                    current_stage = "audio"
                else:
                    current_stage = "video"

                # Calculate progress fraction for current stream (0.0 to 1.0)
                stream_fraction = 0.0
                frag_index = d.get("fragment_index")
                frag_count = d.get("fragment_count")

                if frag_index is not None and frag_count and frag_count > 0:
                    stream_fraction = min(1.0, max(0.0, float(frag_index) / float(frag_count)))
                else:
                    total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                    downloaded = d.get("downloaded_bytes") or 0
                    if total > 0:
                        stream_fraction = min(1.0, max(0.0, float(downloaded) / float(total)))

                # Real-time multi-stage mapping:
                # Video+Audio: Video stream: 0% -> 85%, Audio track: 85% -> 95%, FFmpeg muxing: 95% -> 99%
                # Audio-only: Audio download: 0% -> 90%, MP3 conversion: 90% -> 99%
                if is_audio:
                    calc_percent = stream_fraction * 90.0
                    status_text = "Downloading audio stream..."
                elif current_stage == "video":
                    calc_percent = stream_fraction * 85.0
                    status_text = "Downloading video stream..."
                else:
                    calc_percent = 85.0 + (stream_fraction * 10.0)
                    status_text = "Downloading audio track..."

                # STRICT MONOTONIC PROGRESS GUARANTEE:
                # Progress percent must NEVER decrease
                if calc_percent > max_seen_percent:
                    max_seen_percent = calc_percent

                speed = d.get("speed")
                eta = d.get("eta")
                speed_str = f"{format_bytes(speed)}/s" if speed else ""

                # Format clean human-readable ETA (e.g. 15s or 1m 20s)
                eta_str = ""
                if eta is not None:
                    try:
                        eta_val = int(round(float(eta)))
                        if eta_val > 0:
                            if eta_val >= 60:
                                eta_str = f"{eta_val // 60}m {eta_val % 60}s"
                            else:
                                eta_str = f"{eta_val}s"
                    except (ValueError, TypeError):
                        pass

                downloaded_bytes = d.get("downloaded_bytes") or 0
                total_bytes = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                if total_bytes == 0 and frag_index is not None and frag_count and frag_count > 0 and frag_index > 0:
                    total_bytes = int((downloaded_bytes / frag_index) * frag_count)

                downloaded_str = format_bytes(downloaded_bytes) if downloaded_bytes > 0 else "0 MB"
                total_str = format_bytes(total_bytes) if total_bytes > 0 else ""

                progress_callback({
                    "status": "downloading",
                    "percent": max_seen_percent,
                    "downloaded_bytes": downloaded_bytes,
                    "total_bytes": total_bytes,
                    "downloaded_str": downloaded_str,
                    "total_str": total_str,
                    "speed": speed_str,
                    "eta": eta_str,
                    "status_text": status_text,
                })

            elif status == "finished":
                filename = d.get("filename")
                if filename:
                    downloaded_filepath = filename

                if not is_audio and current_stage == "video":
                    current_stage = "audio"
                    max_seen_percent = max(max_seen_percent, 85.0)
                    progress_callback({
                        "status": "downloading",
                        "percent": max_seen_percent,
                        "downloaded_bytes": 0,
                        "total_bytes": 0,
                        "downloaded_str": "",
                        "total_str": "",
                        "speed": "",
                        "eta": "",
                        "status_text": "Video stream downloaded • Fetching audio track...",
                    })
                else:
                    max_seen_percent = max(max_seen_percent, 95.0)
                    progress_callback({
                        "status": "merging",
                        "percent": max_seen_percent,
                        "downloaded_bytes": 0,
                        "total_bytes": 0,
                        "downloaded_str": "",
                        "total_str": "",
                        "speed": "",
                        "eta": "",
                        "status_text": "Merging video & audio with FFmpeg..." if not is_audio else "Converting to MP3...",
                    })

        def postprocessor_hook(d: Dict[str, Any]):
            nonlocal max_seen_percent
            pp_status = d.get("status")
            pp_name = d.get("postprocessor", "")
            if pp_status == "started":
                max_seen_percent = max(max_seen_percent, 96.0)
                progress_callback({
                    "status": "merging",
                    "percent": max_seen_percent,
                    "downloaded_bytes": 0,
                    "total_bytes": 0,
                    "downloaded_str": "",
                    "total_str": "",
                    "speed": "",
                    "eta": "",
                    "status_text": f"Finalizing media with FFmpeg ({pp_name})...",
                })
            elif pp_status == "finished":
                max_seen_percent = max(max_seen_percent, 99.0)
                progress_callback({
                    "status": "merging",
                    "percent": 99.0,
                    "downloaded_bytes": 0,
                    "total_bytes": 0,
                    "downloaded_str": "",
                    "total_str": "",
                    "speed": "",
                    "eta": "",
                    "status_text": "Processing complete • Saving file...",
                })

        is_youtube = any(d in url.lower() for d in ["youtube.com", "youtu.be"])
        is_instagram = "instagram.com" in url.lower()

        ydl_opts: Dict[str, Any] = {
            "outtmpl": outtmpl,
            "progress_hooks": [hook],
            "postprocessor_hooks": [postprocessor_hook],
            "quiet": True,
            "no_warnings": True,
            "noplaylist": True,
            "retries": 5,
            "socket_timeout": 30,
            "nocheckcertificate": True,
            "geo_bypass": True,
            "user_agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            ),
        }

        if self.ffmpeg_dir:
            ydl_opts["ffmpeg_location"] = self.ffmpeg_dir

        if is_instagram:
            ydl_opts["http_headers"] = {"Accept-Language": "en-US,en;q=0.9"}

        if is_audio:
            ydl_opts["format"] = "bestaudio/best"
            ydl_opts["postprocessors"] = [{
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "192",
            }, {
                "key": "FFmpegMetadata",
                "add_metadata": True,
            }]
        else:
            ydl_opts["format"] = format_spec
            ydl_opts["merge_output_format"] = "mp4"

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            import copy
            meta = None
            if raw_info:
                # Try instant download using cached probe metadata
                try:
                    meta = ydl.process_ie_result(copy.deepcopy(raw_info), download=True)
                except Exception as e:
                    err_str = str(e).lower()
                    if "403" in err_str or "forbidden" in err_str or "expired" in err_str or "reload" in err_str:
                        # Cached URLs expired; fall back to fresh direct extraction seamlessly
                        meta = ydl.extract_info(url, download=True)
                    else:
                        raise e
            else:
                # Direct 1-tap download: analyzes and downloads in a single unified step!
                meta = ydl.extract_info(url, download=True)

            target_path = None
            if meta:
                # Determine produced file name
                if is_audio:
                    # Look for resulting .mp3
                    possible = ydl.prepare_filename(meta)
                    base, _ = os.path.splitext(possible)
                    mp3_path = base + ".mp3"
                    if os.path.exists(mp3_path):
                        target_path = mp3_path
                    elif os.path.exists(possible):
                        target_path = possible
                else:
                    possible = ydl.prepare_filename(meta)
                    base, _ = os.path.splitext(possible)
                    mp4_path = base + ".mp4"
                    if os.path.exists(mp4_path):
                        target_path = mp4_path
                    elif os.path.exists(possible):
                        target_path = possible

            if not target_path or not os.path.exists(target_path):
                target_path = downloaded_filepath

            progress_callback({
                "status": "completed",
                "percent": 100.0,
                "speed": "",
                "eta": "",
                "status_text": "Download finished successfully!",
                "filepath": target_path,
            })

            return target_path or ""
