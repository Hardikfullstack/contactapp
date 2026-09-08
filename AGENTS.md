# ContactApp - Master Index

`com.example.contactapp` — a dialer/contacts Android app (default-dialer-capable: InCallService,
CallScreeningService, spam/block list, fake calls, call themes, flash alerts, call reminders,
after-call screen, auto-reply). Single Gradle module (`app`), MVVM-ish layering with Hilt DI, but
**not** a strict clean-architecture split — ViewModels live next to their screens, not in a
top-level `viewmodel/` package (see below).

## Package Layout
| Package | Responsibility | Notes |
|---------|---------------|-------|
| `ui/features/<feature>/` | Compose screens **and their ViewModels** | 20 feature packages (see `ui/features/AGENTS.md`). ViewModel lives beside its screen, e.g. `ui/features/contacts/ContactsViewModel.kt`. |
| `ui/navigation/` | `MainNavigation.kt` (post-onboarding `NavHost` + bottom bar), `OnboardingNavigation.kt` | Plain Navigation-Compose `NavHost { composable(route) { ... } }` — no custom DSL. Routes are a sealed `MainScreen` hierarchy with `.route`/`.createRoute()`. |
| `ui/components/` | Shared composables used across features (`CommonHeader`, `ContactItem`, `SettingsItem`, ad views wrappers, etc.) | Not Hilt-scoped; plain stateless composables. |
| `viewmodel/` | **Only** `AppConfigViewModel` — app-wide remote config/ad-unit-id state, not a per-screen ViewModel home | Do not add per-feature ViewModels here; they belong in `ui/features/<feature>/`. |
| `domain/model/` | Pure Kotlin data classes: `Contact`, `DetailedContact`, `CallLogModel`, `DialKey` | No Android imports except where unavoidable (e.g. `Uri` in repository contracts, not here). |
| `domain/repository/` | Interfaces: `ContactRepository`, `CallLogRepository` | `Flow` for streams, `suspend fun` for one-shot I/O. |
| `data/local/` | Room: `AppDatabase` (entities: `DeletedContactEntity`, `ReminderEntity`; DAOs: `RecycleBinDao`, `ReminderDao`) | Contacts/call-log themselves come from the **system ContentResolver**, not Room — Room here only backs the recycle bin and call reminders. |
| `data/network/` | Retrofit: `ApiClient` (object), `ApiService` | Single endpoint (`POST api/getApp`) that fetches this app's remote ad/config panel data. |
| `data/repository/` | `ContactRepositoryImpl` (wraps `ContentResolver` + `ContactsContract`, plus `RecycleBinDao`), `CallLogRepositoryImpl` (wraps `ContentResolver` call log + `LocalBlockManager`) | These are ContentProvider-backed, not Room-backed — most of the "database" work here is `ContactsContract` query building. |
| `di/` | `DatabaseModule` (Room + DAOs), `RepositoryModule` (`@Binds` for the two repositories) | See `di/AGENTS.md`. |
| `service/` | `object` singletons + Android components: `CallManager`, `ContactCallService` (InCallService), `ContactCallScreeningService` (CallScreeningService), `FakeCallConnectionService`, receivers (`BootReceiver`, `FakeCallReceiver`, `CallReminderReceiver`, ...), managers (`FlashAlertManager`, `CallAnnouncerManager`, `AutoReplyManager`, `SpamManager`, ...) | Not Hilt-bound; consumed as `Foo.bar()`. |
| `util/` | Mixed bag: stateless helpers (`CallUtils`, `ColorUtils`, `QrUtils`) **and** stateful singletons (`PreferenceManager`, `AnalyticsManager`, `NetworkMonitor`, `LocaleChangeState`) | Despite the name, not everything here is a pure/stateless utility — several are app-wide `object` state holders. |
| `ads/` | AdMob wrappers: `NativeAdCache`/`BannerAdCache` (preload-ahead-of-render caches), `AppOpenAdManager`, `InterstitialAdManager`, `AppOpenBackgroundReturnTrigger`, `AppOpenCounter`, plus `BannerAdView`/`NativeAdView` composables | See `ads/AGENTS.md`. |

## Global Conventions
- **Kotlin**: 2.4.10 (see `gradle/libs.versions.toml`) · **Compose BOM**: 2026.02.01 · **Hilt**: 2.58 · **Room**: 2.8.4
- **Compose**: Material 3, one screen per `@Composable` function named `<Feature>Screen`, consuming its ViewModel via `hiltViewModel()`
- **Naming**: `<Feature>Screen.kt` + `<Feature>ViewModel.kt` + `<Feature>UiState` (often a nested/inline data class, not always its own file) inside `ui/features/<feature>/`; `*RepositoryImpl.kt`; `<Thing>Manager.kt` for `service/`/`ads/` singletons
- **Coroutines**: `viewModelScope.launch` in ViewModels; `service/` singletons keep their own `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. Raw `Thread`/`Thread.sleep` in a few older service files is known technical debt, not the pattern to copy
- **Strings**: All user-facing text goes in `res/values/strings.xml` (+ localized `values-<lang>/strings.xml` for ar/es/hi/de/fr/id/it/pt). Lint's `ExtraTranslation` check is fatal for release builds — never leave a string key in a locale file with no default-locale counterpart

## Build & Test
```bash
./gradlew :app:assembleDebug       # build debug
./gradlew :app:assembleRelease     # build release (minify + resource shrinking + proguard on)
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:installDebug        # install on device
```

## Creating a New Feature — Checklist
1. New package `ui/features/<feature>/` containing `<Feature>Screen.kt` (+ `<Feature>ViewModel.kt` if it needs state/repos)
2. ViewModel: `@HiltViewModel`, constructor-injects `domain/repository/` interfaces (or a `service/` singleton directly when there's no repository, e.g. `CallManager`), exposes `StateFlow<UiState>`
3. Add a route to `MainScreen` (in `ui/navigation/MainNavigation.kt`) or `OnboardingNavigation.kt`, then a `composable(MainScreen.X.route) { XScreen(...) }` block in the relevant `NavHost`
4. If it needs new persisted data, add a Room entity/DAO in `data/local/` and register it in `AppDatabase` + `DatabaseModule`; if it needs a new repository contract, add the interface to `domain/repository/`, the impl to `data/repository/`, and bind it in `di/RepositoryModule.kt`

## Known Rough Edges (don't treat as the pattern to follow)
- Some `service/` files still use raw `Thread`/`Thread.sleep` instead of coroutines
- `util/` mixes genuinely stateless helpers with stateful singletons — check before assuming a `util/` class is side-effect-free
- A few locales (`de`, `fr`, `id`, `it`, `pt`) are missing some newer string keys (e.g. `answer`, `end_call`) and silently fall back to English — known gap, not a crash risk
