"""
Recent Downloads Library View for Fetchreel Windows Desktop.
Matches Android RecentDownloadsList with playback, explorer reveal, and deletion.
"""

from typing import Callable, List, Dict, Any
import customtkinter as ctk

from ..theme import (
    DARK_BG, DARK_SURFACE, DARK_CARD, DARK_CARD_HOVER, DARK_BORDER,
    ACCENT_INDIGO, ACCENT_CYAN, TEXT_PRIMARY, TEXT_SECONDARY, TEXT_MUTED,
    SUCCESS_GREEN, ERROR_RED, ERROR_RED_HOVER,
    FONT_SUBTITLE, FONT_BODY, FONT_BODY_BOLD, FONT_SMALL, FONT_SMALL_BOLD, FONT_BADGE
)


class RecentDownloadsView(ctk.CTkFrame):
    """
    Shows history of downloaded files with immediate playback and management actions.
    """

    def __init__(
        self,
        parent,
        on_play: Callable[[str], None],
        on_open_folder: Callable[[str], None],
        on_delete: Callable[[str, bool], None],
        on_refresh: Callable[[], None],
    ):
        super().__init__(parent, fg_color="transparent")
        self.on_play = on_play
        self.on_open_folder = on_open_folder
        self.on_delete = on_delete
        self.on_refresh = on_refresh

        self._build_ui()

    def _build_ui(self):
        # Header Row
        header = ctk.CTkFrame(self, fg_color="transparent")
        header.pack(fill="x", padx=16, pady=(10, 8))

        title = ctk.CTkLabel(
            header,
            text="📁 Downloaded Library",
            font=FONT_SUBTITLE,
            text_color=TEXT_PRIMARY
        )
        title.pack(side="left")

        refresh_btn = ctk.CTkButton(
            header,
            text="🔄 Refresh",
            font=FONT_SMALL,
            fg_color=DARK_CARD,
            hover_color=DARK_BORDER,
            text_color=TEXT_SECONDARY,
            width=80,
            height=30,
            corner_radius=8,
            command=self.on_refresh
        )
        refresh_btn.pack(side="right")

        # Scrollable items container
        self.scroll_area = ctk.CTkScrollableFrame(
            self,
            fg_color=DARK_SURFACE,
            corner_radius=16,
            border_width=1,
            border_color=DARK_BORDER
        )
        self.scroll_area.pack(fill="both", expand=True, padx=16, pady=(0, 10))

    def update_items(self, items: List[Dict[str, Any]]):
        for w in self.scroll_area.winfo_children():
            w.destroy()

        if not items:
            empty_box = ctk.CTkFrame(self.scroll_area, fg_color="transparent")
            empty_box.pack(fill="both", expand=True, pady=60)

            empty_icon = ctk.CTkLabel(
                empty_box,
                text="📂",
                font=("Segoe UI", 42),
                text_color=TEXT_MUTED
            )
            empty_icon.pack(pady=(0, 8))

            empty_label = ctk.CTkLabel(
                empty_box,
                text="No downloads yet in Fetchreel library",
                font=FONT_BODY,
                text_color=TEXT_SECONDARY
            )
            empty_label.pack()

            hint_label = ctk.CTkLabel(
                empty_box,
                text="Switch to Single Video or Playlist to download your favorite media!",
                font=FONT_SMALL,
                text_color=TEXT_MUTED
            )
            hint_label.pack(pady=4)
            return

        for item in items:
            self._create_item_row(item)

    def _create_item_row(self, item: Dict[str, Any]):
        path = item.get("path", "")
        title = item.get("title", "Untitled")
        fmt = item.get("format", "MP4")
        size = item.get("size", "")
        date = item.get("date", "")
        exists = item.get("exists", True)
        is_audio = item.get("is_audio", False)

        card = ctk.CTkFrame(
            self.scroll_area,
            fg_color=DARK_CARD,
            corner_radius=12,
            border_width=1,
            border_color=DARK_BORDER
        )
        card.pack(fill="x", pady=4, padx=4)

        inner = ctk.CTkFrame(card, fg_color="transparent")
        inner.pack(fill="x", padx=12, pady=10)

        # Format Badge
        badge_color = ACCENT_INDIGO if is_audio else ACCENT_CYAN
        badge = ctk.CTkLabel(
            inner,
            text=f"🎵 {fmt}" if is_audio else f"🎬 {fmt}",
            font=FONT_BADGE,
            text_color="#FFFFFF",
            fg_color=badge_color,
            corner_radius=6,
            padx=8,
            pady=3
        )
        badge.pack(side="left", padx=(0, 12))

        # Title and details
        info_col = ctk.CTkFrame(inner, fg_color="transparent")
        info_col.pack(side="left", fill="both", expand=True)

        title_lbl = ctk.CTkLabel(
            info_col,
            text=title[:80],
            font=FONT_BODY_BOLD,
            text_color=TEXT_PRIMARY,
            anchor="w",
            justify="left"
        )
        title_lbl.pack(anchor="w")

        meta_text = f"{size}  •  {date}" if size else date
        if not exists:
            meta_text += "  •  (File moved or deleted)"

        meta_lbl = ctk.CTkLabel(
            info_col,
            text=meta_text,
            font=FONT_SMALL,
            text_color=TEXT_MUTED if exists else ERROR_RED,
            anchor="w"
        )
        meta_lbl.pack(anchor="w")

        # Action Buttons
        btn_box = ctk.CTkFrame(inner, fg_color="transparent")
        btn_box.pack(side="right")

        if exists:
            play_btn = ctk.CTkButton(
                btn_box,
                text="▶ Play",
                font=FONT_SMALL_BOLD,
                fg_color=SUCCESS_GREEN,
                hover_color="#059669",
                text_color="#FFFFFF",
                width=65,
                height=30,
                corner_radius=8,
                command=lambda p=path: self.on_play(p)
            )
            play_btn.pack(side="left", padx=(0, 6))

            folder_btn = ctk.CTkButton(
                btn_box,
                text="📁 Folder",
                font=FONT_SMALL,
                fg_color=DARK_SURFACE,
                hover_color=DARK_BORDER,
                text_color=TEXT_PRIMARY,
                width=70,
                height=30,
                corner_radius=8,
                command=lambda p=path: self.on_open_folder(p)
            )
            folder_btn.pack(side="left", padx=(0, 6))

        del_btn = ctk.CTkButton(
            btn_box,
            text="🗑️",
            font=FONT_SMALL,
            fg_color=DARK_SURFACE,
            hover_color=ERROR_RED,
            text_color=ERROR_RED,
            width=34,
            height=30,
            corner_radius=8,
            command=lambda p=path, t=title: self._confirm_delete(p, t)
        )
        del_btn.pack(side="left")

    def _confirm_delete(self, path: str, title: str):
        # Open simple confirm dialog
        dialog = ctk.CTkToplevel(self)
        dialog.title("Delete Download")
        dialog.geometry("380x180")
        dialog.resizable(False, False)
        dialog.configure(fg_color=DARK_BG)
        dialog.transient(self.winfo_toplevel())
        dialog.grab_set()

        inner = ctk.CTkFrame(dialog, fg_color=DARK_SURFACE, corner_radius=14)
        inner.pack(fill="both", expand=True, padx=16, pady=16)

        msg = ctk.CTkLabel(
            inner,
            text=f"Delete \"{title[:40]}...\"?",
            font=FONT_BODY_BOLD,
            text_color=TEXT_PRIMARY
        )
        msg.pack(pady=(10, 4))

        sub = ctk.CTkLabel(
            inner,
            text="Do you also want to remove the file from your computer?",
            font=FONT_SMALL,
            text_color=TEXT_SECONDARY
        )
        sub.pack(pady=(0, 16))

        btn_row = ctk.CTkFrame(inner, fg_color="transparent")
        btn_row.pack(fill="x")

        def _do_del(delete_file: bool):
            dialog.destroy()
            self.on_delete(path, delete_file)

        btn_disk = ctk.CTkButton(
            btn_row,
            text="Delete from Disk",
            font=FONT_SMALL,
            fg_color=ERROR_RED,
            hover_color=ERROR_RED_HOVER,
            command=lambda: _do_del(True)
        )
        btn_disk.pack(side="left", fill="x", expand=True, padx=(0, 6))

        btn_cancel = ctk.CTkButton(
            btn_row,
            text="Cancel",
            font=FONT_SMALL,
            fg_color=DARK_CARD,
            hover_color=DARK_BORDER,
            text_color=TEXT_PRIMARY,
            command=dialog.destroy
        )
        btn_cancel.pack(side="right", fill="x", expand=True, padx=(6, 0))
