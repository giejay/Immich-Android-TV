# Google Play listing (en-US)

**Title** (max 30): Immich TV Next (Unofficial)

**Short description** (max 80):
Browse your self-hosted Immich photos and videos on Android TV.

**Full description** (max 4000):
Immich TV Next is an unofficial community client for your own Immich photo server, built for the TV remote. It is not made by, affiliated with or endorsed by the Immich project.

Bring your photo library to the big screen:
• Timeline with day headers, a quick scrubber and a memories row
• Albums, people, folders, recent and seasonal views, random photos
• Search across your library
• Full-screen viewer with slideshow, video playback and photo details
• Screensaver (Daydream) with your albums, random or recent photos, or photos from a similar time of year, and several transitions
• Home screen channels for quick access
• Customize which pages are shown and what the photo overlay displays
• Sign in with an API key or by scanning a QR code with your phone (local, no cloud service)
• Demo mode with sample photos: try the app without an Immich server
• Fully D-pad friendly, no touch screen needed

Getting started: you need a running Immich server (v3 or newer). Sign in with your server URL and an API key, or look around first in the demo mode. The app talks only to your server: no ads, no analytics, no tracking.

Open source (GPL-3.0): https://github.com/daniii88/Immich-Android-TV-Next
Immich is a trademark of its respective owners.

**What's new** (release notes, max 500):
First release: timeline with scrubber and memories, albums, people, folders, search, full-screen viewer with slideshow, screensaver and an offline demo mode.

**Graphics:** in `store/assets/`: icon `icon-512.png` (512x512), feature graphic `feature-graphic-1024x500.png`, TV banner `tv-banner-1280x720.png`. Screenshots (1920x1080, 16:9) are in `screenshots/`; upload in this order: timeline, albums, album-detail, photos, search, viewer, navigation, settings.

---
## Console answers (draft, please review before submitting)

**App access** (the app needs an Immich server to sign in, so tell the reviewers how to get in without one):
"No account is needed. On the first screen choose 'Demo mode' and press Continue. The demo runs fully offline with bundled sample photos (no server, no login). To use the app with real data you need your own Immich server (v3 or newer)."

**Data safety**
- Data collected or shared by the developer: none. The app has no analytics, ads, crash reporting or third-party SDKs and contacts only the Immich server that the user enters. Photos and metadata go only to and from that server, which the user runs.
- The server URL, settings and API key stay on the device (the key is encrypted with the Android Keystore) and are excluded from backups.
- Phone sign-in runs entirely in the user's home network: the TV serves a small page (plain HTTP, one-time token) and receives the server URL and API key from the phone. No relay or third-party service is contacted.
- Data encrypted in transit: depends on the user's server (HTTP is possible by design for home servers); answer "No, not all data" if asked and explain in the policy.
- Data deletion: not applicable, the developer holds no data. Privacy policy: link to `store/privacy-policy.md` (GitHub page, e.g. https://github.com/daniii88/Immich-Android-TV-Next/blob/main/store/privacy-policy.md).

**Content rating:** user-generated content comes only from the user's own server; no ads, no purchases, no social features. Answer the questionnaire as a media viewer; expect "Everyone".

**Target audience:** 18+ / general (not designed for children). **Ads:** none. **Government app / financial features / health:** no. **Category:** Photography (alternative: Video Players & Editors).

**Form factor:** Android TV only (leanback declared, TV banner in `store/assets`). No phone or tablet listing.

**Permissions to justify:** INTERNET (connect to the user's server), WRITE_EPG_DATA (home screen channels).

**Contact:** an email address is required in the listing and is public. Decide which before the release (see `RELEASE_CHECKLIST.md`).

**Open before release:** ask the Immich team for their OK about name and icon.
