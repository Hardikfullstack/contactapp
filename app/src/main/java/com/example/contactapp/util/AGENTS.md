## Utility Helpers (`util/`)

Despite the name, this package is a mix of two different things — check which kind a file is
before assuming it's side-effect-free:

1. **Genuinely stateless helpers**: `CallUtils`, `ColorUtils`, `QrUtils`, `MessageUtils`,
   `SimpleVcfParser`, `WallpaperLuminance` — pure functions / format converters, safe to call from
   anywhere without lifecycle concerns
2. **Stateful `object` singletons**: `PreferenceManager` (SharedPreferences-backed app settings,
   exposes `Flow`s like `sortOrderFlow`), `AnalyticsManager` (Firebase Analytics event logging incl.
   `logAdEvent`/`logScreenView`), `NetworkMonitor`, `LocaleChangeState`, `CrashlyticsManager`,
   `AlarmScheduler`, `RateUsHelper`, `AppUpdateHelper` — these hold real app-wide state or wrap a
   platform service, and are consumed the same way `service/` singletons are (`Foo.bar()`)

Other real files here: `AfterCallMiniOverlay`, `AfterCallNotificationHelper`, `AfterCallState`,
`BuiltInWallpapers`, `CallReliabilityUtils`, `CallReminder`, `CallThemes`, `ContactCallBackgroundManager`,
`LocalBlockManager`, `OverlayLifecycleOwner`, `SpamDetector`, `WallpaperSelection`.

### Rules
- New utility → new file (one concern per file); avoid growing a monolithic helper file
- A stateless helper must stay pure — no hidden `Context`-scoped side effects; if it needs one, it
  belongs in the "stateful singleton" half of this package instead, named `<Thing>Manager`/`<Thing>Helper`
- Business logic that's specific to one feature screen belongs in that feature's ViewModel, not here

### Adding a New Utility
```kotlin
// Pure extension function
fun String.normalizePhoneNumber(): String = replace(Regex("[^0-9]"), "").takeLast(10)

// Stateful singleton (only if genuinely app-wide)
object MyManager {
    fun start(context: Context) { /* ... */ }
}
```

### Anti-Patterns
- ❌ Phone-number normalization duplicated ad hoc instead of reusing the existing pattern (see `ContactRepositoryImpl`'s use of `Regex("[^0-9]").takeLast(10)`)
- ❌ Adding feature-specific logic to a shared `util/` file instead of that feature's own ViewModel
- ❌ Treating everything in this package as side-effect-free — several files here are stateful singletons
