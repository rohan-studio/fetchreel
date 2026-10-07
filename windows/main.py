"""
Fetchreel Desktop Entry Point.
Runs the Modern Dark Material UI Desktop Downloader.
"""

import os
import sys
import ctypes

# Ensure src directory is in sys.path
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.join(BASE_DIR, "src")
if SRC_DIR not in sys.path:
    sys.path.insert(0, SRC_DIR)

# Set Windows App User Model ID for proper taskbar icon grouping
if sys.platform == "win32":
    try:
        app_id = "rohanstudio.fetchreel.desktop.1.0"
        ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(app_id)
    except Exception:
        pass

    # High DPI awareness
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(1)
    except Exception:
        try:
            ctypes.windll.user32.SetProcessDPIAware()
        except Exception:
            pass

from fetchreel.gui.app import FetchreelApp


def main():
    app = FetchreelApp()
    app.mainloop()


if __name__ == "__main__":
    main()
