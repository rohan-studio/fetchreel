"""
Media Preview and Quality Selection Card for Fetchreel Windows Desktop.
Matches Android VideoPreviewCard and QualitySelector.
"""

import io
import urllib.request
import threading
from typing import Callable, Dict, Any, List
import customtkinter as ctk
from PIL import Image

from ..theme import (
    DARK_BG, DARK_SURFACE, DARK_CARD, DARK_CARD_HOVER, DARK_BORDER,
    ACCENT_INDIGO, ACCENT_INDIGO_LIGHT, ACCENT_CYAN,
    TEXT_PRIMARY, TEXT_SECONDARY, TEXT_MUTED,
    SUCCESS_GREEN, SUCCESS_GREEN_HOVER,
    FONT_SUBTITLE, FONT_BODY, FONT_BODY_BOLD, FONT_SMALL, FONT_SMALL_BOLD, FONT_BADGE
)


class MediaPreviewCard(ctk.CTkFrame):
    """
    Displays probed video details (thumbnail, title, duration, uploader)
    and quality selection with one-click download trigger.
    """

    def __init__(
        self,
        parent,
        on_start_download: Callable[[Dict[str, Any], Dict[str, Any]], None],
    ):
        super().__init__(
            parent,
            fg_color=DARK_SURFACE,
            corner_radius=16,
            border_width=1,
            border_color=DARK_BORDER
        )
        self.on_start_download = on_start_download
        self.current_media_info: Dict[str, Any] = {}
        self.selected_option: Dict[str, Any] = {}
        self.thumbnail_image: ctk.CTkImage = None

        self._build_ui()
        self.pack_forget()

    def _build_ui(self):
        self.inner = ctk.CTkFrame(self, fg_color="transparent")
        self.inner.pack(fill="both", expand=True, padx=16, pady=16)

        # Top content layout: Left = Thumbnail, Right = Info & Selection
        self.content_frame = ctk.CTkFrame(self.inner, fg_color="transparent")
        self.content_frame.pack(fill="x", pady=(0, 14))

        # Thumbnail container
        self.thumb_box = ctk.CTkFrame(
            self.content_frame,
            width=220,
            height=125,
            fg_color=DARK_CARD,
            corner_radius=12
        )
        self.thumb_box.pack(side="left", padx=(0, 16))
        self.thumb_box.pack_propagate(False)

        self.thumb_label = ctk.CTkLabel(
            self.thumb_box,
            text="🎬",
            font=("Segoe UI", 32),
            text_color=TEXT_MUTED
        )
        self.thumb_label.place(relx=0.5, rely=0.5, anchor="center")

        # Duration badge inside thumbnail
        self.duration_badge = ctk.CTkLabel(
            self.thumb_box,
            text="",
            font=FONT_BADGE,
            text_color="#FFFFFF",
            fg_color="#000000",
            corner_radius=6,
            padx=6,
            pady=2
        )
        # Placed at bottom-right of thumb_box

        # Details Column (Title, Uploader, Options)
        self.details_frame = ctk.CTkFrame(self.content_frame, fg_color="transparent")
        self.details_frame.pack(side="left", fill="both", expand=True)

        self.title_label = ctk.CTkLabel(
            self.details_frame,
            text="",
            font=FONT_SUBTITLE,
            text_color=TEXT_PRIMARY,
            anchor="w",
            justify="left",
            wraplength=480
        )
        self.title_label.pack(anchor="w", pady=(0, 4))

        self.uploader_label = ctk.CTkLabel(
            self.details_frame,
            text="",
            font=FONT_SMALL,
            text_color=ACCENT_CYAN,
            anchor="w"
        )
        self.uploader_label.pack(anchor="w", pady=(0, 10))

        # Quality Selection Row
        quality_label = ctk.CTkLabel(
            self.details_frame,
            text="Select Format & Quality:",
            font=FONT_SMALL_BOLD,
            text_color=TEXT_SECONDARY,
            anchor="w"
        )
        quality_label.pack(anchor="w", pady=(0, 4))

        self.quality_dropdown = ctk.CTkOptionMenu(
            self.details_frame,
            values=["Best available"],
            font=FONT_BODY,
            dropdown_font=FONT_BODY,
            fg_color=DARK_CARD,
            button_color=ACCENT_INDIGO,
            button_hover_color=ACCENT_INDIGO_LIGHT,
            text_color=TEXT_PRIMARY,
            dropdown_fg_color=DARK_CARD,
            dropdown_text_color=TEXT_PRIMARY,
            dropdown_hover_color=DARK_CARD_HOVER,
            corner_radius=10,
            height=38,
            command=self._on_quality_changed
        )
        self.quality_dropdown.pack(fill="x")

        # Download Action Button
        self.download_btn = ctk.CTkButton(
            self.inner,
            text="⬇ Download to Computer",
            font=FONT_BODY_BOLD,
            fg_color=SUCCESS_GREEN,
            hover_color=SUCCESS_GREEN_HOVER,
            text_color="#FFFFFF",
            height=46,
            corner_radius=12,
            command=self._handle_download_click
        )
        self.download_btn.pack(fill="x")

    def display_media(self, info: Dict[str, Any]):
        """Populates the card with probed media info."""
        self.current_media_info = info
        title = info.get("title", "Untitled Media")
        uploader = info.get("uploader", "Unknown Creator")
        duration_str = info.get("duration_str", "")
        options: List[Dict[str, Any]] = info.get("options", [])

        self.title_label.configure(text=title)
        self.uploader_label.configure(text=f"👤 {uploader}")

        if duration_str:
            self.duration_badge.configure(text=duration_str)
            self.duration_badge.place(relx=0.95, rely=0.92, anchor="se")
        else:
            self.duration_badge.place_forget()

        # Populate quality dropdown
        labels = [opt["label"] for opt in options] if options else ["Best available"]
        self.quality_dropdown.configure(values=labels)
        if labels:
            self.quality_dropdown.set(labels[0])
            self.selected_option = options[0] if options else {}
            self._update_download_button_label()

        # Load thumbnail asynchronously
        thumb_url = info.get("thumbnail")
        if thumb_url:
            self._load_thumbnail_async(thumb_url)
        else:
            self.thumb_label.configure(image="", text="🎬")

        self.pack(fill="x", padx=16, pady=10)

    def _on_quality_changed(self, selected_label: str):
        options = self.current_media_info.get("options", [])
        for opt in options:
            if opt["label"] == selected_label:
                self.selected_option = opt
                break
        self._update_download_button_label()

    def _update_download_button_label(self):
        label = self.selected_option.get("label", "")
        is_audio = self.selected_option.get("is_audio", False)
        icon = "🎵" if is_audio else "⬇"
        short_label = label.split("(")[0].strip()
        self.download_btn.configure(text=f"{icon} Download Now ({short_label})")

    def _load_thumbnail_async(self, url: str):
        def _fetch():
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
                with urllib.request.urlopen(req, timeout=8) as resp:
                    raw_data = resp.read()
                    pil_img = Image.open(io.BytesIO(raw_data)).convert("RGBA")
                    # Crop/fit into 220x125
                    pil_img = pil_img.resize((220, 125), Image.Resampling.LANCZOS)
                    ctk_img = ctk.CTkImage(light_image=pil_img, dark_image=pil_img, size=(220, 125))

                    self.after(0, lambda: self._apply_thumbnail(ctk_img))
            except Exception:
                pass

        threading.Thread(target=_fetch, daemon=True).start()

    def _apply_thumbnail(self, ctk_img: ctk.CTkImage):
        self.thumbnail_image = ctk_img
        self.thumb_label.configure(image=self.thumbnail_image, text="")

    def _handle_download_click(self):
        if self.current_media_info and self.selected_option:
            self.on_start_download(self.current_media_info, self.selected_option)
