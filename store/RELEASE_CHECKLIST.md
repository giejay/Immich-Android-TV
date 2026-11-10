# Release checklist (preparation, nothing is published yet)

What is ready in the repository and what only the developer can do. See `listing/*/listing.md` for texts and Console answers.

## Ready in the repo
- Application id `io.github.daniii88.immichandroidtvnext`, `versionCode 1` / `versionName 1.0.0`, `targetSdk 37`, `minSdk 24`
- Release pipeline `.github/workflows/release.yml`: signed APK and AAB, GitHub release, upload to Play
- Icon 512, feature graphic 1024x500, TV banner 1280x720 in `assets/`; 8 screenshots 1920x1080 in `../screenshots/` (order in the listing file)
- Listing texts (en-US, de-DE), privacy policy (en/de), Console answers, App access instructions for the reviewers
- Offline demo mode, so the reviewers can use the app without a server; phone sign-in runs locally in the home network (no relay, no third party)

## Only you can do
1. ~~Ask the Immich team~~ **Done (2026-11-10, FUTO support):** the name "Immich TV Next (Unofficial)", the pinwheel icon and showing the Immich name in the listing are fine. Their only condition is a clear disclaimer that the app is not affiliated with the Immich project; it is in the README, the store listings and the privacy policy (and in the app under Settings -> About).
2. **Play Console developer account** (one-time fee). New personal accounts may have to run a closed test with a minimum number of testers for a number of days before production access: check the current rule in the Console.
3. **Upload keystore:** create one and keep it safe (losing it blocks updates, unless you use Play App Signing with an upload key):
   `keytool -genkeypair -v -keystore release-keystore.jks -alias immichtvnext -keyalg RSA -keysize 4096 -validity 10000`
4. **GitHub secrets** used by the workflow: `RELEASE_KEYSTORE_BASE64` (`base64 -w0 release-keystore.jks`), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `STRINGS_OTHER` (base64 of `strings_other.xml`; a placeholder file is enough, no relay address is needed any more), `PLAY_STORE_SERVICE_ACCOUNT_JSON` (service account with Play API access).
5. **Create the app in the Console:** name, default language, app or game, free, declarations. Choose Android TV as the form factor.
6. **Contact email** for the listing (public) and a decision whether the project gets its own address.
7. **Privacy policy URL:** e.g. https://github.com/daniii88/Immich-Android-TV-Next/blob/main/store/privacy-policy.md (a rendered GitHub page; GitHub Pages is optional).
8. **Fill the Console forms** from `listing/en-US/listing.md`: App access, Data safety, content rating, target audience, ads, category.
9. **Upload** the assets, texts and the first AAB to the **internal testing** track, install it from Play on the TV, then promote.

## Before the first release build
- Run `./gradlew bundleRelease` with the real keystore and test the release APK (R8 is on). Checked on 2026-11-10 with the debug-signed build on the emulator (timeline, viewer; no crash); repeat it with the real keystore build on a real TV.
- Optionally regenerate the baseline profile (`:app:generateBaselineProfile` on the TV, signed in).
- Bump `versionCode` for every upload.
