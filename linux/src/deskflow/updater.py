"""Desktop auto-updater (#396) & changelog (#397) for DeskFlow Linux."""
from __future__ import annotations

import json
import urllib.request

from deskflow import __version__

GITHUB_RELEASE_API = "https://api.github.com/repos/HenryYT13/deskflow-app/releases/latest"


def check_for_updates() -> dict:
    """Checks GitHub Releases for a newer DeskFlow version."""
    try:
        req = urllib.request.Request(GITHUB_RELEASE_API, headers={"User-Agent": "DeskFlow-Linux"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            tag = str(data.get("tag_name") or "").lstrip("v")
            return {
                "current": __version__,
                "latest": tag or __version__,
                "update_available": bool(tag and tag != __version__),
                "url": data.get("html_url", ""),
                "notes": data.get("body", ""),
            }
    except Exception as exc:
        return {"current": __version__, "latest": __version__, "update_available": False, "error": str(exc)}
