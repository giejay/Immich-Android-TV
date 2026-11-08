#!/usr/bin/env python3
"""Creates an admin user, an API key and uploads ./media into a fresh test Immich server.

    python3 seed.py [http://localhost:2283]

Media files are named <theme>-<id>.<ext> (see fetch_pexels.py); every theme becomes an album, capture
dates are spread over several years; the first themes are dated today (month/day) in earlier years so "N years ago" memories have content.
Writes the server URL and API key to ./.state/credentials.txt (git-ignored); enter both in the app.
"""
import json
import mimetypes
import random
import sys
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib import request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:2283").rstrip("/")
HERE = Path(__file__).parent
MEDIA = HERE / "media"
STATE = HERE / ".state"
EMAIL, PASSWORD, NAME = "test@example.com", "immich-test-1234", "Test User"


def call(path, payload=None, headers=None, method=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = request.Request(BASE + path, data=data, method=method or ("POST" if data else "GET"),
                          headers={"Content-Type": "application/json", "Accept": "application/json", **(headers or {})})
    try:
        with request.urlopen(req, timeout=120) as response:
            body = response.read()
    except Exception as error:  # noqa: BLE001 - surface the server's message for the user
        detail = getattr(error, "read", lambda: b"")().decode(errors="replace")
        raise SystemExit(f"{method or 'POST'} {path} failed: {error} {detail}")
    return json.loads(body) if body else None


def upload(path: Path, api_key: str, taken: datetime, favorite: bool) -> str:
    boundary = uuid.uuid4().hex
    iso = taken.strftime("%Y-%m-%dT%H:%M:%S.000Z")
    fields = {"deviceAssetId": path.name, "deviceId": "test-seed", "fileCreatedAt": iso,
              "fileModifiedAt": iso, "isFavorite": str(favorite).lower()}
    parts = []
    for name, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="assetData"; filename="{path.name}"\r\n'
                 f"Content-Type: {mime}\r\n\r\n".encode())
    parts.append(path.read_bytes())
    parts.append(f"\r\n--{boundary}--\r\n".encode())
    req = request.Request(BASE + "/api/assets", data=b"".join(parts), method="POST",
                          headers={"x-api-key": api_key, "Accept": "application/json",
                                   "Content-Type": f"multipart/form-data; boundary={boundary}"})
    with request.urlopen(req, timeout=300) as response:
        return json.loads(response.read())["id"]


def main():
    if not MEDIA.exists():
        sys.exit("No ./media folder; run fetch_pexels.py first or put photos/videos there.")
    try:
        call("/api/auth/admin-sign-up", {"email": EMAIL, "name": NAME, "password": PASSWORD})
    except SystemExit:
        print("Admin already exists, logging in")
    token = call("/api/auth/login", {"email": EMAIL, "password": PASSWORD})["accessToken"]
    key = call("/api/api-keys", {"name": "android-tv-test", "permissions": ["all"]},
               {"Authorization": f"Bearer {token}"})["secret"]

    rng = random.Random(42)
    now = datetime.now(timezone.utc)
    files = sorted(p for p in MEDIA.rglob("*") if p.suffix.lower() in {".jpg", ".jpeg", ".png", ".mp4", ".mov"})
    themes = sorted({p.name.rsplit("-", 1)[0] for p in files})
    # The first themes are dated on today's month/day in earlier years so Immich's "N years ago" memories
    # (which pick assets by day of year) have content; the others are spread over the past years.
    anniversary_years = [2, 3, 5, 7]
    base_days = {}
    anniversary = {}
    for position, theme in enumerate(themes):
        if position < len(anniversary_years):
            years = anniversary_years[position]
            try:
                day = now.replace(year=now.year - years, hour=12, minute=0, second=0, microsecond=0)
            except ValueError:  # Feb 29
                day = now.replace(year=now.year - years, day=28, hour=12, minute=0, second=0, microsecond=0)
            base_days[theme], anniversary[theme] = day, True
        else:
            base_days[theme] = now - timedelta(days=rng.choice([20, 70, 400, 765, 1130, 1500, 1860, 2190]))
    albums: dict[str, list[str]] = {t: [] for t in themes}
    for index, path in enumerate(files):
        theme = path.name.rsplit("-", 1)[0]
        if theme in anniversary:  # stay on the same calendar day (12:00 +-5 h)
            taken = base_days[theme] + timedelta(hours=rng.randint(-5, 5), minutes=rng.randint(0, 59))
        else:
            taken = base_days[theme] + timedelta(hours=rng.randint(0, 70), minutes=rng.randint(0, 59))
        asset = upload(path, key, taken, favorite=index % 9 == 0)
        albums[theme].append(asset)
        print(f"uploaded {path.name}")
    for theme, ids in albums.items():
        if ids:
            call("/api/albums", {"albumName": theme.replace("-", " ").title(), "assetIds": ids},
                 {"x-api-key": key})
    STATE.mkdir(exist_ok=True)
    (STATE / "credentials.txt").write_text(f"server: {BASE}\napi key: {key}\nlogin: {EMAIL} / {PASSWORD}\n")
    print(f"Done. Credentials in {STATE / 'credentials.txt'}")


if __name__ == "__main__":
    main()
