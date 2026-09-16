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
`LocalBlockManager`, `PhoneNumberMatcher`, `SpamDetector`, `OverlayLifecycleOwner`, `WallpaperSelection`.

### Rules
- New utility → new file (one concern per file); avoid growing a monolithic helper file
- A stateless helper must stay pure — no hidden `Context`-scoped side effects; if it needs one, it
  belongs in the "stateful singleton" half of this package instead, named `<Thing>Manager`/`<Thing>Helper`
- Business logic that's specific to one feature screen belongs in that feature's ViewModel, not here

### Adding a New Utility
```kotlin
// Pure extension function
fun String.shout(): String = uppercase() + "!"

// Stateful singleton (only if genuinely app-wide)
object MyManager {
    fun start(context: Context) { /* ... */ }
}
```

### Phone-number matching
Every place that groups/compares two phone numbers (blocking, spam detection, favorites, call
history, per-contact wallpaper, contact dedup) must go through **`PhoneNumberMatcher.normalize()`**
— never re-derive a normalization key ad hoc. It keys off the last 7 significant digits (matching
`android.telephony.PhoneNumberUtils`'s own `MIN_MATCH` constant), not a fixed national number
length like 10 — a hardcoded 10 only matches India/US-style numbering and silently mismatches an
11-digit China number or a 9-digit Gulf-state one. The one exception is `CallLogRepositoryImpl
.blockNumber()`'s fallback `BlockedNumberContract.unblock(context, last10)` call — that's a real
argument to a system API attempting a specific reconstructed number, not an internal matching key,
so it deliberately keeps its own last-10 truncation.

### Anti-Patterns
- ❌ Phone-number normalization duplicated ad hoc instead of `PhoneNumberMatcher.normalize()`
- ❌ Adding feature-specific logic to a shared `util/` file instead of that feature's own ViewModel
- ❌ Treating everything in this package as side-effect-free — several files here are stateful singletons
