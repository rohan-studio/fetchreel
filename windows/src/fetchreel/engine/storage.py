"""
Storage, Configuration, and Downloads Management for Fetchreel Windows.
"""

import os
import sys
import json
import time
import subprocess
from typing import List, Dict, Any, Optional
from .downloader import format_bytes


class StorageManager:
    """
    Manages user settings, download directory, and download history.
    """

    def __init__(self):
        # Default config directory: %APPDATA%/Fetchreel or ~/.fetchreel
        appdata = os.environ.get("APPDATA")
        if appdata:
            self.config_dir = os.path.join(appdata, "Fetchreel")
        else:
            self.config_dir = os.path.expanduser("~/.fetchreel")
        os.makedirs(self.config_dir, exist_ok=True)

        self.config_file = os.path.join(self.config_dir, "config.json")
        self.history_file = os.path.join(self.config_dir, "history.json")

        self.config = self._load_config()

    def _default_download_dir(self) -> str:
        downloads = os.path.join(os.path.expanduser("~"), "Downloads", "Fetchreel")
        os.makedirs(downloads, exist_ok=True)
        return downloads

    def _load_config(self) -> Dict[str, Any]:
        default = {
            "download_dir": self._default_download_dir(),
            "auto_capture_clipboard": True,
        }
        if os.path.exists(self.config_file):
            try:
                with open(self.config_file, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    default.update(data)
            except Exception:
                pass
        return default

    def _save_config(self):
        try:
            with open(self.config_file, "w", encoding="utf-8") as f:
                json.dump(self.config, f, indent=2)
        except Exception:
            pass

    def get_download_dir(self) -> str:
        d = self.config.get("download_dir") or self._default_download_dir()
        os.makedirs(d, exist_ok=True)
        return d

    def set_download_dir(self, directory: str):
        self.config["download_dir"] = directory
        self._save_config()

    def get_auto_capture(self) -> bool:
        return bool(self.config.get("auto_capture_clipboard", True))

    def set_auto_capture(self, enabled: bool):
        self.config["auto_capture_clipboard"] = enabled
        self._save_config()

    def get_history(self) -> List[Dict[str, Any]]:
        """Returns downloaded media list, newest first."""
        if not os.path.exists(self.history_file):
            return []
        try:
            with open(self.history_file, "r", encoding="utf-8") as f:
                items: List[Dict[str, Any]] = json.load(f)
                # Verify whether files still exist
                for item in items:
                    path = item.get("path")
                    if path and os.path.exists(path):
                        item["exists"] = True
                        item["size"] = format_bytes(os.path.getsize(path))
                    else:
                        item["exists"] = False
                return items
        except Exception:
            return []

    def add_history(self, title: str, filepath: str, url: str, is_audio: bool, thumbnail: str = ""):
        """Adds a downloaded item to history."""
        items = self.get_history()
        # Remove if already exists with same path
        items = [i for i in items if i.get("path") != filepath]

        size_str = format_bytes(os.path.getsize(filepath)) if os.path.exists(filepath) else ""
        ext = os.path.splitext(filepath)[1].lower().replace(".", "").upper()

        new_entry = {
            "title": title,
            "path": filepath,
            "url": url,
            "is_audio": is_audio,
            "format": ext or ("MP3" if is_audio else "MP4"),
            "size": size_str,
            "thumbnail": thumbnail,
            "timestamp": int(time.time()),
            "date": time.strftime("%Y-%m-%d %H:%M"),
        }
        items.insert(0, new_entry)
        # Keep top 100 entries
        items = items[:100]

        try:
            with open(self.history_file, "w", encoding="utf-8") as f:
                json.dump(items, f, indent=2)
        except Exception:
            pass

    def delete_history_item(self, filepath: str, delete_disk_file: bool = False):
        """Removes an item from history and optionally removes file on disk."""
        items = self.get_history()
        items = [i for i in items if i.get("path") != filepath]
        try:
            with open(self.history_file, "w", encoding="utf-8") as f:
                json.dump(items, f, indent=2)
        except Exception:
            pass

        if delete_disk_file and filepath and os.path.exists(filepath):
            try:
                os.remove(filepath)
            except Exception:
                pass

    @staticmethod
    def play_media(filepath: str):
        """Opens file in default Windows media player."""
        if not filepath or not os.path.exists(filepath):
            return
        try:
            os.startfile(filepath)
        except Exception:
            subprocess.Popen(["cmd", "/c", "start", "", filepath], shell=True)

    @staticmethod
    def reveal_in_explorer(filepath: str):
        """Highlights the file in Windows File Explorer or opens the download folder."""
        if not filepath:
            return
        try:
            norm_path = os.path.normpath(os.path.abspath(filepath))
            if os.path.isfile(norm_path):
                # In Windows Explorer, /select,"C:\path\to\file" must NOT have quotes around /select
                # Calling Popen with the formatted string ensures Explorer correctly highlights the file
                try:
                    subprocess.Popen(f'explorer /select,"{norm_path}"')
                    return
                except Exception:
                    pass

                # Fallback: open parent folder directly
                parent_dir = os.path.dirname(norm_path)
                if os.path.isdir(parent_dir):
                    try:
                        os.startfile(parent_dir)
                        return
                    except Exception:
                        subprocess.Popen(f'explorer "{parent_dir}"')
                        return

            elif os.path.isdir(norm_path):
                try:
                    os.startfile(norm_path)
                    return
                except Exception:
                    subprocess.Popen(f'explorer "{norm_path}"')
                    return

            # If path does not exist on disk, fallback to opening its parent or user's downloads folder
            parent_dir = os.path.dirname(norm_path)
            if parent_dir and os.path.isdir(parent_dir):
                try:
                    os.startfile(parent_dir)
                    return
                except Exception:
                    subprocess.Popen(f'explorer "{parent_dir}"')
                    return
        except Exception:
            pass
