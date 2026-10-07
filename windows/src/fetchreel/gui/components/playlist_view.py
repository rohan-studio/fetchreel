"""
Playlist and Batch Downloader View for Fetchreel Windows Desktop.
Matches Android PlaylistCard and PlaylistProgressCard.
"""

import threading
from typing import Callable, Dict, Any, List, Set, Optional
import customtkinter as ctk

from ..theme import (
    DARK_BG, DARK_SURFACE, DARK_CARD, DARK_CARD_HOVER, DARK_BORDER,
    ACCENT_INDIGO, ACCENT_INDIGO_HOVER, ACCENT_INDIGO_LIGHT, ACCENT_CYAN,
    TEXT_PRIMARY, TEXT_SECONDARY, TEXT_MUTED,
    SUCCESS_GREEN, SUCCESS_GREEN_HOVER, ERROR_RED,
    FONT_TITLE, FONT_SUBTITLE, FONT_BODY, FONT_BODY_BOLD, FONT_SMALL, FONT_SMALL_BOLD, FONT_BADGE
)


class PlaylistView(ctk.CTkFrame):
    """
    Complete tab view for probing and batch downloading entire playlists or channels.
    """

    def __init__(
        self,
        parent,
        on_probe_playlist: Callable[[str], None],
        on_start_batch_download: Callable[[List[Dict[str, Any]], Dict[str, Any]], None],
        on_cancel_batch: Callable[[], None],
    ):
        super().__init__(parent, fg_color="transparent")
        self.on_probe_playlist = on_probe_playlist
        self.on_start_batch_download = on_start_batch_download
        self.on_cancel_batch = on_cancel_batch

        self.current_playlist_info: Optional[Dict[str, Any]] = None
        self.item_checkboxes: Dict[str, ctk.CTkCheckBox] = {}
        self.selected_item_ids: Set[str] = set()
        self.selected_option: Dict[str, Any] = {}

        self._build_ui()

    def _build_ui(self):
        # Top Input Card
        self.input_card = ctk.CTkFrame(
            self,
            fg_color=DARK_SURFACE,
            corner_radius=16,
            border_width=1,
            border_color=DARK_BORDER
        )
        self.input_card.pack(fill="x", padx=16, pady=(10, 8))

        inner_input = ctk.CTkFrame(self.input_card, fg_color="transparent")
        inner_input.pack(fill="x", padx=16, pady=16)

        # URL row
        url_row = ctk.CTkFrame(
            inner_input,
            fg_color=DARK_CARD,
            corner_radius=12,
            border_width=1,
            border_color=DARK_BORDER
        )
        url_row.pack(fill="x", pady=(0, 12))

        icon_label = ctk.CTkLabel(
            url_row,
            text="📑",
            font=("Segoe UI", 14),
            text_color=ACCENT_CYAN,
            width=36
        )
        icon_label.pack(side="left", padx=(10, 0))

        self.url_entry = ctk.CTkEntry(
            url_row,
            placeholder_text="Paste playlist link (YouTube playlist, album, etc.)...",
            font=FONT_BODY,
            fg_color="transparent",
            text_color=TEXT_PRIMARY,
            placeholder_text_color=TEXT_MUTED,
            border_width=0,
            height=44
        )
        self.url_entry.pack(side="left", fill="x", expand=True, padx=(4, 8))
        self.url_entry.bind("<Return>", lambda e: self._handle_fetch())

        paste_btn = ctk.CTkButton(
            url_row,
            text="📋 Paste",
            font=FONT_SMALL,
            fg_color="transparent",
            hover_color=DARK_CARD_HOVER,
            text_color=ACCENT_CYAN,
            width=70,
            height=32,
            corner_radius=8,
            command=self._handle_paste
        )
        paste_btn.pack(side="right", padx=(0, 6))

        # Fetch button
        self.fetch_btn = ctk.CTkButton(
            inner_input,
            text="🔍 Fetch Playlist Videos",
            font=FONT_BODY_BOLD,
            fg_color=ACCENT_INDIGO,
            hover_color=ACCENT_INDIGO_HOVER,
            text_color=TEXT_PRIMARY,
            height=44,
            corner_radius=12,
            command=self._handle_fetch
        )
        self.fetch_btn.pack(fill="x")

        # Overview and Selection Card (Hidden until playlist probed)
        self.overview_card = ctk.CTkFrame(
            self,
            fg_color=DARK_SURFACE,
            corner_radius=16,
            border_width=1,
            border_color=DARK_BORDER
        )

        self.playlist_title_label = ctk.CTkLabel(
            self.overview_card,
            text="",
            font=FONT_SUBTITLE,
            text_color=TEXT_PRIMARY,
            anchor="w"
        )

        self.playlist_author_label = ctk.CTkLabel(
            self.overview_card,
            text="",
            font=FONT_SMALL,
            text_color=ACCENT_CYAN,
            anchor="w"
        )

        # Batch Controls (Select all, deselect all, format dropdown)
        self.controls_row = ctk.CTkFrame(self.overview_card, fg_color="transparent")

        self.select_all_btn = ctk.CTkButton(
            self.controls_row,
            text="Select All",
            font=FONT_SMALL,
            fg_color=DARK_CARD,
            hover_color=DARK_BORDER,
            text_color=TEXT_PRIMARY,
            height=32,
            width=80,
            corner_radius=8,
            command=self._select_all
        )

        self.deselect_all_btn = ctk.CTkButton(
            self.controls_row,
            text="Deselect All",
            font=FONT_SMALL,
            fg_color=DARK_CARD,
            hover_color=DARK_BORDER,
            text_color=TEXT_SECONDARY,
            height=32,
            width=90,
            corner_radius=8,
            command=self._deselect_all
        )

        self.count_badge = ctk.CTkLabel(
            self.controls_row,
            text="0 selected",
            font=FONT_BADGE,
            text_color=ACCENT_CYAN,
            fg_color=DARK_CARD,
            corner_radius=8,
            padx=10,
            pady=3
        )

        self.format_menu = ctk.CTkOptionMenu(
            self.controls_row,
            values=["Best available", "1080p FHD", "720p HD", "Audio only (MP3)"],
            font=FONT_SMALL,
            dropdown_font=FONT_SMALL,
            fg_color=DARK_CARD,
            button_color=ACCENT_INDIGO,
            text_color=TEXT_PRIMARY,
            dropdown_fg_color=DARK_CARD,
            dropdown_text_color=TEXT_PRIMARY,
            corner_radius=8,
            height=32,
            command=self._on_format_changed
        )

        # Batch Download Button
        self.batch_download_btn = ctk.CTkButton(
            self.overview_card,
            text="⬇ Download Selected Videos",
            font=FONT_BODY_BOLD,
            fg_color=SUCCESS_GREEN,
            hover_color=SUCCESS_GREEN_HOVER,
            text_color="#FFFFFF",
            height=44,
            corner_radius=12,
            command=self._handle_batch_download
        )

        # Scrollable items container
        self.items_scroll = ctk.CTkScrollableFrame(
            self,
            fg_color=DARK_SURFACE,
            corner_radius=16,
            border_width=1,
            border_color=DARK_BORDER,
            height=280
        )

        # Batch Progress Card
        self.batch_progress_card = ctk.CTkFrame(
            self,
            fg_color=DARK_SURFACE,
            corner_radius=16,
            border_width=1,
            border_color=ACCENT_INDIGO
        )

        self.batch_status_title = ctk.CTkLabel(
            self.batch_progress_card,
            text="⏳ Batch Downloading...",
            font=FONT_SUBTITLE,
            text_color=TEXT_PRIMARY
        )

        self.batch_item_label = ctk.CTkLabel(
            self.batch_progress_card,
            text="",
            font=FONT_BODY,
            text_color=ACCENT_INDIGO_LIGHT,
            anchor="w"
        )

        self.overall_progress_bar = ctk.CTkProgressBar(
            self.batch_progress_card,
            height=10,
            corner_radius=5,
            progress_color=ACCENT_INDIGO,
            fg_color=DARK_CARD
        )

        self.batch_details_label = ctk.CTkLabel(
            self.batch_progress_card,
            text="",
            font=FONT_SMALL,
            text_color=TEXT_SECONDARY
        )

        self.batch_cancel_btn = ctk.CTkButton(
            self.batch_progress_card,
            text="✕ Cancel Batch",
            font=FONT_SMALL,
            fg_color=DARK_CARD,
            hover_color=ERROR_RED,
            text_color=TEXT_PRIMARY,
            height=32,
            corner_radius=8,
            command=self.on_cancel_batch
        )

    def _handle_paste(self):
        try:
            cb = self.clipboard_get().strip()
            if cb:
                self.url_entry.delete(0, "end")
                self.url_entry.insert(0, cb)
        except Exception:
            pass

    def _handle_fetch(self):
        url = self.url_entry.get().strip()
        if url:
            self.on_probe_playlist(url)

    def set_loading(self, loading: bool):
        if loading:
            self.fetch_btn.configure(
                text="⏳ Scanning Playlist Entries...",
                state="disabled",
                fg_color=DARK_CARD,
                text_color=TEXT_SECONDARY
            )
        else:
            self.fetch_btn.configure(
                text="🔍 Fetch Playlist Videos",
                state="normal",
                fg_color=ACCENT_INDIGO,
                text_color=TEXT_PRIMARY
            )

    def display_playlist(self, pl_data: Dict[str, Any]):
        self.current_playlist_info = pl_data
        title = pl_data.get("title", "Untitled Playlist")
        uploader = pl_data.get("uploader", "Unknown Creator")
        items: List[Dict[str, Any]] = pl_data.get("items", [])
        options: List[Dict[str, Any]] = pl_data.get("options", [])

        # Configure overview card
        self.overview_card.pack(fill="x", padx=16, pady=6)
        inner_ov = ctk.CTkFrame(self.overview_card, fg_color="transparent")
        inner_ov.pack(fill="x", padx=16, pady=14)

        self.playlist_title_label.configure(text=f"📑 {title} ({len(items)} videos)")
        self.playlist_title_label.pack(in_=inner_ov, anchor="w", pady=(0, 2))

        self.playlist_author_label.configure(text=f"By: {uploader}")
        self.playlist_author_label.pack(in_=inner_ov, anchor="w", pady=(0, 10))

        # Pack controls
        self.controls_row.pack(in_=inner_ov, fill="x", pady=(0, 10))
        self.select_all_btn.pack(side="left", padx=(0, 6))
        self.deselect_all_btn.pack(side="left", padx=(0, 10))
        self.count_badge.pack(side="left", padx=(0, 10))

        # Format options
        labels = [opt["label"] for opt in options] if options else ["Best available"]
        self.format_menu.configure(values=labels)
        if labels:
            self.format_menu.set(labels[0])
            self.selected_option = options[0] if options else {}

        self.format_menu.pack(side="right")
        self.batch_download_btn.pack(in_=inner_ov, fill="x")

        # Build items list
        self.items_scroll.pack(fill="both", expand=True, padx=16, pady=6)
        for w in self.items_scroll.winfo_children():
            w.destroy()

        self.item_checkboxes.clear()
        self.selected_item_ids = {it["id"] for it in items}

        for item in items:
            item_id = item["id"]
            row = ctk.CTkFrame(self.items_scroll, fg_color=DARK_CARD, corner_radius=10)
            row.pack(fill="x", pady=3, padx=4)

            cb = ctk.CTkCheckBox(
                row,
                text=f"#{item.get('index', 0)}  {item.get('title', '')[:75]}",
                font=FONT_SMALL,
                text_color=TEXT_PRIMARY,
                checkmark_color="#FFFFFF",
                fg_color=ACCENT_INDIGO,
                hover_color=ACCENT_INDIGO_HOVER,
                border_color=DARK_BORDER,
                command=lambda i=item_id: self._toggle_item(i)
            )
            cb.select()
            cb.pack(side="left", fill="x", expand=True, padx=10, pady=8)
            self.item_checkboxes[item_id] = cb

            dur_str = item.get("duration_str", "")
            if dur_str:
                dur_label = ctk.CTkLabel(
                    row,
                    text=dur_str,
                    font=FONT_BADGE,
                    text_color=TEXT_MUTED
                )
                dur_label.pack(side="right", padx=10)

        self._update_selection_count()

    def _toggle_item(self, item_id: str):
        cb = self.item_checkboxes.get(item_id)
        if cb:
            if cb.get():
                self.selected_item_ids.add(item_id)
            else:
                self.selected_item_ids.discard(item_id)
        self._update_selection_count()

    def _select_all(self):
        if not self.current_playlist_info:
            return
        items = self.current_playlist_info.get("items", [])
        for item in items:
            item_id = item["id"]
            self.selected_item_ids.add(item_id)
            cb = self.item_checkboxes.get(item_id)
            if cb:
                cb.select()
        self._update_selection_count()

    def _deselect_all(self):
        self.selected_item_ids.clear()
        for cb in self.item_checkboxes.values():
            cb.deselect()
        self._update_selection_count()

    def _update_selection_count(self):
        total = len(self.current_playlist_info.get("items", [])) if self.current_playlist_info else 0
        sel = len(self.selected_item_ids)
        self.count_badge.configure(text=f"{sel}/{total} selected")
        self.batch_download_btn.configure(text=f"⬇ Download Selected ({sel} Videos)")

    def _on_format_changed(self, label: str):
        if not self.current_playlist_info:
            return
        options = self.current_playlist_info.get("options", [])
        for opt in options:
            if opt["label"] == label:
                self.selected_option = opt
                break

    def _handle_batch_download(self):
        if not self.current_playlist_info or not self.selected_item_ids:
            return
        all_items = self.current_playlist_info.get("items", [])
        chosen = [it for it in all_items if it["id"] in self.selected_item_ids]
        if chosen:
            self._show_batch_progress(len(chosen))
            self.on_start_batch_download(chosen, self.selected_option)

    def _show_batch_progress(self, total: int):
        self.batch_progress_card.pack(fill="x", padx=16, pady=10)
        inner = ctk.CTkFrame(self.batch_progress_card, fg_color="transparent")
        inner.pack(fill="x", padx=16, pady=16)

        self.batch_status_title.pack(in_=inner, anchor="w", pady=(0, 6))
        self.batch_item_label.pack(in_=inner, anchor="w", pady=(0, 8))
        self.overall_progress_bar.pack(in_=inner, fill="x", pady=(0, 6))
        self.overall_progress_bar.set(0.0)
        self.batch_details_label.pack(in_=inner, anchor="w", pady=(0, 10))
        self.batch_cancel_btn.pack(in_=inner, side="left")

    def update_batch_progress(self, data: Dict[str, Any]):
        msg_type = data.get("type")
        cur_idx = data.get("current_index", 0)
        total = data.get("total_items", 1)
        item = data.get("item", {})

        if msg_type == "item_started":
            self.batch_item_label.configure(
                text=f"Downloading [{cur_idx}/{total}]: {item.get('title', '')[:60]}"
            )
            self.overall_progress_bar.set(max(0.0, (cur_idx - 1) / total))

        elif msg_type == "item_progress":
            p = data.get("progress", {})
            percent = p.get("percent", 0.0)
            speed = p.get("speed", "")
            eta = p.get("eta", "")
            self.batch_details_label.configure(
                text=f"Item progress: {int(percent)}%  •  Speed: {speed}  •  ETA: {eta}"
            )

        elif msg_type == "batch_completed":
            comp = data.get("completed", 0)
            self.overall_progress_bar.set(1.0)
            self.overall_progress_bar.configure(progress_color=SUCCESS_GREEN)
            self.batch_status_title.configure(text="✅ Playlist Download Finished!", text_color=SUCCESS_GREEN)
            self.batch_details_label.configure(text=f"Successfully downloaded {comp} of {total} items.")
            self.batch_cancel_btn.configure(text="Dismiss")
