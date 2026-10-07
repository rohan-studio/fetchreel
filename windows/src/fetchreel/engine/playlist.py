"""
Playlist and Batch Downloader Engine for Fetchreel Windows.
"""

import os
import threading
from typing import Callable, Optional, Dict, Any, List

import yt_dlp
from .downloader import DownloaderEngine, format_seconds, DownloadCancelled


class PlaylistEngine:
    """
    Engine for scanning playlists and batch downloading selected items.
    """

    def __init__(self, downloader: DownloaderEngine):
        self.downloader = downloader

    def probe_playlist(self, url: str) -> Dict[str, Any]:
        """
        Extracts playlist metadata and list of videos using --flat-playlist.
        """
        url = url.strip()
        ydl_opts: Dict[str, Any] = {
            "extract_flat": True,
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "socket_timeout": 30,
            "retries": 3,
            "nocheckcertificate": True,
            "user_agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            ),
        }

        if self.downloader.ffmpeg_dir:
            ydl_opts["ffmpeg_location"] = self.downloader.ffmpeg_dir

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            data = ydl.extract_info(url, download=False)
            if not data:
                raise ValueError("No playlist data returned from server.")

            playlist_title = data.get("title") or "Untitled Playlist"
            uploader = data.get("uploader") or data.get("channel") or data.get("uploader_id") or "Unknown"
            entries = data.get("entries") or []

            items: List[Dict[str, Any]] = []
            first_thumbnail = None

            for idx, entry in enumerate(entries):
                if not entry:
                    continue
                item_title = entry.get("title") or f"Video #{idx + 1}"
                if "[private video]" in item_title.lower() or "[deleted video]" in item_title.lower():
                    continue

                item_id = entry.get("id") or f"item_{idx}"
                item_url = entry.get("url")
                if not item_url or not str(item_url).startswith("http"):
                    item_url = f"https://www.youtube.com/watch?v={item_id}" if item_id else url

                duration = entry.get("duration")
                duration_str = format_seconds(duration)

                thumb = entry.get("thumbnail")
                if not thumb and item_id:
                    thumb = f"https://i.ytimg.com/vi/{item_id}/hqdefault.jpg"

                if not first_thumbnail and thumb:
                    first_thumbnail = thumb

                items.append({
                    "id": item_id,
                    "title": item_title,
                    "url": item_url,
                    "duration": duration,
                    "duration_str": duration_str,
                    "thumbnail": thumb,
                    "uploader": entry.get("uploader") or uploader,
                    "index": idx + 1,
                })

            if not items:
                raise ValueError("No downloadable videos found in playlist.")

            default_options = [
                {"label": "Best available (Highest Quality • Original)", "format_spec": "bestvideo+bestaudio/bestvideo*+bestaudio/best", "is_audio": False},
                {"label": "1080p (Full HD)", "format_spec": "bestvideo[height<=?1080]+bestaudio/bestvideo[width<=?1080]+bestaudio/bestvideo+bestaudio/best", "is_audio": False},
                {"label": "720p (HD)", "format_spec": "bestvideo[height<=?720]+bestaudio/bestvideo[width<=?720]+bestaudio/bestvideo+bestaudio/best", "is_audio": False},
                {"label": "480p (SD)", "format_spec": "bestvideo[height<=?480]+bestaudio/bestvideo+bestaudio/best", "is_audio": False},
                {"label": "Audio only (High Quality MP3 • 192kbps)", "format_spec": "bestaudio/best", "is_audio": True},
            ]

            return {
                "url": url,
                "title": playlist_title,
                "uploader": uploader,
                "thumbnail": first_thumbnail or "",
                "total_count": len(items),
                "items": items,
                "options": default_options,
            }

    def download_batch(
        self,
        items: List[Dict[str, Any]],
        format_spec: str,
        is_audio: bool,
        output_dir: str,
        on_batch_progress: Callable[[Dict[str, Any]], None],
        cancel_event: Optional[threading.Event] = None,
    ):
        """
        Downloads a batch of items with item and overall progress.
        """
        total = len(items)
        completed_count = 0
        failed_count = 0

        for idx, item in enumerate(items):
            if cancel_event and cancel_event.is_set():
                raise DownloadCancelled("Playlist download cancelled.")

            on_batch_progress({
                "type": "item_started",
                "current_index": idx + 1,
                "total_items": total,
                "item": item,
                "completed": completed_count,
            })

            def item_progress_cb(p: Dict[str, Any]):
                on_batch_progress({
                    "type": "item_progress",
                    "current_index": idx + 1,
                    "total_items": total,
                    "item": item,
                    "progress": p,
                })

            try:
                self.downloader.download_media(
                    url=item["url"],
                    format_spec=format_spec,
                    is_audio=is_audio,
                    output_dir=output_dir,
                    progress_callback=item_progress_cb,
                    cancel_event=cancel_event,
                )
                completed_count += 1
            except DownloadCancelled:
                raise
            except Exception as e:
                failed_count += 1
                on_batch_progress({
                    "type": "item_failed",
                    "current_index": idx + 1,
                    "total_items": total,
                    "item": item,
                    "error": str(e),
                })

        on_batch_progress({
            "type": "batch_completed",
            "completed": completed_count,
            "failed": failed_count,
            "total": total,
        })
