# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Fork of `giejay/Immich-Android-TV` (GPL-3.0): an Android TV client for the Immich photo server (compatible with Immich v3+). Goal of this fork: modernize the UI (Compose for TV, Synology-Photos-like look) while keeping all functionality. Application id is `io.github.daniii88.immichandroidtvnext`, app name "Immich Android TV Next"; the Kotlin packages and the Gradle `namespace` stay `nl.giejay.android.tv.immich`, so components must be addressed as `io.github.daniii88.immichandroidtvnext/nl.giejay.android.tv.immich.<Class>` (not with the `.Class` shorthand). Deep link scheme: `immichandroidtvnext://`.

## Build & test

Requires JDK 17, Android SDK (`local.properties` with `sdk.dir=/home/daniii/Android/Sdk`; file is git-ignored).

```bash
./gradlew assembleDebug                 # output: app/build/outputs/apk/debug/ImmichAndroidTVNext-<version>.apk
./gradlew testDebugUnitTest             # all unit tests (Robolectric + Mockito + Truth)
./gradlew :app:testDebugUnitTest --tests "nl.giejay.android.tv.immich.timeline.TimelineViewModelTest"   # single class
./gradlew :app:generateBaselineProfile  # regenerate the baseline profile on a connected device (app signed in), writes app/src/release/generated/baselineProfiles
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest   # startup / timeline scroll benchmarks with and without the profile (use the real TV)
```

- `assembleRelease` signs with the debug key when no keystore exists and `CI` is unset (CI keeps requiring the real keystore). The `:baselineprofile` module (journey in `Journey.kt`, `BaselineProfileGenerator`, `Benchmarks`) generates the profile that is committed under `app/src/release/generated/baselineProfiles`; regenerate it after bigger UI changes.
- Untracked local file needed to build (git-ignored, must stay a placeholder and never be committed): `app/src/main/res/values/strings_other.xml` (copy of `app/strings_other.xml.example`).
- Spotless/ktlint is configured but `enforceCheck false`; don't expect it to gate builds.
- `gradle/gradle-daemon-jvm.properties` requests toolchain 25; the environment has JDK 17 (build works regardless).
- CI: `.github/workflows/android.yml` (build) and `release.yml` (signed APK + GitHub release; keystore via env vars).

## Architecture

Two Gradle modules: `:app` and `:mediaslider:mediaslider` (slideshow/viewer library: Compose pager + ExoPlayer/Media3 + Glide, plugin-based overlays such as date, metadata, story progress).

`:app` (`app/src/main/java/nl/giejay/android/tv/immich/`):
- `MainActivity` is a single `FragmentActivity` hosting a Jetpack Navigation `NavHostFragment` (`res/navigation/nav_graph.xml`, Safe Args). It picks the start destination (auth vs. home), handles `immichtv://` deep links from home-screen channels (`homescreenchannels/`), and builds the slider configuration for deep-linked assets.
- `home/HomeFragment` hosts a Compose side navigation rail (`ui/nav/HomeNavRail`, collapsed icons / expanded labels, overlays the content) and shows the selected page (Timeline, Albums, Photos, Random, People, Recent, Seasonal, Folders, Edit, Settings) as a child fragment in `R.id.home_content`. Rail and content are View siblings so D-Pad focus uses the normal View focus search; Back from a page focuses the rail first. Pages talk to the home via `HomeNavHost`/`NavTransitionListener` (Timeline uses it for leave-off focus handling). Hidden items are stored by their localized name in the `HIDDEN_HOME_ITEMS` preference; edit mode toggles them.
- Page fragments (`album/`, `assets/`, `people/`) extend `shared/fragment/VerticalCardGridFragment` (paging/sorting/background logic) on top of `GridFragment` (a plain `Fragment` with an optional title and background artwork view), which renders a Compose `ui/grid/ImmichCardGrid` of `Card`s (`ui/card/ImmichCard`, Glide). No Leanback presenters remain.
- `timeline/` is the most modern part: a mosaic `RecyclerView` (`TimelineMosaicLayoutManager`/`LayoutEngine`), scrubber (`TimelineScrubberView/Model`), focus navigation, video preview, memories row, `TimelineViewModel`. Focus search is handled by `FocusSearchFrameLayout`; it depends on `HomeNavHost` to know whether the side navigation owns focus. Logic is heavily unit-tested; keep it intact.
- Compose timeline (step K), behind the off-by-default `EXPERIMENTAL_COMPOSE_TIMELINE` switch (Settings → Debug; `HomeFragment` picks `TimelineComposeFragment` instead of `TimelineFragment`): `TimelineComposeFragment` hosts `ui/timeline/TimelineScreen`, a `LazyColumn` of day headers, mosaic rows and the memories row, fed by the same activity-scoped `TimelineViewModel` (layout engine, neighbor map, leave-off snapshot). Pieces: `TimelineMosaicCell`/`TimelineMemoryCard` (`TimelineComposables`, focus video preview on one shared muted ExoPlayer), `TimelineScrubber` (Compose port of the rail, still driven by `TimelineScrubberModel`) and `TimelineSliderLauncher` (opens the slider, same leave-off bookkeeping as `TimelineFragment`). D-pad focus goes through `TimelineFocusActions` + per-asset `FocusRequester`s. Pitfalls handled there: restoring focus after the slider / side navigation must not be overwritten by the entry cell that grabs focus first (`pendingRestore`, `leftForSlider`), and months loading around a jump or restore target shift list indices, so the top item is pinned by key (`anchorTo`). Both timelines coexist until the View one is removed (planned step K5, after comparing them on a real TV).
- `api/`: Retrofit + Gson `ApiService`, `ApiClient` wrapper returning arrow `Either`, models in `api/model` (kept by ProGuard).
- `shared/prefs/`: preference system. `Pref` subclasses (`BooleanPref`, `EnumPref`, `IntListPref`, `ActionPref`, ...) declared as `data object`s in `Preferences.kt`, grouped in `PrefScreen`/`PrefCategory`; `PreferenceManager` + `LivePreference` for reads/observation. The settings dialogs render these declarations as Compose (`ui/settings/PrefScreenPanel`, hosted by `settings/ComposePrefScreenFragment` inside `SettingsDialogFragment`); the metadata customizer (`MetaDataCustomizerFragment` preview + `MetaDataItemCustomizerFragment` editor, UI in `ui/settings/MetaDataScreens`) is Compose too. Compose content inside the dialog extends `ComposeSettingsFragment`, which makes Back close an open choice list first.
- `auth/`: login steps 1/2 are Compose screens (`ui/auth/AuthScreens`, hosted by `AuthFragmentStep1/2`; text entry opens a real `BasicTextField` + keyboard on OK) and phone-based auth (`AuthByPhoneFragment`, still a View layout with a QR code); API key stored with `security/ApiKeyCipher`. Saving credentials clears the whole nav back stack so every page starts with the new server settings; the activity-scoped `TimelineViewModel` is keyed by `ApiClientConfig` for the same reason.
- `mediaslider` is Compose end to end: overlays (bottom metadata, top date) in `overlay/SliderOverlays`, the pager in `pager/ComposeSliderPager` (`HorizontalPager` with image, double-image and video pages in `SliderPages`; the page-settled logic lives in `MediaSliderView.onPageSettled`), and the controller bar in `controller/SliderControllerBar` driven by `SliderControllerUiState` (focus order and restore in `ControllerLayout`; plugin buttons are `ControllerButtonSpec` data, not Views). All video pages share one `ExoPlayer` (`requirePlayer`) that is bound to the page that came to rest; the poster covers the wait for the first frame. It is released on pause and rebuilt on resume (`release`/`resume`). `MediaSliderController` only knows the `SliderPager` interface. Page transitions are pure math in `transitions/SliderTransitionMath`, the slideshow zoom/pan and panorama scroll in `effects/SlideshowEffects` (seedable, unit tested). `createOverlayView` installs its own lifecycle owner where the host has none (screensaver DreamService window). Pinch zoom was dropped (no touch on TV).
- `screensaver/`: `ScreenSaverService` (DreamService) with asset loaders; `slider/ImmichMediaSlider` bridges the app to the mediaslider module. Screensaver transitions (`SCREENSAVER_TRANSITION`/`_DURATION` -> `MediaSliderConfiguration.transition`) are the `SliderTransition` enum (page math in `transitions/SliderTransitionMath`); they only apply while "Slide the new asset in" is on, SLIDE is the plain pager movement. The in-app preview and the DreamService share one configuration (`buildScreenSaverConfiguration`).
- `ImmichApplication` / `AppContextInitializer`: static `appContext` is used widely (including inside `Pref` object initializers), so it must be set before preferences are touched.

Firebase, Play Billing (donate) and play-services-auth have been removed; release builds use a plain logcat Timber tree.

Compose for TV (`androidx.tv:tv-material`) is set up under `ui/` (`ui/card/ImmichCard`, `ui/grid/ImmichCardGrid`) (`ImmichTheme`, `ImmichIcons`, `ImmichFocus`; `immichComposeView` hosts Compose in View code). Leanback (the library) is gone: the app theme is `Theme.AppCompat.NoActionBar`; the manifest still declares the `leanback` system feature and `LEANBACK_LAUNCHER`, which are Android TV requirements, not the library. The preference model in `shared/prefs` still builds on `androidx.preference`. PNG icons remain in places. A debug-only `ThemeGalleryActivity` shows the theme: `adb shell am start -n <applicationId>/nl.giejay.android.tv.immich.ui.ThemeGalleryActivity`.

## Working rules for this fork

- Plan first; no code changes until the user approved the plan. Afterwards: one branch per step, small commits with English messages, run `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` after each step and report the results.
- Migrate incrementally: Views and Compose coexist via `ComposeView` until everything is converted. No big-bang rewrite.
- Leave API layer, ViewModels, auth, screensaver and timeline logic untouched unless strictly necessary.
- D-Pad navigation must work at every step: everything focusable, visible focus state, sensible Back behavior.
- Ask before removing features or swapping dependencies that were not explicitly listed.
- Keep license/copyright headers and the `LICENSE` file unchanged.
- Never commit secrets, API keys or real server data; `strings_other.xml` stays a placeholder and out of the repo.
- Do not push; the user pushes.
- Test targets: Android TV emulator and a real TV via adb.
