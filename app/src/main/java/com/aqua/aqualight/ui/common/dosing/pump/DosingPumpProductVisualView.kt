package com.aqua.aqualight.ui.common.dosing.pump

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.google.android.material.card.MaterialCardView
import java.util.WeakHashMap

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
 * Renders the exact Dosing-screen component at the same phone-width geometry and only then applies
 * one uniform scale transform. Fixed insets, corner radii, metal gradients and pump proportions are
 * therefore preserved instead of being recomputed inside an icon-sized constraint box.
 */
@Composable
private fun DosingPumpProductThumbnail(
    pumpCount: Int,
    modifier: Modifier = Modifier
) {
    val pumpHeads = remember(pumpCount) {
        List(pumpCount) { index ->
            DosingPumpHeadUiState(channelNumber = index + 1)
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val scale = (maxWidth.value / THUMBNAIL_SOURCE_WIDTH.value)
            .coerceIn(MIN_SCALE, NORMAL_SCALE)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            DosingPumpDevice(
                pumpHeads = pumpHeads,
                onPumpClick = null,
                modifier = Modifier
                    .requiredWidth(THUMBNAIL_SOURCE_WIDTH)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}

/**
 * Shared ViewBinding bridge. Dosing receives a wide, chrome-free media host so the canonical pump
 * body is visible as a product silhouette. Non-Dosing cards restore their original compact icon
 * container exactly, including width, background, stroke, radius and elevation.
 */
object DosingPumpProductVisualBinder {
    private val hostStates = WeakHashMap<ViewGroup, MediaHostState>()

    fun bind(
        container: ViewGroup,
        fallbackImageView: ImageView,
        isDosingProduct: Boolean,
        dosingChannelCount: Int?,
        @DrawableRes fallbackIconRes: Int,
        contentDescription: CharSequence
    ) {
        bind(
            pumpView = container.obtainPumpVisualView(),
            fallbackImageView = fallbackImageView,
            isDosingProduct = isDosingProduct,
            dosingChannelCount = dosingChannelCount,
            fallbackIconRes = fallbackIconRes,
            contentDescription = contentDescription
        )
    }

    fun bind(
        pumpView: DosingPumpProductVisualView,
        fallbackImageView: ImageView,
        isDosingProduct: Boolean,
        dosingChannelCount: Int?,
        @DrawableRes fallbackIconRes: Int,
        contentDescription: CharSequence
    ) {
        val mediaHost = pumpView.parent as? ViewGroup
        mediaHost?.applyProductVisualHost(isDosingProduct)

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

    private fun ViewGroup.applyProductVisualHost(isDosingProduct: Boolean) {
        val state = hostStates.getOrPut(this) { captureMediaHostState() }
        layoutParams = layoutParams.apply {
            width = if (isDosingProduct) {
                resources.getDimensionPixelSize(R.dimen.aqua_size_96)
            } else {
                state.width
            }
        }

        val card = this as? MaterialCardView ?: return
        if (isDosingProduct) {
            card.setCardBackgroundColor(
                ContextCompat.getColor(card.context, R.color.aqua_palette_hex_00000000)
            )
            card.strokeWidth = NO_STROKE_WIDTH
            card.radius = NO_CORNER_RADIUS
            card.cardElevation = NO_CARD_ELEVATION
        } else {
            card.setCardBackgroundColor(state.cardBackgroundColor)
            card.setStrokeColor(state.strokeColor)
            card.strokeWidth = state.strokeWidth
            card.radius = state.radius
            card.cardElevation = state.cardElevation
        }
    }

    private fun ViewGroup.captureMediaHostState(): MediaHostState {
        val card = this as? MaterialCardView
        val transparent = ContextCompat.getColor(context, R.color.aqua_palette_hex_00000000)
        return MediaHostState(
            width = layoutParams.width,
            cardBackgroundColor = card?.cardBackgroundColor ?: ColorStateList.valueOf(transparent),
            strokeColor = card?.strokeColor ?: transparent,
            strokeWidth = card?.strokeWidth ?: NO_STROKE_WIDTH,
            radius = card?.radius ?: NO_CORNER_RADIUS,
            cardElevation = card?.cardElevation ?: NO_CARD_ELEVATION
        )
    }
}

private fun ViewGroup.obtainPumpVisualView(): DosingPumpProductVisualView {
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child is DosingPumpProductVisualView) return child
    }

    return DosingPumpProductVisualView(context).also { pumpView ->
        pumpView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        pumpView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        pumpView.visibility = View.GONE
        addView(pumpView, 0)
    }
}

private data class MediaHostState(
    val width: Int,
    val cardBackgroundColor: ColorStateList,
    val strokeColor: Int,
    val strokeWidth: Int,
    val radius: Float,
    val cardElevation: Float
)

internal fun isSupportedDosingPumpCount(value: Int): Boolean =
    value == DOSING_PRO_2_PUMP_COUNT || value == DOSING_PRO_4_PUMP_COUNT

private const val UNBOUND_PUMP_COUNT = 0
private const val DOSING_PRO_2_PUMP_COUNT = 2
private const val DOSING_PRO_4_PUMP_COUNT = 4
private const val MIN_SCALE = 0f
private const val NORMAL_SCALE = 1f
private const val THUMBNAIL_SOURCE_WIDTH_DP = 360
private const val NO_STROKE_WIDTH = 0
private const val NO_CORNER_RADIUS = 0f
private const val NO_CARD_ELEVATION = 0f
private val THUMBNAIL_SOURCE_WIDTH = THUMBNAIL_SOURCE_WIDTH_DP.dp
