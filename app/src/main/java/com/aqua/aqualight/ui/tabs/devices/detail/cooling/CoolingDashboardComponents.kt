@file:Suppress(
    "LongMethod",
    "MagicNumber",
    "MatchingDeclarationName",
    "MaxLineLength",
    "TooManyFunctions"
)

package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingPalette
import com.aqua.aqualight.ui.common.cooling.aquaCoolingTextStyle
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun CoolingAquariumHero(
    fanRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val motion = rememberInfiniteTransition(label = "cooling-hero-motion")
    val fanRotation by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = FAN_ROTATION_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cooling-fan-rotation"
    )
    val waterPhase by motion.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = WATER_WAVE_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cooling-water-phase"
    )
    val shape = RoundedCornerShape(AquaDeviceCardGeometry.cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AquaCoolingGeometry.heroHeight)
            .clip(shape)
            .background(AquaCoolingPalette.surface)
            .border(1.dp, AquaCoolingPalette.outline, shape)
    ) {
        Image(
            painter = painterResource(R.drawable.cooling_dashboard_aquarium),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x2B00101D),
                        0.55f to Color.Transparent,
                        1f to Color(0x65000A12)
                    )
                )
        )
        CoolingWaterOverlay(
            fanRunning = fanRunning,
            phase = if (fanRunning) waterPhase else 0f,
            modifier = Modifier.fillMaxSize()
        )
        CoolingFanAssembly(
            rotationDegrees = if (fanRunning) fanRotation else 0f,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 7.dp, top = 11.dp)
                .width(112.dp)
                .height(84.dp)
        )
        CoolingSensorBadge(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 92.dp, y = (-24).dp)
        )
    }
}

@Composable
private fun CoolingWaterOverlay(
    fanRunning: Boolean,
    phase: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val top = size.height * 0.070f
        repeat(WATER_LINE_COUNT) { index ->
            val path = Path()
            val linePhase = phase + index * 0.62f
            val wave = sin(linePhase) * if (fanRunning) 4.5f else 1.5f
            val y = top + index * size.height * 0.017f + wave
            path.moveTo(0f, y)
            path.cubicTo(
                size.width * 0.18f,
                y - 6f - cos(linePhase) * 3f,
                size.width * 0.34f,
                y + 7f + sin(linePhase + 0.8f) * 3f,
                size.width * 0.51f,
                y
            )
            path.cubicTo(
                size.width * 0.68f,
                y - 5f - sin(linePhase + 1.4f) * 3f,
                size.width * 0.85f,
                y + 6f + cos(linePhase + 0.3f) * 3f,
                size.width + 12f,
                y + sin(linePhase + 2.1f) * 2f
            )
            drawPath(
                path = path,
                color = Color(0xFF9BC8FF).copy(alpha = 0.66f - index * 0.09f),
                style = Stroke(width = 1.05.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        if (!fanRunning) return@Canvas
        repeat(AIRFLOW_LINE_COUNT) { index ->
            val path = Path()
            val startX = size.width * 0.210f
            val startY = size.height * (0.300f + index * 0.014f)
            path.moveTo(startX, startY)
            path.cubicTo(
                size.width * 0.26f,
                size.height * (0.36f + index * 0.017f),
                size.width * 0.25f,
                size.height * (0.54f + index * 0.011f),
                size.width * 0.430f,
                size.height * (0.62f + index * 0.009f)
            )
            drawPath(
                path = path,
                color = AquaCoolingPalette.accent.copy(alpha = 0.18f),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = path,
                color = Color(0xFF1E70FF).copy(alpha = 0.82f - index * 0.09f),
                style = Stroke(
                    width = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(18f, 7f),
                        phase = -phase * 18f - index * 5f
                    )
                )
            )
        }
    }
}

@Composable
private fun CoolingFanAssembly(
    rotationDegrees: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val housingWidth = size.width * 0.78f
        val housingHeight = size.height * 0.88f
        val housingTop = size.height * 0.05f
        val center = Offset(housingWidth * 0.50f, housingTop + housingHeight * 0.52f)
        val fanRadius = housingHeight * 0.39f

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF4B5561), Color(0xFF171D24), Color(0xFF080B0F))
            ),
            topLeft = Offset(0f, housingTop),
            size = Size(housingWidth, housingHeight),
            cornerRadius = CornerRadius(8.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF77828F).copy(alpha = 0.65f),
            topLeft = Offset(1.dp.toPx(), housingTop + 1.dp.toPx()),
            size = Size(housingWidth - 2.dp.toPx(), housingHeight - 2.dp.toPx()),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF11161C), Color(0xFF242C35), Color(0xFF070A0E))
            ),
            topLeft = Offset(housingWidth * 0.76f, housingTop + housingHeight * 0.20f),
            size = Size(size.width * 0.23f, housingHeight * 0.62f),
            cornerRadius = CornerRadius(7.dp.toPx())
        )

        drawCircle(color = Color(0xFF05080C), radius = fanRadius * 1.06f, center = center)
        drawCircle(
            color = Color(0xFF3DE6FF).copy(alpha = 0.32f),
            radius = fanRadius * 1.01f,
            center = center,
            style = Stroke(width = 5.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF43E6FF),
            radius = fanRadius * 0.99f,
            center = center,
            style = Stroke(width = 1.3.dp.toPx())
        )

        withTransform({ rotate(degrees = rotationDegrees, pivot = center) }) {
            repeat(FAN_BLADE_COUNT) { bladeIndex ->
                rotate(degrees = bladeIndex * (360f / FAN_BLADE_COUNT), pivot = center) {
                    drawOval(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF4D5964), Color(0xFF080B0F))
                        ),
                        topLeft = Offset(
                            center.x - fanRadius * 0.19f,
                            center.y - fanRadius * 0.82f
                        ),
                        size = Size(fanRadius * 0.38f, fanRadius * 0.72f)
                    )
                }
            }
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF46515C), Color(0xFF0A0D12)),
                center = center,
                radius = fanRadius * 0.34f
            ),
            radius = fanRadius * 0.30f,
            center = center
        )
        drawCircle(
            color = Color(0xFF687480),
            radius = fanRadius * 0.30f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        val boltInset = housingHeight * 0.13f
        listOf(
            Offset(boltInset, housingTop + boltInset),
            Offset(housingWidth - boltInset, housingTop + boltInset),
            Offset(boltInset, housingTop + housingHeight - boltInset),
            Offset(housingWidth - boltInset, housingTop + housingHeight - boltInset)
        ).forEach { boltCenter ->
            drawCircle(color = Color(0xFF080A0D), radius = 4.dp.toPx(), center = boltCenter)
            drawCircle(
                color = Color(0xFF65707A),
                radius = 4.dp.toPx(),
                center = boltCenter,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

@Composable
private fun CoolingSensorBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .widthIn(min = 112.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xC8071421))
            .border(1.dp, Color(0xFF536172), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0xFF657184), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CoolingGlyph(
                type = CoolingGlyphType.SNOWFLAKE,
                color = AquaCoolingPalette.textPrimary,
                modifier = Modifier.size(15.dp)
            )
        }
        Column(modifier = Modifier.padding(start = 7.dp)) {
            BasicText(
                text = stringResource(R.string.device_cooling_temperature_sensor),
                style = aquaCoolingTextStyle(
                    size = 8.sp,
                    lineHeight = 10.sp,
                    color = AquaCoolingPalette.textPrimary
                )
            )
            BasicText(
                text = stringResource(R.string.device_cooling_current_temperature_value),
                modifier = Modifier.padding(top = 2.dp),
                style = aquaCoolingTextStyle(
                    size = 13.sp,
                    lineHeight = 16.sp,
                    color = AquaCoolingPalette.textPrimary,
                    semiBold = true
                )
            )
        }
    }
}

@Composable
internal fun CoolingFanGaugeCard(modifier: Modifier = Modifier) {
    CoolingDashboardCard(modifier = modifier) {
        CoolingCardTitle(text = stringResource(R.string.device_cooling_fan_speed))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(76.dp)) {
                val stroke = 8.dp.toPx()
                drawArc(
                    color = AquaCoolingPalette.disabled,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            AquaCoolingPalette.cyan,
                            AquaCoolingPalette.accent,
                            AquaCoolingPalette.accent
                        )
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * PLACEHOLDER_FAN_FRACTION,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    text = stringResource(R.string.device_cooling_fan_percent),
                    style = aquaCoolingTextStyle(
                        size = 20.sp,
                        lineHeight = 24.sp,
                        color = AquaCoolingPalette.textPrimary,
                        semiBold = true
                    )
                )
                BasicText(
                    text = stringResource(R.string.device_cooling_mode_automatic),
                    modifier = Modifier.padding(top = 1.dp),
                    style = aquaCoolingTextStyle(
                        size = 9.sp,
                        lineHeight = 12.sp,
                        color = AquaCoolingPalette.cyan
                    )
                )
            }
        }
    }
}

@Composable
internal fun CoolingFanModeCard(modifier: Modifier = Modifier) {
    CoolingDashboardCard(modifier = modifier) {
        CoolingCardTitle(text = stringResource(R.string.device_cooling_fan_mode))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CoolingModeOption(
                text = stringResource(R.string.device_cooling_mode_automatic),
                selected = true
            )
            CoolingModeOption(
                text = stringResource(R.string.device_cooling_mode_manual),
                selected = false
            )
            CoolingModeOption(
                text = stringResource(R.string.device_cooling_mode_program),
                selected = false
            )
        }
    }
}

@Composable
private fun CoolingModeOption(text: String, selected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF0D2133) else Color.Transparent)
            .border(
                1.dp,
                if (selected) Color(0xFF405267) else AquaCoolingPalette.outlineSoft,
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) {
                        AquaCoolingPalette.accent
                    } else {
                        AquaCoolingPalette.textMuted
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AquaCoolingPalette.accent)
                )
            }
        }
        BasicText(
            text = text,
            modifier = Modifier.padding(start = 7.dp),
            style = aquaCoolingTextStyle(
                size = 10.sp,
                lineHeight = 13.sp,
                color = AquaCoolingPalette.textPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun CoolingPowerCard(modifier: Modifier = Modifier) {
    CoolingDashboardCard(modifier = modifier) {
        CoolingCardTitle(text = stringResource(R.string.device_cooling_power))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(AquaCoolingPalette.surfaceRaised)
                        .border(1.dp, AquaCoolingPalette.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CoolingGlyph(
                        type = CoolingGlyphType.LIGHTNING,
                        color = AquaCoolingPalette.textPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                BasicText(
                    text = stringResource(R.string.device_cooling_power_value),
                    modifier = Modifier.padding(start = 6.dp),
                    style = aquaCoolingTextStyle(
                        size = 13.sp,
                        lineHeight = 16.sp,
                        color = AquaCoolingPalette.textPrimary,
                        semiBold = true
                    )
                )
            }
            Column(modifier = Modifier.padding(top = 10.dp)) {
                BasicText(
                    text = stringResource(R.string.device_cooling_estimated_consumption),
                    style = aquaCoolingTextStyle(
                        size = 9.sp,
                        lineHeight = 12.sp,
                        color = AquaCoolingPalette.textSecondary
                    )
                )
                BasicText(
                    text = stringResource(R.string.device_cooling_consumption_value),
                    modifier = Modifier.padding(top = 2.dp),
                    style = aquaCoolingTextStyle(
                        size = 13.sp,
                        lineHeight = 16.sp,
                        color = AquaCoolingPalette.textPrimary
                    )
                )
            }
        }
    }
}

@Composable
internal fun CoolingStatusCard(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    CoolingDashboardCard(modifier = modifier) {
        CoolingCardTitle(text = stringResource(R.string.device_cooling_status))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CoolingStatusRow(
                glyph = CoolingGlyphType.FAN,
                label = stringResource(R.string.device_cooling_status_fan),
                value = stringResource(R.string.device_cooling_status_running),
                success = true
            )
            CoolingStatusRow(
                glyph = CoolingGlyphType.SENSOR,
                label = stringResource(R.string.device_cooling_status_sensors),
                value = stringResource(R.string.device_cooling_status_normal),
                success = true
            )
            CoolingStatusRow(
                glyph = CoolingGlyphType.LINK,
                label = stringResource(R.string.device_cooling_status_connection),
                value = stringResource(
                    if (isOnline) {
                        R.string.device_cooling_status_online
                    } else {
                        R.string.device_cooling_status_offline
                    }
                ),
                success = isOnline
            )
            CoolingStatusRow(
                glyph = CoolingGlyphType.ALARM,
                label = stringResource(R.string.device_cooling_status_alarm),
                value = stringResource(R.string.device_cooling_status_none),
                success = true
            )
        }
    }
}

@Composable
private fun CoolingStatusRow(
    glyph: CoolingGlyphType,
    label: String,
    value: String,
    success: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoolingGlyph(
            type = glyph,
            color = AquaCoolingPalette.textPrimary,
            modifier = Modifier.size(13.dp)
        )
        BasicText(
            text = label,
            modifier = Modifier
                .padding(start = 6.dp)
                .weight(1f),
            style = aquaCoolingTextStyle(
                size = 9.sp,
                lineHeight = 12.sp,
                color = AquaCoolingPalette.textSecondary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = value,
            modifier = Modifier.padding(start = 5.dp),
            style = aquaCoolingTextStyle(
                size = 9.sp,
                lineHeight = 12.sp,
                color = if (success) {
                    AquaCoolingPalette.success
                } else {
                    AquaCoolingPalette.textMuted
                },
                textAlign = TextAlign.End
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun CoolingProfileCard(modifier: Modifier = Modifier) {
    CoolingDashboardCard(modifier = modifier) {
        CoolingCardTitle(text = stringResource(R.string.device_cooling_profile))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoolingProfileChip(
                text = stringResource(R.string.device_cooling_profile_quiet),
                glyph = CoolingGlyphType.MOON,
                selected = false,
                modifier = Modifier.weight(1f)
            )
            CoolingProfileChip(
                text = stringResource(R.string.device_cooling_profile_balanced),
                glyph = CoolingGlyphType.BALANCE,
                selected = true,
                modifier = Modifier.weight(1f)
            )
            CoolingProfileChip(
                text = stringResource(R.string.device_cooling_profile_performance),
                glyph = CoolingGlyphType.FAN,
                selected = false,
                modifier = Modifier.weight(1f)
            )
            CoolingProfileChip(
                text = stringResource(R.string.device_cooling_profile_boost),
                glyph = CoolingGlyphType.ROCKET,
                selected = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CoolingProfileChip(
    text: String,
    glyph: CoolingGlyphType,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (selected) AquaCoolingPalette.accent.copy(alpha = 0.82f) else Color.Transparent
            )
            .border(
                1.dp,
                if (selected) AquaCoolingPalette.accent else AquaCoolingPalette.outline,
                RoundedCornerShape(13.dp)
            )
            .padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CoolingGlyph(
            type = glyph,
            color = AquaCoolingPalette.textPrimary,
            modifier = Modifier.size(16.dp)
        )
        BasicText(
            text = text,
            modifier = Modifier.padding(top = 2.dp),
            style = aquaCoolingTextStyle(
                size = 8.sp,
                lineHeight = 10.sp,
                color = AquaCoolingPalette.textPrimary,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun CoolingManualFanCard(modifier: Modifier = Modifier) {
    CoolingDashboardCard(modifier = modifier) {
        CoolingCardTitle(text = stringResource(R.string.device_cooling_manual_fan_speed))
        BasicText(
            text = stringResource(R.string.device_cooling_manual_only),
            modifier = Modifier.padding(top = 1.dp),
            style = aquaCoolingTextStyle(
                size = 8.sp,
                lineHeight = 10.sp,
                color = AquaCoolingPalette.textSecondary
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.Bottom
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_range_minimum),
                modifier = Modifier.padding(bottom = 2.dp),
                style = aquaCoolingTextStyle(
                    size = 9.sp,
                    lineHeight = 12.sp,
                    color = AquaCoolingPalette.textSecondary
                )
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText(
                    text = stringResource(R.string.device_cooling_fan_percent),
                    style = aquaCoolingTextStyle(
                        size = 10.sp,
                        lineHeight = 13.sp,
                        color = AquaCoolingPalette.textPrimary,
                        semiBold = true
                    )
                )
                CoolingDisabledSlider(
                    progress = PLACEHOLDER_FAN_FRACTION,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                )
            }
            BasicText(
                text = stringResource(R.string.device_cooling_range_maximum),
                modifier = Modifier.padding(bottom = 2.dp),
                style = aquaCoolingTextStyle(
                    size = 9.sp,
                    lineHeight = 12.sp,
                    color = AquaCoolingPalette.textSecondary
                )
            )
        }
    }
}

@Composable
private fun CoolingDisabledSlider(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val radius = 6.dp.toPx()
        val startX = radius
        val endX = size.width - radius
        val thumbX = startX + (endX - startX) * progress
        drawLine(
            color = AquaCoolingPalette.disabled,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(AquaCoolingPalette.accent, AquaCoolingPalette.cyan)
            ),
            start = Offset(startX, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = AquaCoolingPalette.textPrimary,
            radius = radius,
            center = Offset(thumbX, centerY)
        )
    }
}

@Composable
internal fun CoolingDashboardCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        AquaDeviceCardGeometry.compactContentPadding
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    AquaDeviceCardSurface(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        Column(modifier = Modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun CoolingCardTitle(text: String) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    BasicText(
        text = text,
        style = typography.compactTitle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun CoolingGlyph(
    type: CoolingGlyphType,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = 1.35.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.38f
        when (type) {
            CoolingGlyphType.SNOWFLAKE -> {
                repeat(3) { index ->
                    val angle = Math.toRadians((index * 60).toDouble())
                    val dx = cos(angle).toFloat() * radius
                    val dy = sin(angle).toFloat() * radius
                    drawLine(
                        color = color,
                        start = Offset(center.x - dx, center.y - dy),
                        end = Offset(center.x + dx, center.y + dy),
                        strokeWidth = width,
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(color = color, radius = width * 0.8f, center = center)
            }

            CoolingGlyphType.LIGHTNING -> {
                val path = Path().apply {
                    moveTo(size.width * 0.56f, size.height * 0.07f)
                    lineTo(size.width * 0.22f, size.height * 0.56f)
                    lineTo(size.width * 0.48f, size.height * 0.54f)
                    lineTo(size.width * 0.38f, size.height * 0.94f)
                    lineTo(size.width * 0.79f, size.height * 0.42f)
                    lineTo(size.width * 0.54f, size.height * 0.44f)
                    close()
                }
                drawPath(path = path, color = color)
            }

            CoolingGlyphType.FAN -> {
                drawCircle(color = color, radius = radius, center = center, style = Stroke(width))
                drawCircle(color = color, radius = size.minDimension * 0.08f, center = center)
                repeat(3) { index ->
                    val angle = Math.toRadians((index * 120 - 90).toDouble())
                    val bladeCenter = Offset(
                        center.x + cos(angle).toFloat() * radius * 0.48f,
                        center.y + sin(angle).toFloat() * radius * 0.48f
                    )
                    drawCircle(color = color.copy(alpha = 0.78f), radius = radius * 0.25f, center = bladeCenter)
                }
            }

            CoolingGlyphType.SENSOR -> {
                drawCircle(color = color, radius = radius, center = center, style = Stroke(width))
                drawCircle(color = color, radius = radius * 0.32f, center = center, style = Stroke(width))
                drawCircle(color = color, radius = width * 0.7f, center = center)
            }

            CoolingGlyphType.LINK -> {
                drawArc(
                    color = color,
                    startAngle = 45f,
                    sweepAngle = 230f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.04f, size.height * 0.28f),
                    size = Size(size.width * 0.55f, size.height * 0.55f),
                    style = Stroke(width, cap = StrokeCap.Round)
                )
                drawArc(
                    color = color,
                    startAngle = 225f,
                    sweepAngle = 230f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.41f, size.height * 0.16f),
                    size = Size(size.width * 0.55f, size.height * 0.55f),
                    style = Stroke(width, cap = StrokeCap.Round)
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.38f, size.height * 0.62f),
                    end = Offset(size.width * 0.63f, size.height * 0.38f),
                    strokeWidth = width,
                    cap = StrokeCap.Round
                )
            }

            CoolingGlyphType.ALARM -> {
                drawArc(
                    color = color,
                    startAngle = 196f,
                    sweepAngle = 148f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
                    size = Size(size.width * 0.64f, size.height * 0.66f),
                    style = Stroke(width, cap = StrokeCap.Round)
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.18f, size.height * 0.72f),
                    end = Offset(size.width * 0.82f, size.height * 0.72f),
                    strokeWidth = width,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = color,
                    radius = width,
                    center = Offset(center.x, size.height * 0.85f)
                )
            }

            CoolingGlyphType.MOON -> {
                drawArc(
                    color = color,
                    startAngle = 72f,
                    sweepAngle = 220f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.09f),
                    size = Size(size.width * 0.72f, size.height * 0.82f),
                    style = Stroke(width * 1.15f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = color,
                    startAngle = 100f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.34f, size.height * 0.12f),
                    size = Size(size.width * 0.54f, size.height * 0.61f),
                    style = Stroke(width * 1.15f, cap = StrokeCap.Round)
                )
            }

            CoolingGlyphType.BALANCE -> {
                drawLine(color, Offset(center.x, size.height * 0.13f), Offset(center.x, size.height * 0.83f), width)
                drawLine(color, Offset(size.width * 0.16f, size.height * 0.31f), Offset(size.width * 0.84f, size.height * 0.31f), width)
                drawLine(color, Offset(size.width * 0.28f, size.height * 0.31f), Offset(size.width * 0.14f, size.height * 0.65f), width)
                drawLine(color, Offset(size.width * 0.28f, size.height * 0.31f), Offset(size.width * 0.42f, size.height * 0.65f), width)
                drawLine(color, Offset(size.width * 0.72f, size.height * 0.31f), Offset(size.width * 0.58f, size.height * 0.65f), width)
                drawLine(color, Offset(size.width * 0.72f, size.height * 0.31f), Offset(size.width * 0.86f, size.height * 0.65f), width)
                drawLine(color, Offset(size.width * 0.14f, size.height * 0.65f), Offset(size.width * 0.42f, size.height * 0.65f), width)
                drawLine(color, Offset(size.width * 0.58f, size.height * 0.65f), Offset(size.width * 0.86f, size.height * 0.65f), width)
                drawLine(color, Offset(size.width * 0.31f, size.height * 0.86f), Offset(size.width * 0.69f, size.height * 0.86f), width)
            }

            CoolingGlyphType.ROCKET -> {
                val body = Path().apply {
                    moveTo(size.width * 0.33f, size.height * 0.67f)
                    cubicTo(
                        size.width * 0.38f,
                        size.height * 0.30f,
                        size.width * 0.61f,
                        size.height * 0.12f,
                        size.width * 0.82f,
                        size.height * 0.12f
                    )
                    cubicTo(
                        size.width * 0.82f,
                        size.height * 0.35f,
                        size.width * 0.65f,
                        size.height * 0.59f,
                        size.width * 0.33f,
                        size.height * 0.67f
                    )
                    close()
                }
                drawPath(body, color = color, style = Stroke(width, cap = StrokeCap.Round))
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.07f,
                    center = Offset(size.width * 0.64f, size.height * 0.31f),
                    style = Stroke(width)
                )
                drawLine(color, Offset(size.width * 0.31f, size.height * 0.68f), Offset(size.width * 0.16f, size.height * 0.83f), width)
                drawLine(color, Offset(size.width * 0.39f, size.height * 0.77f), Offset(size.width * 0.28f, size.height * 0.91f), width)
            }
        }
    }
}

private enum class CoolingGlyphType {
    SNOWFLAKE,
    LIGHTNING,
    FAN,
    SENSOR,
    LINK,
    ALARM,
    MOON,
    BALANCE,
    ROCKET
}

private const val PLACEHOLDER_FAN_FRACTION = 0.60f
private const val FAN_ROTATION_DURATION_MS = 1_500
private const val WATER_WAVE_DURATION_MS = 2_400
private const val WATER_LINE_COUNT = 4
private const val AIRFLOW_LINE_COUNT = 5
private const val FAN_BLADE_COUNT = 6
