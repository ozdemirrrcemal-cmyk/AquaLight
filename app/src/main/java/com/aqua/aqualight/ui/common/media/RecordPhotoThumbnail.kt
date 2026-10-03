package com.aqua.aqualight.ui.common.media

import android.net.Uri
import android.widget.ImageView
import androidx.annotation.DrawableRes
import coil3.load
import coil3.request.crossfade
import coil3.request.fallback
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R

/** Explicitly replace the previous request, including when a photo is removed or a view is reused. */
fun ImageView.bindRecordPhoto(photoUri: String?) {
    bindRecordPhoto(photoUri, R.drawable.ic_camera_24)
}

fun ImageView.bindRecordPhoto(
    photoUri: String?,
    @DrawableRes placeholderRes: Int
) {
    clearColorFilter()
    val resolvedUri = photoUri?.takeIf(String::isNotBlank)?.let(Uri::parse)
    scaleType = if (resolvedUri == null) {
        ImageView.ScaleType.CENTER
    } else {
        ImageView.ScaleType.CENTER_CROP
    }
    load(resolvedUri) {
        placeholder(placeholderRes)
        fallback(placeholderRes)
        error(placeholderRes)
        crossfade(true)
    }
}
