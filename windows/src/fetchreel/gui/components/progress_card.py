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
        job_id: str = "",
        on_cancel: Optional[Callable[[str], None]] = None = None,
        on_play: Optional[Callable[[str], None]] = None = None,
        on_open_folder: Optional[Callable[[str], None]] = None = None,
        on_dismiss: Optional[Callable[[str], None]] = None = None,
    ):
        super().__init__(
            parent,
            fg_color=DARK_SURFACE,
            corner_radius=16,
            border_width=1,
            border_color=ACCENT_INDIGO
        )
        self.job_id = job_id
        self.on_cancel = on_cancel
        self.on_play = on_play
        self.on_open_folder = on_open_folder
        self.on_dismiss = on_dismiss

        self.completed_filepath: Optional[str] = None
        self._max_percent = 0.0
        self._build_ui()

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
            command=self._handle_cancel
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

        self.dismiss_btn = ctk.CTkButton(
            self.actions_row,
            text="✕ Dismiss",
            font=FONT_SMALL,
            fg_color=DARK_CARD,
            hover_color=DARK_BORDER,
            text_color=TEXT_MUTED,
            height=34,
            corner_radius=8,
            command=self._handle_dismiss
        )

    def start_download(self, title: str, quality_label: str):
        self.completed_filepath = None
        self._max_percent = 0.0
        self.media_title.configure(text=title)
        self.quality_badge.configure(text=quality_label)
        self.status_title.configure(text="⏳ Downloading...", text_color=TEXT_PRIMARY)
        self.progress_bar.configure(progress_color=ACCENT_INDIGO)
        self.progress_bar.set(0.0)
        self.speed_label.configure(text="Connecting to media source...")
        self.percent_label.configure(text="0%")
        self.status_text.configure(text="Starting engine and extracting stream...", text_color=ACCENT_INDIGO_LIGHT)

        self.play_btn.pack_forget()
        self.folder_btn.pack_forget()
        self.dismiss_btn.pack_forget()
        self.cancel_btn.pack(side="left")

        self.pack(fill="x", padx=16, pady=8)

    def update_progress(self, data: Dict[str, Any]):
        raw_percent = data.get("percent", 0.0)
        # Ensure progress strictly monotonically increases and never decreases
        self._max_percent = max(getattr(self, "_max_percent", 0.0), raw_percent)
        percent = min(99.0, self._max_percent)

        downloaded_str = data.get("downloaded_str", "")
        total_str = data.get("total_str", "")
        speed = data.get("speed", "")
        eta = data.get("eta", "")
        status = data.get("status_text", "Downloading...")

        self.progress_bar.set(min(1.0, max(0.0, percent / 100.0)))

        # Format metrics: Downloaded / Total • Speed
        size_parts = []
        if downloaded_str:
            if total_str:
                size_parts.append(f"{downloaded_str} / {total_str}")
            else:
                size_parts.append(downloaded_str)
        if speed:
            size_parts.append(speed)

        metrics_display = "  •  ".join(size_parts) if size_parts else "Processing stream..."
        self.speed_label.configure(text=metrics_display)

        percent_str = f"{int(percent)}%"
        if eta:
            percent_str += f" (ETA: {eta})"
        self.percent_label.configure(text=percent_str)
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
        self.folder_btn.pack(side="left", padx=(0, 8))
        self.dismiss_btn.pack(side="left")

    def mark_error(self, error_message: str):
        self.status_title.configure(text="⚠️ Download Failed", text_color=ERROR_RED)
        self.progress_bar.configure(progress_color=ERROR_RED)
        self.status_text.configure(text=error_message, text_color=ERROR_RED)
        self.cancel_btn.pack_forget()
        self.dismiss_btn.pack(side="left")

    def _handle_cancel(self):
        if self.on_cancel:
            self.on_cancel(self.job_id)

    def _handle_dismiss(self):
        if self.on_dismiss:
            self.on_dismiss(self.job_id)
        self.destroy()

    def _handle_play(self):
        if self.completed_filepath and self.on_play:
            self.on_play(self.completed_filepath)

    def _handle_folder(self):
        if self.completed_filepath and self.on_open_folder:
            self.on_open_folder(self.completed_filepath)
