#!/usr/bin/env python3
"""Downloads the curated stock set listed in media.json (about 55 MB) from the public Pexels CDN.

    python3 fetch_media.py     # -> ./media/photos, ./media/videos, ./media/CREDITS.md

No API key needed: photo ids and video files were picked by hand. Re-running skips finished files.
Pexels license: free to use; see the page of each id for the photographer.
"""
import json
import urllib.request
from pathlib import Path

HERE = Path(__file__).parent
OUT = HERE / "media"
HEADERS = {"User-Agent": "immich-android-tv-next-test-media"}


def download(url: str, target: Path) -> None:
    if target.exists():
        return
    # temp file first so an interrupted transfer never leaves a truncated file that later runs skip
    partial = target.with_suffix(target.suffix + ".part")
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=180) as response, open(partial, "wb") as file:
            file.write(response.read())
        partial.replace(target)
    finally:
        partial.unlink(missing_ok=True)


def main() -> None:
    manifest = json.loads((HERE / "media.json").read_text())
    (OUT / "photos").mkdir(parents=True, exist_ok=True)
    (OUT / "videos").mkdir(parents=True, exist_ok=True)
    credits = ["# Media credits", "", manifest["license"], ""]
    for theme, ids in manifest["photos"].items():
        for photo_id in ids:
            target = OUT / "photos" / f"{theme}-{photo_id}.jpg"
            download(f"https://images.pexels.com/photos/{photo_id}/pexels-photo-{photo_id}.jpeg?auto=compress&cs=tinysrgb&w=1920", target)
            credits.append(f"- {target.name}: https://www.pexels.com/photo/{photo_id}/")
        print(f"{theme}: photos done")
    for theme, videos in manifest["videos"].items():
        for video in videos:
            target = OUT / "videos" / f"{theme}-{video['id']}.mp4"
            download(video["file"], target)
            credits.append(f"- {target.name}: https://www.pexels.com/video/{video['id']}/")
        print(f"{theme}: videos done")
    (OUT / "CREDITS.md").write_text("\n".join(credits) + "\n")
    print(f"Saved to {OUT}")


if __name__ == "__main__":
    main()
