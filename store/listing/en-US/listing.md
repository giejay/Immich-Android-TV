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
• Screensaver (Daydream) with your albums, people or favorites and several transitions
• Home screen channels for quick access
• Customize which pages are shown and what the photo overlay displays
• Fully D-pad friendly, no touch screen needed

Getting started: you need a running Immich server (v3 or newer). Sign in with your server URL and an API key, or scan a QR code with your phone. The app only talks to your server: no ads, no analytics, no tracking.

Open source (GPL-3.0): https://github.com/daniii88/Immich-Android-TV-Next
Immich is a trademark of its respective owners.

---
## Console answers (draft, please review before submitting)

**Data safety**
- Data collected: none. Data shared: none. The app sends data only to the server the user configures; nothing goes to the developer or third parties.
- Data encrypted in transit: depends on the user's server (HTTP is possible by design for home servers).
- Users can request data deletion: not applicable, no data is held by the developer. Privacy policy URL: link to `store/privacy-policy.md` (raw GitHub URL or Pages).

**Content rating:** user-generated content comes only from the user's own server; no ads, no purchases. Expect "Everyone"; answer the questionnaire as a media viewer.

**App category:** Photography (alternative: Video Players & Editors). **Ads:** none. **Android TV:** leanback declared, banner 1280x720 in `store/assets`.

**Permissions to justify:** INTERNET (connect to the user's server), WRITE_EPG_DATA (home screen channels).

**Open before release:** ask the Immich team for their OK about name and icon; add real screenshots (1920x1080, 16:9) taken against the test server.
