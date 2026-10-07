"""
URL Input Card Component for Fetchreel Windows Desktop.
Matches Android UrlInputField styling and behavior with 1-Tap Quick Downloads.
"""

from typing import Callable, Optional
import customtkinter as ctk

from ..theme import (
    DARK_BG, DARK_SURFACE, DARK_CARD, DARK_CARD_HOVER, DARK_BORDER,
    ACCENT_INDIGO, ACCENT_INDIGO_HOVER, ACCENT_INDIGO_LIGHT,
    ACCENT_CYAN, ACCENT_CYAN_HOVER,
    TEXT_PRIMARY, TEXT_SECONDARY, TEXT_MUTED,
    FONT_BODY, FONT_BODY_BOLD, FONT_SMALL
)


class UrlInputCard(ctk.CTkFrame):
    """
    Card providing URL input, clipboard paste, clear, 1-tap fast download triggers,
    and inspect formats analyzer.
    """

    def __init__(
        self,
        parent,
        on_fetch: Callable[[str], None],
        on_quick_video: Callable[[str], None],
        on_quick_audio: Callable[[str], None],
        on_toggle_autocapture: Callable[[bool], None],
        initial_autocapture: bool = True,
    ):
        super().__init__(
            parent,
            fg_color=DARK_SURFACE,
            corner_radius=16,
            border_width=1,
            border_color=DARK_BORDER
        )
        self.on_fetch = on_fetch
        self.on_quick_video = on_quick_video
        self.on_quick_audio = on_quick_audio
        self.on_toggle_autocapture = on_toggle_autocapture
        self.is_probing = False

        self._build_ui(initial_autocapture)

    def _build_ui(self, initial_autocapture: bool):
        self.pack_configure(fill="x", padx=16, pady=10)

        inner = ctk.CTkFrame(self, fg_color="transparent")
        inner.pack(fill="x", padx=16, pady=16)

        # Input Row
        input_row = ctk.CTkFrame(
            inner,
            fg_color=DARK_CARD,
            corner_radius=12,
            border_width=1,
            border_color=DARK_BORDER
        )
        input_row.pack(fill="x", pady=(0, 12))

        # Icon prefix
        icon_label = ctk.CTkLabel(
            input_row,
            text="🔗",
            font=("Segoe UI", 14),
            text_color=ACCENT_INDIGO_LIGHT,
            width=36
        )
        icon_label.pack(side="left", padx=(10, 0))

        # Entry
        self.entry = ctk.CTkEntry(
            input_row,
            placeholder_text="Paste link (YouTube, TikTok, Instagram, X/Twitter, etc.)...",
            font=FONT_BODY,
            fg_color="transparent",
            text_color=TEXT_PRIMARY,
            placeholder_text_color=TEXT_MUTED,
            border_width=0,
            height=46
        )
        self.entry.pack(side="left", fill="x", expand=True, padx=(4, 8))
        self.entry.bind("<Return>", lambda e: self._handle_fetch())

        # Paste Button
        self.paste_btn = ctk.CTkButton(
            input_row,
            text="📋 Paste",
            font=FONT_SMALL,
            fg_color="transparent",
            hover_color=DARK_CARD_HOVER,
            text_color=ACCENT_INDIGO_LIGHT,
            width=70,
            height=32,
            corner_radius=8,
            command=self._handle_paste
        )
        self.paste_btn.pack(side="right", padx=(0, 6))

        # Clear Button
        self.clear_btn = ctk.CTkButton(
            input_row,
            text="✕ Clear",
            font=FONT_SMALL,
            fg_color="transparent",
            hover_color=DARK_CARD_HOVER,
            text_color=TEXT_MUTED,
            width=65,
            height=32,
            corner_radius=8,
            command=self.clear_input
        )
        self.clear_btn.pack(side="right", padx=(0, 2))

        # Action Row 1: 1-Tap Instant Direct Download Buttons (Zero Wait Time!)
        quick_row = ctk.CTkFrame(inner, fg_color="transparent")
        quick_row.pack(fill="x", pady=(0, 10))

        self.fast_video_btn = ctk.CTkButton(
            quick_row,
            text="⚡ Fast Video (1-Tap Best)",
            font=FONT_BODY_BOLD,
            fg_color=ACCENT_INDIGO,
            hover_color=ACCENT_INDIGO_HOVER,
            text_color=TEXT_PRIMARY,
            height=42,
            corner_radius=10,
            command=self._handle_fast_video
        )
        self.fast_video_btn.pack(side="left", fill="x", expand=True, padx=(0, 6))

        self.fast_audio_btn = ctk.CTkButton(
            quick_row,
            text="🎵 Fast MP3 (1-Tap Audio)",
            font=FONT_BODY_BOLD,
            fg_color=ACCENT_CYAN,
            hover_color=ACCENT_CYAN_HOVER,
            text_color="#FFFFFF",
            height=42,
            corner_radius=10,
            command=self._handle_fast_audio
        )
        self.fast_audio_btn.pack(side="right", fill="x", expand=True, padx=(6, 0))

        # Action Row 2: Inspect Formats & Auto-capture switch
        action_row = ctk.CTkFrame(inner, fg_color="transparent")
        action_row.pack(fill="x")

        self.fetch_btn = ctk.CTkButton(
            action_row,
            text="🔍 Inspect Formats (4K, 1080p, 720p...)",
            font=FONT_SMALL,
            fg_color=DARK_CARD,
            hover_color=DARK_CARD_HOVER,
            border_width=1,
            border_color=DARK_BORDER,
            text_color=TEXT_PRIMARY,
            height=36,
            corner_radius=10,
            command=self._handle_fetch
        )
        self.fetch_btn.pack(side="left", fill="x", expand=True, padx=(0, 16))

        self.autocapture_switch = ctk.CTkSwitch(
            action_row,
            text="Auto-capture clipboard",
            font=FONT_SMALL,
            text_color=TEXT_SECONDARY,
            progress_color=ACCENT_INDIGO,
            command=self._handle_autocapture_toggle
        )
        if initial_autocapture:
            self.autocapture_switch.select()
        else:
            self.autocapture_switch.deselect()
        self.autocapture_switch.pack(side="right")

    def _handle_paste(self):
        try:
            clipboard = self.clipboard_get().strip()
            if clipboard:
                self.entry.delete(0, "end")
                self.entry.insert(0, clipboard)
        except Exception:
            pass

    def _handle_fast_video(self):
        url = self.entry.get().strip()
        if url:
            self.on_quick_video(url)

    def _handle_fast_audio(self):
        url = self.entry.get().strip()
        if url:
            self.on_quick_audio(url)

    def _handle_fetch(self):
        url = self.entry.get().strip()
        if url and not self.is_probing:
            self.on_fetch(url)

    def _handle_autocapture_toggle(self):
        is_on = bool(self.autocapture_switch.get())
        self.on_toggle_autocapture(is_on)

    def set_url(self, url: str):
        self.entry.delete(0, "end")
        self.entry.insert(0, url.strip())

    def clear_input(self):
        self.entry.delete(0, "end")

    def get_url(self) -> str:
        return self.entry.get().strip()

    def set_loading(self, loading: bool):
        self.is_probing = loading
        if loading:
            self.fetch_btn.configure(
                text="⏳ Analyzing Formats...",
                state="disabled",
                fg_color=DARK_CARD,
                text_color=TEXT_SECONDARY
            )
            self.fast_video_btn.configure(state="disabled")
            self.fast_audio_btn.configure(state="disabled")
        else:
            self.fetch_btn.configure(
                text="🔍 Inspect Formats (4K, 1080p, 720p...)",
                state="normal",
                fg_color=DARK_CARD,
                text_color=TEXT_PRIMARY
            )
            self.fast_video_btn.configure(state="normal")
            self.fast_audio_btn.configure(state="normal")
