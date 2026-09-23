"""
Fetchreel — a small self-hosted webapp that wraps yt-dlp behind a simple
paste-a-link UI. It works with any site yt-dlp supports (YouTube, TikTok,
Facebook, Instagram, X/Twitter, Reddit, and hundreds more) because none of
the extraction logic is site-specific: yt-dlp figures out the right site
extractor from the URL itself.

Run it with:
    python app.py
then open http://localhost:5000

See README.md for setup, deployment, and legal/ethical notes.
"""

import os
import re
import uuid
import tempfile
from pathlib import Path

from flask import Flask, render_template, request, jsonify, send_file, after_this_request
import yt_dlp

app = Flask(__name__)

DOWNLOAD_DIR = Path(tempfile.gettempdir()) / "fetchreel_downloads"
DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)

URL_RE = re.compile(r"^https?://", re.IGNORECASE)


def is_valid_url(url: str) -> bool:
    return bool(url) and bool(URL_RE.match(url.strip()))


def cleanup_stale_files(max_age_seconds: int = 3600) -> None:
    """Best-effort sweep of leftover files from downloads that were never
    picked up (e.g. the browser tab was closed mid-request)."""
    import time
    now = time.time()
    for path in DOWNLOAD_DIR.glob("*"):
        try:
            if now - path.stat().st_mtime > max_age_seconds:
                path.unlink()
        except OSError:
            pass


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
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
    except Exception as exc:
        return jsonify({"error": f"Couldn't read that link ({exc})."}), 400

    if info is None:
        return jsonify({"error": "Couldn't find anything downloadable at that link."}), 400

    # Playlists: just describe the first item, so the UI still has something
    # sensible to show. Full playlist support is a natural next step.
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
    """Actually fetch the media and stream it back as a file attachment."""
    cleanup_stale_files()

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
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
    except Exception as exc:
        return jsonify({"error": f"Download failed ({exc})."}), 500

    if "entries" in info and info["entries"]:
        info = info["entries"][0]

    # Rather than guess the final extension after postprocessing/merging,
    # just look for whatever landed in the folder under this job's id.
    matches = list(DOWNLOAD_DIR.glob(f"{job_id}.*"))
    if not matches:
        return jsonify({"error": "The download finished but the file went missing."}), 500
    filepath = str(max(matches, key=lambda p: p.stat().st_mtime))

    safe_title = re.sub(r"[^\w\-. ]", "", info.get("title") or "video")[:80].strip() or "video"
    download_name = f"{safe_title}{Path(filepath).suffix}"

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
