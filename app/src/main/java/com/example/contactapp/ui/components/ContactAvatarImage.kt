package com.example.contactapp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * Loads a contact's photo the same way `AsyncImage(model = uri)` would, except with Coil's
 * memory and disk caching disabled for it.
 *
 * Contact photo URIs (PHOTO_URI / PHOTO_THUMBNAIL_URI) are stable *per contact* — the same
 * content:// URI string is reused every time that contact's photo changes — but Coil's default
 * cache keys purely on that URI string. So once loaded, Coil keeps serving the old cached bytes
 * indefinitely even after the underlying photo is updated (e.g. synced down from Google), since
 * there's no signal in the URI itself that anything changed. Contact thumbnails are small, so
 * re-decoding on each load is cheap — this trades a negligible amount of performance for always
 * showing the current photo instead of a stale one.
 */
@Composable
fun ContactAvatarImage(
    photoUri: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val request = remember(photoUri) {
        ImageRequest.Builder(context)
            .data(photoUri)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale
    )
}
