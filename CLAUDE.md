# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Fork of `giejay/Immich-Android-TV` (GPL-3.0): an Android TV client for the Immich photo server (compatible with Immich v3+). Goal of this fork: modernize the UI (Compose for TV, Synology-Photos-like look) while keeping all functionality. Package ID is still `nl.giejay.android.tv.immich`, app name "Immich TV".

## Build & test

Requires JDK 17, Android SDK (`local.properties` with `sdk.dir=/home/daniii/Android/Sdk`; file is git-ignored).

```bash
./gradlew assembleDebug                 # output: app/build/outputs/apk/debug/ImmichTV-<version>.apk
./gradlew testDebugUnitTest             # all unit tests (Robolectric + Mockito + Truth)
./gradlew :app:testDebugUnitTest --tests "nl.giejay.android.tv.immich.timeline.TimelineViewModelTest"   # single class
```

- Untracked local file needed to build (git-ignored, must stay a placeholder and never be committed): `app/src/main/res/values/strings_other.xml` (copy of `app/strings_other.xml.example`).
- Spotless/ktlint is configured but `enforceCheck false`; don't expect it to gate builds.
- `gradle/gradle-daemon-jvm.properties` requests toolchain 25; the environment has JDK 17 (build works regardless).
- CI: `.github/workflows/android.yml` (build) and `release.yml` (signed APK + GitHub release; keystore via env vars).

## Architecture

Two Gradle modules: `:app` and `:mediaslider:mediaslider` (slideshow/viewer library: ViewPager + ExoPlayer/Media3 + Glide, plugin-based overlays such as date, metadata, story progress).

`:app` (`app/src/main/java/nl/giejay/android/tv/immich/`):
- `MainActivity` is a single `FragmentActivity` hosting a Jetpack Navigation `NavHostFragment` (`res/navigation/nav_graph.xml`, Safe Args). It picks the start destination (auth vs. home), handles `immichtv://` deep links from home-screen channels (`homescreenchannels/`), and builds the slider configuration for deep-linked assets.
- `home/HomeFragment` hosts a Compose side navigation rail (`ui/nav/HomeNavRail`, collapsed icons / expanded labels, overlays the content) and shows the selected page (Timeline, Albums, Photos, Random, People, Recent, Seasonal, Folders, Edit, Settings) as a child fragment in `R.id.home_content`. Rail and content are View siblings so D-Pad focus uses the normal View focus search; Back from a page focuses the rail first. Pages talk to the home via `HomeNavHost`/`NavTransitionListener` (Timeline uses it for leave-off focus handling). Hidden items are stored by their localized name in the `HIDDEN_HOME_ITEMS` preference; edit mode toggles them.
- Page fragments (`album/`, `assets/`, `people/`) extend `shared/fragment/VerticalCardGridFragment` (paging/sorting/background logic) on top of `GridFragment`, which renders a Compose `ui/grid/ImmichCardGrid` of `Card`s (`ui/card/ImmichCard`, Glide). `card/CardPresenter` (Leanback) remains only for the settings icon row.
- `timeline/` is the most modern part: a mosaic `RecyclerView` (`TimelineMosaicLayoutManager`/`LayoutEngine`), scrubber (`TimelineScrubberView/Model`), focus navigation, video preview, memories row, `TimelineViewModel`. It still depends on Leanback focus behaviour (`BrowseFrameLayout`, focus search listeners) and on `HomeNavHost` to know whether the side navigation owns focus. Logic is heavily unit-tested; keep it intact.
- `api/`: Retrofit + Gson `ApiService`, `ApiClient` wrapper returning arrow `Either`, models in `api/model` (kept by ProGuard).
- `shared/prefs/`: preference system. `Pref` subclasses (`BooleanPref`, `EnumPref`, `IntListPref`, `ActionPref`, ...) declared as `data object`s in `Preferences.kt`, grouped in `PrefScreen`/`PrefCategory`; `PreferenceManager` + `LivePreference` for reads/observation. The settings dialogs render these declarations as Compose (`ui/settings/PrefScreenPanel`, hosted by `settings/ComposePrefScreenFragment` inside `SettingsDialogFragment`); only the metadata customizer still uses the Leanback preference fragments (`SettingsScreenFragment`).
- `auth/`: login via `GuidedStepSupportFragment` (steps 1/2) and phone-based auth; API key stored with `security/ApiKeyCipher`.
- `screensaver/`: `ScreenSaverService` (DreamService) with asset loaders; `slider/ImmichMediaSlider` bridges the app to the mediaslider module.
- `ImmichApplication` / `AppContextInitializer`: static `appContext` is used widely (including inside `Pref` object initializers), so it must be set before preferences are touched.

Firebase, Play Billing (donate) and play-services-auth have been removed; release builds use a plain logcat Timber tree.

Compose for TV (`androidx.tv:tv-material`) is set up under `ui/` (`ui/card/ImmichCard`, `ui/grid/ImmichCardGrid`) (`ImmichTheme`, `ImmichIcons`, `ImmichFocus`; `immichComposeView` hosts Compose in Leanback/View code). UI is still mostly Leanback (`Theme.AppCompat.Leanback`, PNG icons) until the later steps. A debug-only `ThemeGalleryActivity` shows the theme: `adb shell am start -n <applicationId>/nl.giejay.android.tv.immich.ui.ThemeGalleryActivity`.

## Working rules for this fork

- Plan first; no code changes until the user approved the plan. Afterwards: one branch per step, small commits with English messages, run `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` after each step and report the results.
- Migrate incrementally: Leanback fragments and Compose coexist via `ComposeView` until everything is converted. No big-bang rewrite.
- Leave API layer, ViewModels, auth, screensaver and timeline logic untouched unless strictly necessary.
- D-Pad navigation must work at every step: everything focusable, visible focus state, sensible Back behavior.
- Ask before removing features or swapping dependencies that were not explicitly listed.
- Keep license/copyright headers and the `LICENSE` file unchanged.
- Never commit secrets, API keys or real server data; `strings_other.xml` stays a placeholder and out of the repo.
- Do not push; the user pushes.
- Test targets: Android TV emulator and a real TV via adb.
