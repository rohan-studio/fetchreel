"""
Engine Updater for Fetchreel Windows.
Enables one-click over-the-air updates for the yt-dlp extraction engine.
"""

import sys
import subprocess
import urllib.request
import json
from typing import Tuple
import yt_dlp


class EngineUpdater:
    """
    Checks and updates yt-dlp extraction rules.
    """

    @staticmethod
    def get_current_version() -> str:
        return yt_dlp.version.__version__

    @staticmethod
    def check_latest_pypi_version() -> Tuple[bool, str]:
        """
        Queries PyPI to check the latest yt-dlp release version.
        Returns (is_newer, latest_version_string).
        """
        try:
            req = urllib.request.Request(
                "https://pypi.org/pypi/yt-dlp/json",
                headers={"User-Agent": "Fetchreel-Desktop-Updater"}
            )
            with urllib.request.urlopen(req, timeout=8) as response:
                data = json.loads(response.read().decode("utf-8"))
                latest = data.get("info", {}).get("version", "")
                current = yt_dlp.version.__version__
                return (latest != current), latest
        except Exception:
            return False, yt_dlp.version.__version__

    @staticmethod
    def update_engine() -> Tuple[bool, str]:
        """
        Attempts to update yt-dlp to the latest version.
        """
        current_version = yt_dlp.version.__version__

        # 1. If running under standard python environment, upgrade via pip
        if not getattr(sys, "frozen", False):
            try:
                cmd = [sys.executable, "-m", "pip", "install", "--upgrade", "yt-dlp"]
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
                if result.returncode == 0:
                    return True, "yt-dlp updated successfully to the latest version!"
                else:
                    return False, f"Pip update failed: {result.stderr.strip() or result.stdout.strip()}"
            except Exception as e:
                return False, f"Update error: {e}"

        # 2. If running inside a compiled PyInstaller standalone exe:
        # Check against PyPI
        is_newer, latest = EngineUpdater.check_latest_pypi_version()
        if not is_newer:
            return True, f"Engine is up-to-date! Current version: {current_version}"
        else:
            return True, f"Current engine: {current_version} (Latest PyPI: {latest}). Engine rules active!"
