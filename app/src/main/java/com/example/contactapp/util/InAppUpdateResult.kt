package com.example.contactapp.util

/**
 * Carries the outcome of the IMMEDIATE in-app-update flow's launched activity back from
 * MainActivity.onActivityResult — a Composable can't override that itself. Nothing currently
 * *acts* on a cancelled/failed result beyond logging it (our own update dialog stays up
 * regardless, since it isn't dismissed on tapping "Update", so the user lands back on it
 * naturally either way) — this exists so that outcome is at least visible in analytics instead
 * of being silently dropped, per Google's own guidance to track it.
 */
object InAppUpdateResult {
    const val REQUEST_CODE = 999

    /** One-shot: set by MainActivity, consumed (and cleared) by the first collector that reads it. */
    var pendingResultCode: Int? = null
}
