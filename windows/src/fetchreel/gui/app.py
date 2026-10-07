"""
Main Application Window for Fetchreel Windows Desktop.
Matching Android App architecture, Material Dark UI, and native features.
"""

import os
import sys
import re
import uuid
import threading
from typing import Optional, Dict, Any, List, Tuple
import tkinter as tk
from tkinter import filedialog, messagebox
import customtkinter as ctk

from ..engine import DownloaderEngine, PlaylistEngine, StorageManager, EngineUpdater
from .theme import (
    DARK_BG, DARK_SURFACE, DARK_CARD, DARK_BORDER,
    ACCENT_INDIGO, ACCENT_INDIGO_HOVER, ACCENT_CYAN,
    TEXT_PRIMARY, TEXT_SECONDARY, TEXT_MUTED, SUCCESS_GREEN,
    FONT_TITLE, FONT_SUBTITLE, FONT_BODY, FONT_BODY_BOLD, FONT_SMALL
)
from .components import (
    HeaderView,
    UrlInputCard,
    MediaPreviewCard,
    DownloadProgressCard,
    PlaylistView,
    RecentDownloadsView,
    QrCodeDialog,
)


class FetchreelApp(ctk.CTk):
    """
    Main Desktop Application Window.
    """

    def __init__(self):
        super().__init__()

        # Setup CustomTkinter appearance
        ctk.set_appearance_mode("Dark")
        ctk.set_default_color_theme("blue")

        # Window properties
        self.title("Fetchreel — Native Media Downloader")
        self.geometry("980x780")
        self.minsize(840, 640)
        self.configure(fg_color=DARK_BG)

        # Set window icon if available
        self._set_app_icon()

        # Initialize engines
        self.downloader = DownloaderEngine()
        self.playlist_engine = PlaylistEngine(self.downloader)
        self.storage = StorageManager()

        # Multi-Download Concurrent Registry: maps job_id -> {"cancel_event": Event, "card": DownloadProgressCard, "title": str}
        self.active_jobs: Dict[str, Dict[str, Any]] = {}
        self.is_batch_downloading = False
        self.last_clipboard_text = ""

        self._build_ui()

        # Start clipboard listener if enabled
        self.after(1500, self._check_clipboard_loop)

    def _set_app_icon(self):
        base_dir = os.path.dirname(os.path.abspath(__file__))
        # Look for icon.ico in windows/assets
        ico_candidates = [
            os.path.join(base_dir, "..", "..", "assets", "icon.ico"),
            os.path.join(base_dir, "..", "assets", "icon.ico"),
            os.path.join(getattr(sys, "_MEIPASS", ""), "assets", "icon.ico"),
            os.path.join(getattr(sys, "_MEIPASS", ""), "icon.ico"),
        ]
        for p in ico_candidates:
            if os.path.exists(p):
                try:
                    self.iconbitmap(p)
                    break
                except Exception:
                    pass

    def _build_ui(self):
        # 1. Top Header
        self.header = HeaderView(
            parent=self,
            engine_version=self.downloader.get_engine_version(),
            has_ffmpeg=self.downloader.has_ffmpeg(),
            on_update_engine=self._handle_ota_update,
            on_show_qr=self._show_qr_dialog,
            on_select_folder=self._handle_select_folder,
        )
        self.header.pack(fill="x", padx=16, pady=(12, 4))

        # 2. Main Navigation Segmented Tabs
        nav_frame = ctk.CTkFrame(self, fg_color="transparent")
        nav_frame.pack(fill="x", padx=16, pady=(6, 8))

        self.tab_selector = ctk.CTkSegmentedButton(
            nav_frame,
            values=["🎬 Single Video / Reel", "📑 Playlist / Channel", "📁 Downloaded Library"],
            font=FONT_BODY_BOLD,
            fg_color=DARK_SURFACE,
            selected_color=ACCENT_INDIGO,
            selected_hover_color=ACCENT_INDIGO_HOVER,
            unselected_color=DARK_CARD,
            unselected_hover_color=DARK_BORDER,
            text_color=TEXT_PRIMARY,
            corner_radius=12,
            height=42,
            command=self._on_tab_changed
        )
        self.tab_selector.set("🎬 Single Video / Reel")
        self.tab_selector.pack(fill="x")

        # 3. Main Content Container
        self.content_container = ctk.CTkFrame(self, fg_color="transparent")
        self.content_container.pack(fill="both", expand=True)

        # Tab Views
        self._build_single_tab()
        self._build_playlist_tab()
        self._build_recent_tab()

        # Display initial tab
        self._show_single_tab()

        # 4. Bottom Watermark Footer (matching Android WatermarkFooter)
        self._build_footer()

    def _build_single_tab(self):
        self.single_frame = ctk.CTkScrollableFrame(
            self.content_container,
            fg_color="transparent"
        )

        # URL Input Card with 1-Tap Quick Actions & Formats Inspector
        self.url_card = UrlInputCard(
            parent=self.single_frame,
            on_fetch=self._start_single_probing,
            on_quick_video=self._start_quick_video_download,
            on_quick_audio=self._start_quick_audio_download,
            on_toggle_autocapture=self._toggle_autocapture,
            initial_autocapture=self.storage.get_auto_capture()
        )


        # Probed Video Preview & Quality Card
        self.media_preview = MediaPreviewCard(
            parent=self.single_frame,
            on_start_download=self._start_single_download
        )

        # Active Downloads Container (supports multiple concurrent download progress cards)
        self.downloads_container = ctk.CTkFrame(self.single_frame, fg_color="transparent")
        self.downloads_container.pack(fill="x", padx=0, pady=(6, 12))

    def _build_playlist_tab(self):
        self.playlist_frame = PlaylistView(
            parent=self.content_container,
            on_probe_playlist=self._start_playlist_probing,
            on_start_batch_download=self._start_batch_download,
            on_cancel_batch=self._cancel_download
        )

    def _build_recent_tab(self):
        self.recent_frame = RecentDownloadsView(
            parent=self.content_container,
            on_play=self.storage.play_media,
            on_open_folder=self.storage.reveal_in_explorer,
            on_delete=self._handle_delete_history_item,
            on_refresh=self._refresh_library
        )

    def _build_footer(self):
        footer = ctk.CTkFrame(self, fg_color=DARK_SURFACE, height=36, corner_radius=0)
        footer.pack(fill="x", side="bottom")

        inner_f = ctk.CTkFrame(footer, fg_color="transparent")
        inner_f.pack(fill="x", padx=16, pady=6)

        brand_lbl = ctk.CTkLabel(
            inner_f,
            text="Fetchreel v1.0.0 • Developed by Rohan Studio (Rohan Arya)",
            font=FONT_SMALL,
            text_color=TEXT_MUTED
        )
        brand_lbl.pack(side="left")

        dest_dir = self.storage.get_download_dir()
        self.dest_lbl = ctk.CTkLabel(
            inner_f,
            text=f"Save path: {dest_dir}",
            font=FONT_SMALL,
            text_color=ACCENT_CYAN
        )
        self.dest_lbl.pack(side="right")

    def _on_tab_changed(self, value: str):
        if "Single" in value:
            self._show_single_tab()
        elif "Playlist" in value:
            self._show_playlist_tab()
        elif "Library" in value:
            self._show_recent_tab()

    def _show_single_tab(self):
        self.playlist_frame.pack_forget()
        self.recent_frame.pack_forget()
        self.single_frame.pack(fill="both", expand=True)

    def _show_playlist_tab(self):
        self.single_frame.pack_forget()
        self.recent_frame.pack_forget()
        self.playlist_frame.pack(fill="both", expand=True)

    def _show_recent_tab(self):
        self.single_frame.pack_forget()
        self.playlist_frame.pack_forget()
        self.recent_frame.pack(fill="both", expand=True)
        self._refresh_library()

    # --- Engine Actions ---

    def _start_single_probing(self, url: str):
        if not url.strip():
            return
        if self.url_card.is_probing:
            return

        clean_url = self.downloader._normalize_url(url)

        # 1. Zero-delay Memory Cache Check: if already probed, show immediately with NO network request!
        with self.downloader._cache_lock:
            if clean_url in self.downloader._probe_cache:
                info = self.downloader._probe_cache[clean_url]
                self.media_preview.display_media(info)
                return

        self.url_card.set_loading(True)

        def _worker():
            try:
                info = self.downloader.probe_url(url)
                self.after(0, lambda: self._on_single_probe_success(info))
            except Exception as e:
                err = str(e)
                self.after(0, lambda: self._on_single_probe_error(err))

        threading.Thread(target=_worker, daemon=True).start()


    def _on_single_probe_success(self, info: Dict[str, Any]):
        self.url_card.set_loading(False)
        self.media_preview.display_media(info)

    def _on_single_probe_error(self, error_message: str):
        self.url_card.set_loading(False)
        messagebox.showerror("Failed to Read Media", error_message)

    def _create_download_card(self, title: str, quality_label: str) -> Tuple[str, DownloadProgressCard, threading.Event]:
        job_id = str(uuid.uuid4())[:8]
        cancel_event = threading.Event()

        card = DownloadProgressCard(
            parent=self.downloads_container,
            job_id=job_id,
            on_cancel=self._cancel_job,
            on_play=self.storage.play_media,
            on_open_folder=self.storage.reveal_in_explorer,
            on_dismiss=self._dismiss_job
        )
        card.pack(fill="x", pady=6)
        card.start_download(title, quality_label)

        self.active_jobs[job_id] = {
            "cancel_event": cancel_event,
            "card": card,
            "title": title
        }
        return job_id, card, cancel_event

    def _cancel_job(self, job_id: str):
        job = self.active_jobs.get(job_id)
        if job and job.get("cancel_event"):
            job["cancel_event"].set()

    def _dismiss_job(self, job_id: str):
        if job_id in self.active_jobs:
            del self.active_jobs[job_id]

    def _start_single_download(self, media_info: Dict[str, Any], option: Dict[str, Any]):
        url = media_info.get("url", "")
        if not url:
            return
        title = media_info.get("title", "Media")
        format_spec = option.get("format_spec", "bestvideo+bestaudio/best")
        is_audio = option.get("is_audio", False)
        quality_label = option.get("label", "Best")
        thumbnail = media_info.get("thumbnail", "")
        raw_info = media_info.get("_raw_info")

        out_dir = self.storage.get_download_dir()
        job_id, card, cancel_event = self._create_download_card(title, quality_label)

        def _worker():
            try:
                def _prog_hook(data: Dict[str, Any]):
                    self.after(0, lambda d=data, c=card: c.update_progress(d))

                # Pass pre-extracted raw_info to eliminate analyzing twice!
                filepath = self.downloader.download_media(
                    url=url,
                    format_spec=format_spec,
                    is_audio=is_audio,
                    output_dir=out_dir,
                    progress_callback=_prog_hook,
                    cancel_event=cancel_event,
                    raw_info=raw_info,
                )

                if filepath and os.path.exists(filepath):
                    self.storage.add_history(
                        title=title,
                        filepath=filepath,
                        url=url,
                        is_audio=is_audio,
                        thumbnail=thumbnail
                    )
                    self.after(0, lambda p=filepath, c=card: c.mark_completed(p))

            except Exception as e:
                err_msg = str(e)
                self.after(0, lambda m=err_msg, c=card: c.mark_error(m))

        threading.Thread(target=_worker, daemon=True).start()

    def _start_quick_video_download(self, url: str):
        if not url.strip():
            return

        out_dir = self.storage.get_download_dir()
        cached = self.downloader._probe_cache.get(self.downloader._normalize_url(url))
        raw_info = cached.get("_raw_info") if cached else None
        title = cached.get("title", "Fast Video Download") if cached else "Fast Video Download"

        job_id, card, cancel_event = self._create_download_card(title, "Best Quality")

        def _worker():
            try:
                def _prog_hook(data: Dict[str, Any]):
                    self.after(0, lambda d=data, c=card: c.update_progress(d))

                filepath = self.downloader.download_media(
                    url=url,
                    format_spec="bestvideo+bestaudio/best",
                    is_audio=False,
                    output_dir=out_dir,
                    progress_callback=_prog_hook,
                    cancel_event=cancel_event,
                    raw_info=raw_info,
                )

                if filepath and os.path.exists(filepath):
                    final_title = os.path.splitext(os.path.basename(filepath))[0]
                    self.storage.add_history(
                        title=final_title,
                        filepath=filepath,
                        url=url,
                        is_audio=False,
                        thumbnail=""
                    )
                    self.after(0, lambda p=filepath, c=card: c.mark_completed(p))

            except Exception as e:
                err_msg = str(e)
                self.after(0, lambda m=err_msg, c=card: c.mark_error(m))

        threading.Thread(target=_worker, daemon=True).start()

    def _start_quick_audio_download(self, url: str):
        if not url.strip():
            return

        out_dir = self.storage.get_download_dir()
        cached = self.downloader._probe_cache.get(self.downloader._normalize_url(url))
        raw_info = cached.get("_raw_info") if cached else None
        title = cached.get("title", "Fast MP3 Download") if cached else "Fast MP3 Download"

        job_id, card, cancel_event = self._create_download_card(title, "Audio (MP3)")

        def _worker():
            try:
                def _prog_hook(data: Dict[str, Any]):
                    self.after(0, lambda d=data, c=card: c.update_progress(d))

                filepath = self.downloader.download_media(
                    url=url,
                    format_spec="bestaudio/best",
                    is_audio=True,
                    output_dir=out_dir,
                    progress_callback=_prog_hook,
                    cancel_event=cancel_event,
                    raw_info=raw_info,
                )

                if filepath and os.path.exists(filepath):
                    final_title = os.path.splitext(os.path.basename(filepath))[0]
                    self.storage.add_history(
                        title=final_title,
                        filepath=filepath,
                        url=url,
                        is_audio=True,
                        thumbnail=""
                    )
                    self.after(0, lambda p=filepath, c=card: c.mark_completed(p))

            except Exception as e:
                err_msg = str(e)
                self.after(0, lambda m=err_msg, c=card: c.mark_error(m))

        threading.Thread(target=_worker, daemon=True).start()

    def _cancel_download(self):
        for job in list(self.active_jobs.values()):
            ev = job.get("cancel_event")
            if ev:
                ev.set()

    # --- Playlist Actions ---

    def _start_playlist_probing(self, url: str):
        self.playlist_frame.set_loading(True)

        def _worker():
            try:
                data = self.playlist_engine.probe_playlist(url)
                self.after(0, lambda: self._on_playlist_probe_success(data))
            except Exception as e:
                err = str(e)
                self.after(0, lambda: self._on_playlist_probe_error(err))

        threading.Thread(target=_worker, daemon=True).start()

    def _on_playlist_probe_success(self, data: Dict[str, Any]):
        self.playlist_frame.set_loading(False)
        self.playlist_frame.display_playlist(data)

    def _on_playlist_probe_error(self, error_message: str):
        self.playlist_frame.set_loading(False)
        messagebox.showerror("Playlist Error", error_message)

    def _start_batch_download(self, items: List[Dict[str, Any]], option: Dict[str, Any]):
        if self.is_batch_downloading:
            messagebox.showwarning("Busy", "A playlist download is currently in progress. Please wait.")
            return

        format_spec = option.get("format_spec", "bestvideo+bestaudio/best")
        is_audio = option.get("is_audio", False)
        out_dir = self.storage.get_download_dir()

        self.active_cancel_event = threading.Event()
        self.is_batch_downloading = True

        def _worker():
            try:
                def _batch_cb(data: Dict[str, Any]):
                    self.after(0, lambda: self.playlist_frame.update_batch_progress(data))
                    if data.get("type") == "item_progress":
                        p = data.get("progress", {})
                        if p.get("status") == "completed" and p.get("filepath"):
                            item = data.get("item", {})
                            self.storage.add_history(
                                title=item.get("title", ""),
                                filepath=p.get("filepath", ""),
                                url=item.get("url", ""),
                                is_audio=is_audio,
                                thumbnail=item.get("thumbnail", "")
                            )

                self.playlist_engine.download_batch(
                    items=items,
                    format_spec=format_spec,
                    is_audio=is_audio,
                    output_dir=out_dir,
                    on_batch_progress=_batch_cb,
                    cancel_event=self.active_cancel_event
                )
            except Exception as e:
                messagebox.showerror("Batch Error", str(e))
            finally:
                self.is_batch_downloading = False
                self.active_cancel_event = None

        threading.Thread(target=_worker, daemon=True).start()

    # --- Header & Dialogs ---

    def _handle_ota_update(self):
        self.header.set_updating_state(True)

        def _worker():
            ok, msg = EngineUpdater.update_engine()
            self.after(0, lambda: self._on_ota_update_complete(ok, msg))

        threading.Thread(target=_worker, daemon=True).start()

    def _on_ota_update_complete(self, ok: bool, msg: str):
        self.header.set_updating_state(False)
        if ok:
            messagebox.showinfo("yt-dlp Engine Update", msg)
        else:
            messagebox.showwarning("Update Status", msg)

    def _show_qr_dialog(self):
        QrCodeDialog(self)

    def _handle_select_folder(self):
        current = self.storage.get_download_dir()
        selected = filedialog.askdirectory(initialdir=current, title="Select Fetchreel Downloads Folder")
        if selected:
            self.storage.set_download_dir(selected)
            self.dest_lbl.configure(text=f"Save path: {selected}")
            messagebox.showinfo("Download Folder Updated", f"Downloads will be saved to:\n{selected}")

    def _toggle_autocapture(self, enabled: bool):
        self.storage.set_auto_capture(enabled)

    # --- Clipboard Auto-Capture Loop ---

    def _check_clipboard_loop(self):
        if self.storage.get_auto_capture() and not self.url_card.is_probing:
            try:
                clip = self.clipboard_get().strip()
                if clip and clip != self.last_clipboard_text:
                    url_match = re.search(r"https?://[^\s]+", clip)
                    if url_match:
                        matched_url = url_match.group(0).strip()
                        self.last_clipboard_text = clip
                        if matched_url != self.url_card.get_url():
                            self.url_card.set_url(matched_url)
                            self._start_single_probing(matched_url)
            except Exception:
                pass

        self.after(1500, self._check_clipboard_loop)


    # --- Library Management ---

    def _refresh_library(self):
        items = self.storage.get_history()
        self.recent_frame.update_items(items)

    def _handle_delete_history_item(self, filepath: str, delete_file: bool):
        self.storage.delete_history_item(filepath, delete_disk_file=delete_file)
        self._refresh_library()
