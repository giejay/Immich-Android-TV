#!/usr/bin/env python3
"""Downloads a small stock set (photos + short videos) from Pexels through its official API.

    export PEXELS_API_KEY=...   # free key: https://www.pexels.com/api/
    python3 fetch_pexels.py     # -> ./media/photos, ./media/videos, ./media/CREDITS.md

Pexels license: free to use, attribution appreciated; CREDITS.md records photographer and source page.
"""
import json
import os
import sys
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://api.pexels.com"
OUT = Path(__file__).parent / "media"
# theme -> (photo count, video count); themes become albums in seed.py
THEMES = {
    "mountains": (7, 1),
    "beach": (7, 1),
    "city": (7, 1),
    "forest": (6, 0),
    "food": (6, 0),
    "family picnic": (6, 1),
}
MAX_VIDEO_SECONDS = 25
MAX_VIDEO_WIDTH = 1280


def get(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"Authorization": KEY, "User-Agent": "immich-android-tv-next-test-media"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def download(url: str, target: Path) -> None:
    if target.exists():
        return
    # media files are public CDN links, no auth header needed
    request = urllib.request.Request(url, headers={"User-Agent": "immich-android-tv-next-test-media"})
    with urllib.request.urlopen(request, timeout=120) as response, open(target, "wb") as file:
        file.write(response.read())


def main() -> None:
    credits = ["# Media credits", "", "Stock media from [Pexels](https://www.pexels.com) (Pexels License).", ""]
    for theme, (photos, videos) in THEMES.items():
        slug = theme.replace(" ", "-")
        (OUT / "photos").mkdir(parents=True, exist_ok=True)
        (OUT / "videos").mkdir(parents=True, exist_ok=True)
        query = urllib.parse.quote(theme)
        if photos:
            data = json.loads(get(f"{API}/v1/search?query={query}&per_page={photos}&orientation=landscape"))
            for photo in data["photos"]:
                target = OUT / "photos" / f"{slug}-{photo['id']}.jpg"
                download(photo["src"]["large2x"], target)
                credits.append(f"- {target.name}: {photo['photographer']} - {photo['url']}")
        if videos:
            data = json.loads(get(f"{API}/videos/search?query={query}&per_page=15&orientation=landscape"))
            picked = 0
            for video in data["videos"]:
                if video["duration"] > MAX_VIDEO_SECONDS or picked >= videos:
                    continue
                files = [f for f in video["video_files"] if f["file_type"] == "video/mp4" and (f.get("width") or 0) <= MAX_VIDEO_WIDTH]
                if not files:
                    continue
                best = max(files, key=lambda f: f.get("width") or 0)
                target = OUT / "videos" / f"{slug}-{video['id']}.mp4"
                download(best["link"], target)
                credits.append(f"- {target.name}: {video['user']['name']} - {video['url']}")
                picked += 1
        print(f"{theme}: done")
    (OUT / "CREDITS.md").write_text("\n".join(credits) + "\n")
    print(f"Saved to {OUT}")


if __name__ == "__main__":
    KEY = os.environ.get("PEXELS_API_KEY", "")
    if not KEY:
        sys.exit("Set PEXELS_API_KEY first (free key: https://www.pexels.com/api/)")
    main()
