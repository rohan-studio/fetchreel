"""
GUI components for Fetchreel Windows Desktop.
"""

from .header import HeaderView
from .url_card import UrlInputCard
from .media_card import MediaPreviewCard
from .progress_card import DownloadProgressCard
from .playlist_view import PlaylistView
from .recent_view import RecentDownloadsView
from .qr_dialog import QrCodeDialog

__all__ = [
    "HeaderView",
    "UrlInputCard",
    "MediaPreviewCard",
    "DownloadProgressCard",
    "PlaylistView",
    "RecentDownloadsView",
    "QrCodeDialog",
]
