"""
QR Code Dialog for sharing Fetchreel Android APK and GitHub releases.
Matches Android app QR Code dialog.
"""

import io
import customtkinter as ctk
from PIL import Image
import qrcode

from ..theme import (
    DARK_BG, DARK_SURFACE, DARK_CARD, DARK_BORDER,
    ACCENT_INDIGO, ACCENT_CYAN, TEXT_PRIMARY, TEXT_SECONDARY, TEXT_MUTED,
    FONT_SUBTITLE, FONT_BODY, FONT_SMALL
)


class QrCodeDialog(ctk.CTkToplevel):
    """
    Modern modal dialog presenting a QR code to download Fetchreel APK.
    """

    APK_URL = "https://github.com/rohan-studio/fetchreel/releases/download/v1.0.0/fetchreel-v1.0.0.apk"
    REPO_URL = "https://github.com/rohan-studio/fetchreel"

    def __init__(self, parent):
        super().__init__(parent)
        self.title("Scan & Download Mobile APK")
        self.geometry("420x520")
        self.resizable(False, False)
        self.configure(fg_color=DARK_BG)
        self.transient(parent)
        self.grab_set()

        # Center on parent window
        parent.update_idletasks()
        px = parent.winfo_x()
        py = parent.winfo_y()
        pw = parent.winfo_width()
        ph = parent.winfo_height()
        self.geometry(f"+{px + max(0, (pw - 420) // 2)}+{py + max(0, (ph - 520) // 2)}")

        self._build_ui()

    def _build_ui(self):
        container = ctk.CTkFrame(
            self,
            fg_color=DARK_SURFACE,
            corner_radius=16,
            border_width=1,
            border_color=DARK_BORDER
        )
        container.pack(fill="both", expand=True, padx=20, pady=20)

        # Title
        title_label = ctk.CTkLabel(
            container,
            text="📱 Fetchreel for Android",
            font=FONT_SUBTITLE,
            text_color=TEXT_PRIMARY
        )
        title_label.pack(pady=(20, 4))

        subtitle_label = ctk.CTkLabel(
            container,
            text="Scan with your phone to download the .APK directly",
            font=FONT_SMALL,
            text_color=TEXT_SECONDARY
        )
        subtitle_label.pack(pady=(0, 16))

        # Generate QR code with dark/light colors
        qr = qrcode.QRCode(
            version=1,
            error_correction=qrcode.constants.ERROR_CORRECT_M,
            box_size=7,
            border=2,
        )
        qr.add_data(self.APK_URL)
        qr.make(fit=True)
        qr_pil = qr.make_image(fill_color="white", back_color="#0F1117").convert("RGBA")

        self.qr_image = ctk.CTkImage(light_image=qr_pil, dark_image=qr_pil, size=(220, 220))
        qr_label = ctk.CTkLabel(container, image=self.qr_image, text="")
        qr_label.pack(pady=10)

        # Status badge
        badge = ctk.CTkLabel(
            container,
            text="v1.0.0 • arm64-v8a • 100% On-Device",
            font=FONT_SMALL,
            text_color=ACCENT_CYAN,
            fg_color=DARK_CARD,
            corner_radius=8,
            padx=12,
            pady=4
        )
        badge.pack(pady=8)

        # Copy URL / Dismiss
        btn_row = ctk.CTkFrame(container, fg_color="transparent")
        btn_row.pack(fill="x", padx=20, pady=(16, 20))

        copy_btn = ctk.CTkButton(
            btn_row,
            text="Copy APK Link",
            fg_color=ACCENT_INDIGO,
            hover_color="#4F46E5",
            font=FONT_BODY,
            height=38,
            corner_radius=10,
            command=self._copy_link
        )
        copy_btn.pack(side="left", fill="x", expand=True, padx=(0, 6))

        close_btn = ctk.CTkButton(
            btn_row,
            text="Close",
            fg_color=DARK_CARD,
            hover_color=DARK_BORDER,
            text_color=TEXT_PRIMARY,
            font=FONT_BODY,
            height=38,
            corner_radius=10,
            command=self.destroy
        )
        close_btn.pack(side="right", fill="x", expand=True, padx=(6, 0))

    def _copy_link(self):
        self.clipboard_clear()
        self.clipboard_append(self.APK_URL)
        self.update()
