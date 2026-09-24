"""
Fetchreel — a small self-hosted webapp that wraps yt-dlp behind a simple
paste-a-link UI. Works with any site yt-dlp supports (YouTube, TikTok,
Facebook, Instagram, X/Twitter, Reddit, and hundreds more) because none of
the extraction logic is site-specific — yt-dlp reads the URL itself.

Storage: by default, downloads land in a temp folder on this server and
are deleted right after being sent to the browser. Setting the S3_*
environment variables switches to uploading finished files to
S3-compatible storage instead — see storage.py.

YouTube specifically: cloud server IPs get challenged by YouTube's bot
detection far more than home connections. This file retries a failed
extraction with a few different yt-dlp "player client" profiles before
giving up, and will use cookies/a proxy if you've configured them (see
youtube_auth.py and README.md's "YouTube blocking" section) — but there's
no configuration that makes this impossible, only less likely.

Run it with:
    python app.py
then open http://localhost:5000
"""

import os
import re
import shutil
import time
import uuid
import tempfile
from pathlib import Path

from dotenv import load_dotenv
load_dotenv()

from flask import Flask, render_template, request, jsonify, send_file, after_this_request
import yt_dlp

import storage
import youtube_auth

app = Flask(__name__)

DOWNLOAD_DIR = Path(tempfile.gettempdir()) / "fetchreel_downloads"
DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)

URL_RE = re.compile(r"^https?://", re.IGNORECASE)
MIN_FREE_BYTES = int(os.environ.get("MIN_FREE_MB", "500")) * 1024 * 1024

# Player client profiles to try in order when the default request gets
# challenged. None = yt-dlp's own default behaviour, tried first.
PLAYER_CLIENT_FALLBACKS = [None, ["tv"], ["tv_embedded"], ["android"], ["ios"]]

_BOT_CHECK_MARKERS = ("sign in to confirm", "not a bot", "confirm you're not a bot")

BOT_CHECK_MESSAGE = (
    "YouTube is challenging this server as a bot right now. This happens "
    "more on cloud/server IPs than on home connections, and no single "
    "setting eliminates it — see the \"YouTube blocking\" section in "
    "README.md for cookies and proxy options that make it less frequent."
)


def is_valid_url(url: str) -> bool:
    return bool(url) and bool(URL_RE.match(url.strip()))


def has_enough_disk_space() -> bool:
    """Even in cloud-storage mode, the file is briefly written here before
    being uploaded, so this check always applies."""
    try:
        return shutil.disk_usage(DOWNLOAD_DIR).free > MIN_FREE_BYTES
    except OSError:
        return True  # fail open rather than block downloads on a check error


def cleanup_stale_files(max_age_seconds: int = 3600) -> None:
    """Best-effort sweep of leftovers from downloads that were never
    picked up (e.g. the browser tab closed mid-request)."""
    now = time.time()
    for path in DOWNLOAD_DIR.glob("*"):
        try:
            if now - path.stat().st_mtime > max_age_seconds:
                path.unlink()
        except OSError:
            pass


def _looks_like_bot_check(exc: Exception) -> bool:
    text = str(exc).lower()
    return any(marker in text for marker in _BOT_CHECK_MARKERS)


def base_ydl_opts() -> dict:
    """Options applied to every request, regardless of site: cookies and
    a proxy if configured, otherwise nothing extra."""
    opts = {}
    cookiefile = youtube_auth.cookiefile()
    if cookiefile:
        opts["cookiefile"] = cookiefile
    proxy = os.environ.get("YTDLP_PROXY")
    if proxy:
        opts["proxy"] = proxy
    return opts


def extract_with_fallback(url: str, opts: dict, download: bool):
    """Run yt-dlp's extraction, retrying with alternate player-client
    profiles if (and only if) the failure looks like a bot challenge —
    a different failure (bad URL, private video, etc.) is raised right
    away instead of being retried pointlessly."""
    last_exc = None
    for clients in PLAYER_CLIENT_FALLBACKS:
        attempt_opts = dict(opts)
        if clients:
            attempt_opts["extractor_args"] = {"youtube": {"player_client": clients}}
        try:
            with yt_dlp.YoutubeDL(attempt_opts) as ydl:
                return ydl.extract_info(url, download=download)
        except Exception as exc:
            last_exc = exc
            if not _looks_like_bot_check(exc):
                raise
            continue
    raise last_exc


@app.route("/")
def index():
    return render_template("index.html")


@app.route("/api/probe", methods=["POST"])
def probe():
    """Given a URL, ask yt-dlp what it is without downloading anything,
    and hand back a short list of quality choices for the frontend."""
    data = request.get_json(silent=True) or {}
    url = (data.get("url") or "").strip()

    if not is_valid_url(url):
        return jsonify({"error": "That doesn't look like a link. Paste a full https:// URL."}), 400

    ydl_opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "socket_timeout": 20,
        "retries": 2,
        **base_ydl_opts(),
    }

    try:
        info = extract_with_fallback(url, ydl_opts, download=False)
    except Exception as exc:
        if _looks_like_bot_check(exc):
            return jsonify({"error": BOT_CHECK_MESSAGE}), 400
        return jsonify({"error": f"Couldn't read that link ({exc})."}), 400

    if info is None:
        return jsonify({"error": "Couldn't find anything downloadable at that link."}), 400

    # Playlists: describe just the first item for now.
    if "entries" in info and info["entries"]:
        info = info["entries"][0]

    formats = info.get("formats") or []
    seen_heights = set()
    quality_options = []

    for f in formats:
        height = f.get("height")
        if not height or height in seen_heights or f.get("vcodec") == "none":
            continue
        seen_heights.add(height)
        quality_options.append({
            "label": f"{height}p",
            "value": f"bestvideo[height<={height}]+bestaudio/best[height<={height}]",
            "height": height,
        })

    quality_options.sort(key=lambda o: o["height"], reverse=True)
    for o in quality_options:
        del o["height"]

    quality_options.insert(0, {"label": "Best available", "value": "bestvideo+bestaudio/best"})
    quality_options.append({"label": "Audio only (MP3)", "value": "audio"})

    return jsonify({
        "title": info.get("title") or "Untitled",
        "thumbnail": info.get("thumbnail"),
        "uploader": info.get("uploader") or info.get("channel"),
        "duration": info.get("duration"),
        "options": quality_options,
    })


@app.route("/api/download", methods=["POST"])
def download():
    """Fetch the media, then either stream it straight to the browser
    (default) or upload it to cloud storage and hand back a signed link
    (if S3_* env vars are set) — see storage.py."""
    cleanup_stale_files()

    if not has_enough_disk_space():
        return jsonify({
            "error": "Not enough free disk space on the server right now. "
                     "Try again shortly, or set up cloud storage (see "
                     "README.md) so downloads don't depend on local disk."
        }), 507

    data = request.get_json(silent=True) or {}
    url = (data.get("url") or "").strip()
    fmt = (data.get("format") or "bestvideo+bestaudio/best").strip()

    if not is_valid_url(url):
        return jsonify({"error": "That doesn't look like a link. Paste a full https:// URL."}), 400

    job_id = uuid.uuid4().hex
    outtmpl = str(DOWNLOAD_DIR / f"{job_id}.%(ext)s")

    ydl_opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "outtmpl": outtmpl,
        "merge_output_format": "mp4",
        "concurrent_fragment_downloads": 4,  # parallelize HLS/DASH fragments
        "socket_timeout": 30,
        "retries": 3,
        **base_ydl_opts(),
    }

    if fmt == "audio":
        ydl_opts["format"] = "bestaudio/best"
        ydl_opts["postprocessors"] = [{
            "key": "FFmpegExtractAudio",
            "preferredcodec": "mp3",
            "preferredquality": "192",
        }]
    else:
        ydl_opts["format"] = fmt

    try:
        info = extract_with_fallback(url, ydl_opts, download=True)
    except Exception as exc:
        if _looks_like_bot_check(exc):
            return jsonify({"error": BOT_CHECK_MESSAGE}), 400
        return jsonify({"error": f"Download failed ({exc})."}), 500

    if "entries" in info and info["entries"]:
        info = info["entries"][0]

    # Rather than guess the final extension after merging/postprocessing,
    # look for whatever actually landed in the folder under this job id.
    matches = list(DOWNLOAD_DIR.glob(f"{job_id}.*"))
    if not matches:
        return jsonify({"error": "The download finished but the file went missing."}), 500
    filepath = str(max(matches, key=lambda p: p.stat().st_mtime))

    safe_title = re.sub(r"[^\w\-. ]", "", info.get("title") or "video")[:80].strip() or "video"
    download_name = f"{safe_title}{Path(filepath).suffix}"

    if storage.enabled():
        try:
            signed_url = storage.upload_and_get_url(filepath, download_name)
        except Exception as exc:
            return jsonify({"error": f"Cloud upload failed ({exc})."}), 500
        finally:
            try:
                os.remove(filepath)
            except OSError:
                pass
        return jsonify({"url": signed_url})

    @after_this_request
    def cleanup(response):
        try:
            os.remove(filepath)
        except OSError:
            pass
        return response

    return send_file(filepath, as_attachment=True, download_name=download_name)


if __name__ == "__main__":
    # threaded=True lets one slow download not block a second visitor.
    # For real production use, run behind gunicorn instead — see README.md.
    app.run(host="0.0.0.0", port=5000, debug=False, threaded=True)
