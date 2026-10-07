"""
Header component for Fetchreel Windows Desktop.
Matching Android AppHeader with gradient logo, engine status, and action buttons.
"""

import os
import threading
from typing import Callable
import customtkinter as ctk
from PIL import Image

from ..theme import (
    DARK_BG, DARK_SURFACE, DARK_CARD, DARK_BORDER,
    ACCENT_INDIGO, ACCENT_INDIGO_LIGHT, ACCENT_CYAN,
    TEXT_PRIMARY, TEXT_SECONDARY, TEXT_MUTED, SUCCESS_GREEN,
    FONT_TITLE, FONT_SUBTITLE, FONT_BODY, FONT_SMALL, FONT_BADGE
)


class HeaderView(ctk.CTkFrame):
    """
    Top Header Bar featuring Fetchreel branding and controls.
    """

    def __init__(
        self,
        parent,
        engine_version: str,
        has_ffmpeg: bool,
        on_update_engine: Callable[[], None],
        on_show_qr: Callable[[], None],
        on_select_folder: Callable[[], None],
    ):
        super().__init__(parent, fg_color="transparent")
        self.engine_version = engine_version
        self.has_ffmpeg = has_ffmpeg
        self.on_update_engine = on_update_engine
        self.on_show_qr = on_show_qr
        self.on_select_folder = on_select_folder

        self._build_ui()

    def _build_ui(self):
        # Left branding block
        brand_frame = ctk.CTkFrame(self, fg_color="transparent")
        brand_frame.pack(side="left", fill="y", padx=(4, 0))

        # Icon box
        icon_box = ctk.CTkFrame(
            brand_frame,
            width=46,
            height=46,
            fg_color=ACCENT_INDIGO,
            corner_radius=12
        )
        icon_box.pack(side="left", padx=(0, 12))
        icon_box.pack_propagate(False)

        # Download symbol inside box
        icon_symbol = ctk.CTkLabel(
            icon_box,
            text="⬇️",
            font=("Segoe UI", 20),
            text_color="#FFFFFF"
        )
        icon_symbol.place(relx=0.5, rely=0.5, anchor="center")

        # Title & Subtitle
        text_frame = ctk.CTkFrame(brand_frame, fg_color="transparent")
        text_frame.pack(side="left", fill="y")

        title_label = ctk.CTkLabel(
            text_frame,
            text="Fetchreel",
            font=FONT_TITLE,
            text_color=TEXT_PRIMARY,
            anchor="w"
        )
        title_label.pack(anchor="w")

        subtitle_row = ctk.CTkFrame(text_frame, fg_color="transparent")
        subtitle_row.pack(anchor="w")

        sub_label = ctk.CTkLabel(
            subtitle_row,
            text="100% On-Device • No Server",
            font=FONT_SMALL,
            text_color=ACCENT_CYAN,
            anchor="w"
        )
        sub_label.pack(side="left")

        # Right Action Buttons & Badges
        actions_frame = ctk.CTkFrame(self, fg_color="transparent")
        actions_frame.pack(side="right", fill="y", padx=(0, 4))

        # FFmpeg Badge
        ffmpeg_color = SUCCESS_GREEN if self.has_ffmpeg else "#EF4444"
        ffmpeg_text = "FFmpeg Active" if self.has_ffmpeg else "FFmpeg Missing"
        ffmpeg_badge = ctk.CTkLabel(
            actions_frame,
            text=f"● {ffmpeg_text}",
            font=FONT_BADGE,
            text_color=ffmpeg_color,
            fg_color=DARK_CARD,
            corner_radius=8,
            padx=10,
            pady=4
        )
        ffmpeg_badge.pack(side="left", padx=(0, 8))

        # Engine Badge
        engine_badge = ctk.CTkLabel(
            actions_frame,
            text=f"yt-dlp {self.engine_version[:10]}",
            font=FONT_BADGE,
            text_color=TEXT_SECONDARY,
            fg_color=DARK_CARD,
            corner_radius=8,
            padx=10,
            pady=4
        )
        engine_badge.pack(side="left", padx=(0, 10))

        # Downloads Folder Button
        self.folder_btn = ctk.CTkButton(
            actions_frame,
            text="📁 Downloads",
            font=FONT_SMALL,
            fg_color=DARK_CARD,
            hover_color=DARK_BORDER,
            text_color=TEXT_PRIMARY,
            width=90,
            height=34,
            corner_radius=10,
            command=self.on_select_folder
        )
        self.folder_btn.pack(side="left", padx=(0, 6))

        # QR Code Button (Phone App)
        self.qr_btn = ctk.CTkButton(
            actions_frame,
            text="📱 Mobile APK",
            font=FONT_SMALL,
            fg_color=DARK_CARD,
            hover_color=DARK_BORDER,
            text_color=ACCENT_CYAN,
            width=95,
            height=34,
            corner_radius=10,
            command=self.on_show_qr
        )
        self.qr_btn.pack(side="left", padx=(0, 6))

        # OTA Engine Update Button
        self.update_btn = ctk.CTkButton(
            actions_frame,
            text="🔄 Check Updates",
            font=FONT_SMALL,
            fg_color=DARK_CARD,
            hover_color=DARK_BORDER,
            text_color=TEXT_SECONDARY,
            width=110,
            height=34,
            corner_radius=10,
            command=self.on_update_engine
        )
        self.update_btn.pack(side="left")

    def set_updating_state(self, is_updating: bool, status_text: str = ""):
        """Toggles the loading state on the update button."""
        if is_updating:
            self.update_btn.configure(
                text="⏳ Updating...",
                state="disabled",
                fg_color=DARK_CARD,
                text_color=ACCENT_INDIGO_LIGHT
            )
        else:
            self.update_btn.configure(
                text="🔄 Check Updates",
                state="normal",
                fg_color=DARK_CARD,
                text_color=TEXT_SECONDARY
            )
