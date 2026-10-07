"""
Active Download Progress Card for Fetchreel Windows Desktop.
Matches Android DownloadProgressCard layout and realtime updates.
"""

import os
from typing import Callable, Dict, Any, Optional
import customtkinter as ctk

from ..theme import (
    DARK_BG, DARK_SURFACE, DARK_CARD, DARK_BORDER,
    ACCENT_INDIGO, ACCENT_INDIGO_LIGHT, ACCENT_CYAN,
    TEXT_PRIMARY, TEXT_SECONDARY, TEXT_MUTED,
    SUCCESS_GREEN, SUCCESS_GREEN_HOVER, ERROR_RED,
    FONT_TITLE, FONT_SUBTITLE, FONT_BODY, FONT_BODY_BOLD, FONT_SMALL, FONT_SMALL_BOLD, FONT_BADGE
)


class DownloadProgressCard(ctk.CTkFrame):
    """
    Displays active download progress bar, speed, ETA, status description,
    and post-download playback and explorer triggers.
    """

    def __init__(
        self,
        parent,
        on_cancel: Callable[[], None],
        on_play: Callable[[str], None],
        on_open_folder: Callable[[str], None],
    ):
        super().__init__(
            parent,
            fg_color=DARK_SURFACE,
            corner_radius=16,
            border_width=1,
            border_color=ACCENT_INDIGO
        )
        self.on_cancel = on_cancel
        self.on_play = on_play
        self.on_open_folder = on_open_folder

        self.completed_filepath: Optional[str] = None
        self._build_ui()
        self.pack_forget()

    def _build_ui(self):
        self.inner = ctk.CTkFrame(self, fg_color="transparent")
        self.inner.pack(fill="both", expand=True, padx=16, pady=16)

        # Header Row: Status icon + title, and Quality badge
        self.header_row = ctk.CTkFrame(self.inner, fg_color="transparent")
        self.header_row.pack(fill="x", pady=(0, 8))

        self.status_title = ctk.CTkLabel(
            self.header_row,
            text="⏳ Downloading...",
            font=FONT_SUBTITLE,
            text_color=TEXT_PRIMARY
        )
        self.status_title.pack(side="left")

        self.quality_badge = ctk.CTkLabel(
            self.header_row,
            text="1080p",
            font=FONT_BADGE,
            text_color=ACCENT_CYAN,
            fg_color=DARK_CARD,
            corner_radius=8,
            padx=10,
            pady=3
        )
        self.quality_badge.pack(side="right")

        # Media Title Label
        self.media_title = ctk.CTkLabel(
            self.inner,
            text="",
            font=FONT_BODY_BOLD,
            text_color=TEXT_PRIMARY,
            anchor="w",
            justify="left",
            wraplength=680
        )
        self.media_title.pack(anchor="w", pady=(0, 10))

        # Progress Bar
        self.progress_bar = ctk.CTkProgressBar(
            self.inner,
            height=10,
            corner_radius=5,
            progress_color=ACCENT_INDIGO,
            fg_color=DARK_CARD
        )
        self.progress_bar.set(0.0)
        self.progress_bar.pack(fill="x", pady=(0, 6))

        # Metrics Row: Speed | ETA | Percent
        self.metrics_row = ctk.CTkFrame(self.inner, fg_color="transparent")
        self.metrics_row.pack(fill="x", pady=(0, 4))

        self.speed_label = ctk.CTkLabel(
            self.metrics_row,
            text="Processing stream...",
            font=FONT_SMALL,
            text_color=TEXT_SECONDARY
        )
        self.speed_label.pack(side="left")

        self.percent_label = ctk.CTkLabel(
            self.metrics_row,
            text="0%",
            font=FONT_SMALL_BOLD,
            text_color=ACCENT_CYAN
        )
        self.percent_label.pack(side="right")

        # Status Detail Text
        self.status_text = ctk.CTkLabel(
            self.inner,
            text="Connecting to media source...",
            font=FONT_SMALL,
            text_color=ACCENT_INDIGO_LIGHT,
            anchor="w"
        )
        self.status_text.pack(anchor="w", pady=(0, 12))

        # Actions Row (Cancel while active / Play & Folder when completed)
        self.actions_row = ctk.CTkFrame(self.inner, fg_color="transparent")
        self.actions_row.pack(fill="x")

        self.cancel_btn = ctk.CTkButton(
            self.actions_row,
            text="✕ Cancel Download",
            font=FONT_SMALL,
            fg_color=DARK_CARD,
            hover_color=ERROR_RED,
            text_color=TEXT_PRIMARY,
            height=34,
            corner_radius=8,
            command=self.on_cancel
        )
        self.cancel_btn.pack(side="left")

        self.play_btn = ctk.CTkButton(
            self.actions_row,
            text="▶ Play Media",
            font=FONT_SMALL_BOLD,
            fg_color=SUCCESS_GREEN,
            hover_color=SUCCESS_GREEN_HOVER,
            text_color="#FFFFFF",
            height=34,
            corner_radius=8,
            command=self._handle_play
        )

        self.folder_btn = ctk.CTkButton(
            self.actions_row,
            text="📁 Show in Folder",
            font=FONT_SMALL,
            fg_color=DARK_CARD,
            hover_color=DARK_BORDER,
            text_color=TEXT_PRIMARY,
            height=34,
            corner_radius=8,
            command=self._handle_folder
        )

    def start_download(self, title: str, quality_label: str):
        self.completed_filepath = None
        self.media_title.configure(text=title)
        self.quality_badge.configure(text=quality_label)
        self.status_title.configure(text="⏳ Downloading...", text_color=TEXT_PRIMARY)
        self.progress_bar.configure(progress_color=ACCENT_INDIGO)
        self.progress_bar.set(0.0)
        self.speed_label.configure(text="Initializing download...")
        self.percent_label.configure(text="0%")
        self.status_text.configure(text="Starting engine and extracting stream...", text_color=ACCENT_INDIGO_LIGHT)

        self.play_btn.pack_forget()
        self.folder_btn.pack_forget()
        self.cancel_btn.pack(side="left")

        self.pack(fill="x", padx=16, pady=10)

    def update_progress(self, data: Dict[str, Any]):
        percent = data.get("percent", 0.0)
        speed = data.get("speed", "")
        eta = data.get("eta", "")
        status = data.get("status_text", "Downloading...")

        self.progress_bar.set(min(1.0, max(0.0, percent / 100.0)))
        percent_str = f"{int(percent)}%"
        if eta:
            percent_str += f" (ETA: {eta})"
        self.percent_label.configure(text=percent_str)

        speed_text = speed if speed else "Processing stream..."
        self.speed_label.configure(text=speed_text)
        self.status_text.configure(text=status)

    def mark_completed(self, filepath: str):
        self.completed_filepath = filepath
        self.progress_bar.set(1.0)
        self.progress_bar.configure(progress_color=SUCCESS_GREEN)
        self.status_title.configure(text="✅ Download Complete!", text_color=SUCCESS_GREEN)
        self.percent_label.configure(text="100%")
        self.speed_label.configure(text="Saved to Fetchreel Downloads")
        self.status_text.configure(text=f"File: {os.path.basename(filepath)}", text_color=TEXT_SECONDARY)

        self.cancel_btn.pack_forget()
        self.play_btn.pack(side="left", padx=(0, 8))
        self.folder_btn.pack(side="left")

    def mark_error(self, error_message: str):
        self.status_title.configure(text="⚠️ Download Failed", text_color=ERROR_RED)
        self.progress_bar.configure(progress_color=ERROR_RED)
        self.status_text.configure(text=error_message, text_color=ERROR_RED)
        self.cancel_btn.configure(text="✕ Dismiss", hover_color=DARK_BORDER)

    def _handle_play(self):
        if self.completed_filepath:
            self.on_play(self.completed_filepath)

    def _handle_folder(self):
        if self.completed_filepath:
            self.on_open_folder(self.completed_filepath)
