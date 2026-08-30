package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.hero

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.aqua.aqualight.R

@Composable
internal fun rememberCoolingHeroArtwork(): ImageBitmap {
    val context = LocalContext.current
    return remember(context) {
        val encoded = buildString {
            HERO_REFERENCE_PARTS.forEach { resourceId ->
                context.resources.openRawResource(resourceId).bufferedReader().use { reader ->
                    append(reader.readText())
                }
            }
        }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)).asImageBitmap()
    }
}

private val HERO_REFERENCE_PARTS = intArrayOf(
    R.raw.cooling_hero_reference_1,
    R.raw.cooling_hero_reference_2,
    R.raw.cooling_hero_reference_3,
    R.raw.cooling_hero_reference_4,
    R.raw.cooling_hero_reference_5
)
