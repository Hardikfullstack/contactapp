## Ads Layer (`ads/`)

Google Mobile Ads (AdMob) wrappers. Real files: `AppOpenAdManager`, `AppOpenBackgroundReturnTrigger`,
`AppOpenCounter`, `InterstitialAdManager`, `BannerAdCache`, `BannerAdView`, `NativeAdCache`,
`NativeAdView`, plus `AdLoadingLottie`/`AdShimmer`/`AdWaiting` loading-state composables.

### Rules
- Ad unit IDs come from the remote config fetched via `data/network/ApiClient` (see
  `viewmodel/AppConfigViewModel.kt`), not hardcoded — callers pass `adUnitId: String?` and skip
  rendering when it's null
- `NativeAdCache` / `BannerAdCache` are `object` singletons that **preload** an ad ahead of the
  screen that needs it composing (e.g. `SplashScreen` preloads for a screen the user hasn't opened
  yet); `take(adUnitId)` consumes the cached ad — a `NativeAd` can only be bound to one view, so it's
  removed from the cache once handed out
- If nothing is cached, ad views (`NativeAdView`, `BannerAdView`) fall back to loading on-render —
  the cache is a head start, not a requirement
- Every load attempt/success/failure is logged via `AnalyticsManager.logAdEvent(type, adUnitId, event)`

### Adding a New Ad Placement
```kotlin
// 1. Load (or preload via the relevant *Cache object)
val adLoader = AdLoader.Builder(context, adUnitId)
    .forNativeAd { ad -> /* cache or bind */ }
    .withAdListener(object : AdListener() {
        override fun onAdFailedToLoad(error: LoadAdError) { /* log + fall back gracefully */ }
    })
    .build()
adLoader.loadAd(AdRequest.Builder().build())

// 2. Always have a no-ad fallback — ad unit id can be null/absent from remote config
```

### Anti-Patterns
- ❌ Hardcoding an ad unit ID instead of reading it from the remote config
- ❌ Blocking a screen's content on an ad load — ads are additive, never gate core functionality
- ❌ Rebinding a `NativeAd` already handed out by `take()` — request/cache a new one instead
