"""
Optional YouTube cookies for Fetchreel.

Cloud/datacenter IPs (Render included) get challenged by YouTube's bot
detection far more often than home connections do. Passing a real,
logged-in session's cookies to yt-dlp raises the trust score of each
request and cuts down on how often that happens — see the "YouTube
blocking" section in README.md for the full picture and its limits.

To use this: export cookies.txt from a real YouTube session in your
browser (the "Get cookies.txt LOCALLY" extension works well, Netscape
format), then base64-encode the file and set it as YOUTUBE_COOKIES_B64:

    base64 -w0 cookies.txt        # Linux
    base64 -i cookies.txt         # macOS

Paste the output as the YOUTUBE_COOKIES_B64 environment variable. This
module decodes it to a local file at startup; nothing else to configure.
Leave it unset and Fetchreel just doesn't send cookies — everything else
still works the same.
"""

import base64
import os
import tempfile
from pathlib import Path

_COOKIES_PATH = None

_b64 = os.environ.get("YOUTUBE_COOKIES_B64")
if _b64:
    try:
        candidate = Path(tempfile.gettempdir()) / "fetchreel_cookies.txt"
        candidate.write_bytes(base64.b64decode(_b64))
        _COOKIES_PATH = str(candidate)
    except Exception:
        # A malformed value here shouldn't take the whole app down —
        # just fall back to no cookies.
        _COOKIES_PATH = None


def cookiefile():
    """Path to the decoded cookies.txt, or None if not configured."""
    return _COOKIES_PATH
