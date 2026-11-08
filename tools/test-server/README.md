# Local test server

A real Immich server filled with stock media (Pexels), so the app can be tested and README screenshots
taken without private photos.

1. Install Docker (once): `sudo apt install docker.io docker-compose-v2 && sudo usermod -aG docker $USER` (log in again).
2. `docker compose up -d` (first start pulls the images; add `--profile ml` for faces/smart search).
3. `export PEXELS_API_KEY=...` (free key: https://www.pexels.com/api/) and `python3 fetch_pexels.py`.
4. `python3 seed.py` -> creates the admin user, an API key and uploads `./media` (albums per theme).
5. In the app use the URL and API key from `.state/credentials.txt`. In the emulator the host is `http://10.0.2.2:2283`.

`media/`, `data/` and `.state/` are git-ignored. `docker compose down && rm -rf data` resets the server.
