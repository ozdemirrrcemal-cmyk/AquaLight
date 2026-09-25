package com.aqua.aqualight.ui.common.media

import android.net.Uri
import android.widget.ImageView
import coil3.load
import coil3.request.crossfade
import coil3.request.fallback
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R

/** Explicitly replace the previous request, including when a photo is removed or a view is reused. */
fun ImageView.bindRecordPhoto(photoUri: String?) {
    clearColorFilter()
    scaleType = if (photoUri.isNullOrBlank()) ImageView.ScaleType.CENTER else ImageView.ScaleType.CENTER_CROP
    load(photoUri?.takeIf(String::isNotBlank)?.let(Uri::parse)) {
        placeholder(R.drawable.ic_camera_24)
        fallback(R.drawable.ic_camera_24)
        error(R.drawable.ic_camera_24)
        crossfade(true)
    }
}
