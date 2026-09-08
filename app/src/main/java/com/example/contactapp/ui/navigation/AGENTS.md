

## Navigation (`ui/navigation/`)

Two files, plain Jetpack **Navigation-Compose** — no custom DSL.

- `MainNavigation.kt` — `MainScreen` sealed hierarchy (`object`/`class` per route, each with
  `.route`, some with `.createRoute(...)` for arguments, `.labelRes`/`.icon` for the bottom-bar
  entries). `MainNavigation(...)` composable builds a `Scaffold` with `CommonBottomBar` (+ a
  `BannerAdView` under it when an ad unit id is available) and a `NavHost(navController, startDestination) { composable(MainScreen.X.route) { XScreen(...) } }` block per screen. Bottom bar
  visibility/tab state is derived from `navController.currentBackStackEntryAsState()` in this same
  file — not delegated to each feature
- `OnboardingNavigation.kt` — `OnboardingNavHost(...)`, same `NavHost`/`composable()` pattern, for
  the pre-main-app onboarding flow (permissions, language selection, etc.)
- Every destination change is logged via `AnalyticsManager.logScreenView(...)` from a
  `DisposableEffect` `OnDestinationChangedListener` in `MainNavigation`
- Screen composables receive navigation as callbacks (`onContactClick: (String, String) -> Unit`,
  `onSearchClick: () -> Unit`, ...) that `MainNavigation`/`OnboardingNavigation` wire to
  `navController.navigate(...)`/`popBackStack()` — a feature screen never touches `NavController` directly

### Adding a New Route
```kotlin
// 1. In MainScreen (MainNavigation.kt), add the route:
object MyFeature : MainScreen("my_feature")

// 2. Add the composable() block in the NavHost:
composable(MainScreen.MyFeature.route) {
    MyFeatureScreen(onBack = { navController.popBackStack() })
}

// 3. If it needs a bottom-bar entry, add it to navItems and give MyFeature labelRes/icon
```

### Anti-Patterns
- ❌ A feature screen calling `navController.navigate(...)` directly — pass a callback in from `ui/navigation/` instead
- ❌ Duplicating route strings — always go through the `MainScreen`/route sealed object
- ❌ Forgetting the `AnalyticsManager.logScreenView` listener still fires on any new route — no action needed, but don't assume screen-view analytics need separate wiring per feature
