package com.aqua.aqualight.ui.common.dosing.pump

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp

/** XML/ViewBinding host for the one shared Compose-drawn Dose Pro product visual. */
class DosingPumpProductVisualView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

    private var pumpCount by mutableIntStateOf(UNBOUND_PUMP_COUNT)

    fun bindPumpCount(value: Int) {
        pumpCount = value.takeIf(::isSupportedDosingPumpCount) ?: UNBOUND_PUMP_COUNT
        visibility = if (pumpCount == UNBOUND_PUMP_COUNT) View.GONE else View.VISIBLE
    }

    @Composable
    override fun Content() {
        val exactPumpCount = pumpCount.takeIf(::isSupportedDosingPumpCount) ?: return
        DosingPumpProductThumbnail(
            pumpCount = exactPumpCount,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Adapts the existing full-size pump device to icon-sized hosts without changing its drawing code.
 * The component lays out at a stable source width and is then uniformly scaled by its host width.
 */
@Composable
private fun DosingPumpProductThumbnail(
    pumpCount: Int,
    modifier: Modifier = Modifier
) {
    val sourceWidth = if (pumpCount == DOSING_PRO_2_PUMP_COUNT) {
        DOSING_PRO_2_THUMBNAIL_SOURCE_WIDTH
    } else {
        DOSING_PRO_4_THUMBNAIL_SOURCE_WIDTH
    }
    val pumpHeads = remember(pumpCount) {
        List(pumpCount) { index ->
            DosingPumpHeadUiState(channelNumber = index + 1)
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val scale = (maxWidth.value / sourceWidth.value).coerceIn(MIN_SCALE, NORMAL_SCALE)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            DosingPumpDevice(
                pumpHeads = pumpHeads,
                onPumpClick = null,
                modifier = Modifier
                    .requiredWidth(sourceWidth)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}

/** Shared ViewBinding bridge. Dosing never falls back to the legacy four-head bitmap. */
object DosingPumpProductVisualBinder {
    fun bind(
        pumpView: DosingPumpProductVisualView,
        fallbackImageView: ImageView,
        dosingChannelCount: Int?,
        @DrawableRes fallbackIconRes: Int,
        contentDescription: CharSequence
    ) {
        val isDosingProduct = dosingChannelCount != null
        val exactPumpCount = dosingChannelCount?.takeIf(::isSupportedDosingPumpCount)

        pumpView.bindPumpCount(exactPumpCount ?: UNBOUND_PUMP_COUNT)
        pumpView.contentDescription = contentDescription
        fallbackImageView.visibility = if (isDosingProduct) View.GONE else View.VISIBLE

        if (!isDosingProduct) {
            fallbackImageView.setImageResource(fallbackIconRes)
            fallbackImageView.imageTintList = null
            fallbackImageView.clearColorFilter()
            fallbackImageView.contentDescription = contentDescription
        }
    }
}

private fun isSupportedDosingPumpCount(value: Int): Boolean =
    value == DOSING_PRO_2_PUMP_COUNT || value == DOSING_PRO_4_PUMP_COUNT

private const val UNBOUND_PUMP_COUNT = 0
private const val DOSING_PRO_2_PUMP_COUNT = 2
private const val DOSING_PRO_4_PUMP_COUNT = 4
private const val MIN_SCALE = 0f
private const val NORMAL_SCALE = 1f
private const val DOSING_PRO_2_THUMBNAIL_SOURCE_WIDTH_DP = 144
private const val DOSING_PRO_4_THUMBNAIL_SOURCE_WIDTH_DP = 240
private val DOSING_PRO_2_THUMBNAIL_SOURCE_WIDTH = DOSING_PRO_2_THUMBNAIL_SOURCE_WIDTH_DP.dp
private val DOSING_PRO_4_THUMBNAIL_SOURCE_WIDTH = DOSING_PRO_4_THUMBNAIL_SOURCE_WIDTH_DP.dp
