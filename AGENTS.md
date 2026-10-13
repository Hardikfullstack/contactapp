# Contacts - Master Index

`com.phone.contacts` — a dialer/contacts Android app (default-dialer-capable: InCallService,
CallScreeningService, block list, after-call screen, call reminders, speed dial, call themes/wallpaper,
quick response, recycle bin). Single Gradle module (`app`). No DI framework and no ViewModel layer —
screens are stateful `@Composable` functions that call `data/`/`util/`/`service/` singletons directly
(`remember`/`mutableStateOf` for local state, `MutableStateFlow` singletons like `AppConfigStore` for
app-wide state).

## Package Layout
| Package | Responsibility | Notes |
|---------|---------------|-------|
| `ui/screens/` | Most Compose screens (Contacts, ContactDetail, AddContact, Settings, Blocking, SpeedDial, Theme, CallWallpaper, RingToneScreen, RecycleBin, ImportExport, EmergencyContacts, QuickResponse, ...) | One file per screen, named `<Feature>Screen.kt`; no ViewModel — reads/writes `data/` repositories and `util/*Preferences` objects directly. |
| `ui/features/<feature>/` | The 4 screens that aren't plain nav destinations: `call/` (in-call UI, its own `CallActivity`), `aftercall/` (`AfterCallActivity`, post-call overlay/reminder), `onboarding/` (language/permission setup flow), `splash/` | These have their own Activity and/or run outside the main `NavHost`. |
| `ui/navigation/` | `MainNavigation.kt` — single `NavHost`, routes as a sealed `MainScreen` hierarchy with `.route`/`.routeFor()`; mounts `AppUpdatePrompt()` + maintenance-dialog gating at the top level, plus `OnDestinationChangedListener`-based screen-view analytics | No separate onboarding NavHost file — onboarding screens are plain Activities/composables launched before `MainNavigation`. |
| `ui/components/` | Shared composables: `SettingsRow`/`SettingsCard`, `SearchHighlight.kt` (query-match highlighting), `ListRowSkeleton` (shimmer), `AppUpdatePrompt`/`UpdateAppDialog`/`MaintenanceDialog` | Plain stateless composables, no DI. |
| `ui/theme/` | Material3 theme, color scheme, `primaryAccentColor()` | |
| `data/` (flat files) | `ContactRepository` (wraps `ContentResolver`/`ContactsContract` — contact CRUD, photo add/remove, `findContactByNumber`/`findContactByNumberNow` sync+suspend lookups), `CallLogRepository` (wraps `CallLog.Calls`), `BlockRepository` (`BlockedNumberContract`), `Contact.kt`/`CallLogItem.kt` (plain data classes) | Contacts/call-log themselves come from the **system ContentResolver**, not Room. |
| `data/local/` | Room: `AppDatabase` + entities/DAOs for `DeletedContactEntity`/`RecycleBinDao`, `ReminderEntity`/`ReminderDao`, `EmergencyContactEntity`/`EmergencyContactDao`, `NumberSeriesEntity`/`NumberSeriesDao` | Room only backs recycle bin, call reminders, emergency contacts, and spam number-series — never the contacts/call-log themselves. |
| `data/network/` | `ApiClient` (object, Retrofit), `ApiService` — single `POST api/getApp` endpoint for the remote ad/config panel | |
| `data/model/` | `AppResponse`/`AppResult` — the remote-config schema (`native_N`, `banner_N`, `interstitial_N`, `app_open_N`, `extra_data_N` slots, each with `_on_off`/`_message`) | Shared schema convention with the reference app (`contactapp`). |
| `service/` | `object` singletons + Android components: `CallManager` (call-state holder, synchronous + cached contact resolution for the call screen), `ContactsCallService` (InCallService), `ContactsCallScreeningService` (CallScreeningService), `CallNotificationManager`, `AfterCallReceiver`/`AfterCallOverlay` | Not DI-bound; consumed as `CallManager.foo()` etc. |
| `util/` | Mixed: stateless helpers (`CallUtils`, `MessageUtils`, `WhatsAppUtils`, `GoogleMeetUtils`, `DeviceUtils`) **and** stateful singletons/`MutableStateFlow` holders (`AppConfigStore`, `AnalyticsManager`, `CrashlyticsManager`, `*Preferences` objects backed by `SharedPreferences`) | Despite the name, several are app-wide state holders, not pure functions. |
| `ads/` | AdMob wrappers: `InterstitialAdManager`/`AppOpenAdManager` (preload-ahead-of-render caches keyed by ad unit id), `NativeAdCache`/`BannerAdCache`/`ListAdCache`, `AdPlacements` (maps remote-config slot → ad unit id), `BackInterstitial.kt` (`rememberInterstitialTrigger`/`rememberBackWithInterstitial`), `AdLoadingScreen`/`AdLoadingLottie` | Mirrors `contactapp`'s `ads/` package naming/pattern. |

## Global Conventions
- **Kotlin**: 2.4.10 (see `gradle/libs.versions.toml`) · **Compose BOM**: 2024.09.00 · **AGP**: 9.4.1 · no Hilt/Dagger, no Room outside the four entities above
- **Compose**: Material 3, one screen per `@Composable` function named `<Feature>Screen`, reading app-wide state via `collectAsState()` on a singleton `StateFlow` (e.g. `AppConfigStore.config`) and local state via `remember { mutableStateOf(...) }` — no `hiltViewModel()`, no per-screen ViewModel class
- **Naming**: `<Feature>Screen.kt` in `ui/screens/` (or `ui/features/<feature>/` for the 4 Activity-backed flows); `*Repository.kt` in `data/`; `<Thing>Manager.kt`/`<Thing>Preferences.kt` for `service/`/`util/`/`ads/` singletons
- **Coroutines**: `rememberCoroutineScope()`/`LaunchedEffect` in composables; `service/` singletons keep their own scope where needed (e.g. `CallManager`)
- **Strings**: all user-facing text in `res/values/strings.xml` (+ localized `values-<lang>/strings.xml`)
- **Reference app**: `../contactapp` (sibling project, full source) is the canonical pattern source when porting a feature (in-app update/review, analytics event naming, ad placement slots, after-call behavior, etc.) — check it before inventing a new pattern for something it already solves

## Build & Test
```bash
./gradlew :app:assembleDebug       # build debug
./gradlew :app:assembleRelease     # build release (minify + resource shrinking + proguard on)
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:installDebug        # install on device
```
Note: builds/installs are normally run by the developer in Android Studio, not from this assistant.

## Creating a New Feature — Checklist
1. New file `ui/screens/<Feature>Screen.kt` (or a new `ui/features/<feature>/` package if it needs its own Activity, like `call`/`aftercall`)
2. Screen reads/writes state directly from a `data/` repository or `util/*Preferences` object — no ViewModel to wire up
3. Add a route to `MainScreen` (in `ui/navigation/MainNavigation.kt`), then a `composable(MainScreen.X.route) { XScreen(...) }` block in its `NavHost`
4. If it needs new persisted data beyond what `ContentResolver` already exposes, add a Room entity/DAO in `data/local/` and register it in `AppDatabase`
5. If it shows an ad, add/reuse a slot in `AdPlacements` and call the relevant `ads/` composable (`NativeOrBannerAdView`, `rememberBackWithInterstitial`, ...)

## Known Rough Edges (don't treat as the pattern to follow)
- `util/` mixes genuinely stateless helpers with stateful singletons — check before assuming a `util/` class is side-effect-free
- Settings → Privacy Policy / Terms & Conditions rows are currently disabled (`enabled = false`, `SettingsScreen.kt`) — not wired to a URL yet, though the remote-config schema already has `extra_data_3`/`extra_data_4` slots free for this (matching `contactapp`'s convention)
- `ads/InterstitialAdManager.show()` has no timeout safety net: if AdMob fires neither `onAdDismissedFullScreenContent` nor `onAdFailedToShowFullScreenContent` (known SDK flakiness), the caller's `onDismissed` callback never fires — a watchdog fix for this was tried and explicitly reverted; don't re-add it without asking
