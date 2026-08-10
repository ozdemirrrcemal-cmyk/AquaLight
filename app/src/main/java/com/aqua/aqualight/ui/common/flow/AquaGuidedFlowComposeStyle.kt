@file:Suppress("MagicNumber", "LongParameterList")

package com.aqua.aqualight.ui.common.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.R

/** Shared visual contract for focused, multi-step setup and calibration journeys. */
@Immutable
data class AquaGuidedFlowColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val secondaryButton: Color,
    val onSecondaryButton: Color,
    val disabled: Color,
    val onDisabled: Color,
    val danger: Color
)

@Immutable
data class AquaGuidedFlowTypography(
    val eyebrow: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val metric: TextStyle
)

object AquaGuidedFlowGeometry {
    val screenHorizontalPadding = 20.dp
    val screenBottomPadding = 32.dp
    val sectionGap = 20.dp
    val compactGap = 10.dp
    val cardRadius = 22.dp
    val cardPadding = 18.dp
    val outlineWidth = 1.dp
    val buttonRadius = 15.dp
    val buttonMinHeight = 52.dp
    val controlRadius = 14.dp
    val minimumTouchTarget = 48.dp
}

private val InterRegular = FontFamily(Font(R.font.inter_regular))
private val InterMedium = FontFamily(Font(R.font.inter_medium))
private val InterSemiBold = FontFamily(Font(R.font.inter_semibold))

@Composable
fun aquaGuidedFlowColors(): AquaGuidedFlowColors = AquaGuidedFlowColors(
    background = colorResource(R.color.background_color),
    surface = colorResource(R.color.aqua_card_device_surface),
    surfaceRaised = colorResource(R.color.aqua_card_device_media_surface),
    outline = colorResource(R.color.aqua_card_device_outline),
    textPrimary = colorResource(R.color.aqua_card_text_primary),
    textSecondary = colorResource(R.color.aqua_card_text_secondary),
    accent = colorResource(R.color.aqua_button_primary_container),
    onAccent = colorResource(R.color.aqua_button_primary_content),
    secondaryButton = colorResource(R.color.aqua_button_secondary_container),
    onSecondaryButton = colorResource(R.color.aqua_button_secondary_content),
    disabled = colorResource(R.color.aqua_button_disabled_container),
    onDisabled = colorResource(R.color.aqua_button_disabled_content),
    danger = colorResource(R.color.aqua_card_state_danger)
)

fun aquaGuidedFlowTypography(colors: AquaGuidedFlowColors) = AquaGuidedFlowTypography(
    eyebrow = TextStyle(
        color = colors.accent,
        fontFamily = InterSemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    ),
    title = TextStyle(
        color = colors.textPrimary,
        fontFamily = InterSemiBold,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    body = TextStyle(
        color = colors.textSecondary,
        fontFamily = InterRegular,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    label = TextStyle(
        color = colors.textPrimary,
        fontFamily = InterMedium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    button = TextStyle(
        fontFamily = InterSemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center
    ),
    metric = TextStyle(
        color = colors.textPrimary,
        fontFamily = InterSemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        textAlign = TextAlign.Center
    )
)

@Composable
fun AquaGuidedFlowSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = aquaGuidedFlowColors()
    val shape = RoundedCornerShape(AquaGuidedFlowGeometry.cardRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(AquaGuidedFlowGeometry.outlineWidth, colors.outline, shape)
            .padding(AquaGuidedFlowGeometry.cardPadding),
        content = content
    )
}

@Composable
fun AquaGuidedFlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false
) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    val container = when {
        !enabled -> colors.disabled
        secondary -> colors.secondaryButton
        else -> colors.accent
    }
    val content = when {
        !enabled -> colors.onDisabled
        secondary -> colors.onSecondaryButton
        else -> colors.onAccent
    }
    val shape = RoundedCornerShape(AquaGuidedFlowGeometry.buttonRadius)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = AquaGuidedFlowGeometry.buttonMinHeight)
            .clip(shape)
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text = text, style = typography.button.copy(color = content))
    }
}
