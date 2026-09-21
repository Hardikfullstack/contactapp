package com.phone.contact.call.dialer.ads

import com.google.android.gms.ads.nativead.NativeAd

/**
 * Per-slot native ad cache for a scrollable list (Recents' inline ads) — keyed by a stable row id
 * (not the ad unit id), so scrolling a slot off-screen and back doesn't reload a fresh ad: the
 * LazyColumn recomposes/disposes that row's own composable as it scrolls, but the actual [NativeAd]
 * object lives here instead, independent of any one row's composition lifetime.
 *
 * Bounded (LRU-evicted past [MAX_ENTRIES]) and staleness-checked (like every other ad cache in
 * this app) so a long call-history list can't hold an unbounded number of live NativeAd objects,
 * and a slot loaded a long time ago but never actually scrolled into view again doesn't get
 * handed out once stale.
 */
object ListAdCache {
    private const val MAX_ENTRIES = 30

    // Same staleness guidance InterstitialAdManager/AppOpenAdManager/NativeAdCache already follow
    // — a row preloaded a couple of scroll-slots ahead but not actually scrolled into view for a
    // long time (list left open/backgrounded) shouldn't be handed out once it's sat this long.
    private const val EXPIRY_MS = 60 * 60 * 1000L

    private val ads = object : LinkedHashMap<String, NativeAd>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NativeAd>): Boolean {
            if (size <= MAX_ENTRIES) return false
            eldest.value.destroy()
            loadTimesMs.remove(eldest.key)
            return true
        }
    }
    private val loadTimesMs = mutableMapOf<String, Long>()

    /** Returns the cached ad for [key] if one is ready and hasn't expired — self-clears (and
     * destroys) a stale entry instead of handing it out, so the row falls back to loading fresh
     * the same way it would if nothing had ever been cached for this key. */
    fun get(key: String): NativeAd? {
        val loadedAt = loadTimesMs[key]
        if (ads.containsKey(key) && loadedAt != null && System.currentTimeMillis() - loadedAt > EXPIRY_MS) {
            ads.remove(key)?.destroy()
            loadTimesMs.remove(key)
            return null
        }
        return ads[key]
    }

    fun put(key: String, ad: NativeAd) {
        ads[key] = ad
        loadTimesMs[key] = System.currentTimeMillis()
    }
}
