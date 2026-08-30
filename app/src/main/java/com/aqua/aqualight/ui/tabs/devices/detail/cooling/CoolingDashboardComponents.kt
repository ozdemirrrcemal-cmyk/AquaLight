package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.R
import kotlin.math.cos
import kotlin.math.sin

internal object CoolingDashboardPalette {
    val background = Color(0xFF00101C)
    val surface = Color(0xFF061827)
    val surfaceRaised = Color(0xFF0B2032)
    val outline = Color(0xFF294052)
    val outlineSoft = Color(0xFF183143)
    val textPrimary = Color(0xFFE8E8FB)
    val textSecondary = Color(0xFFB5B5CC)
    val textMuted = Color(0xFF828A9B)
    val accent = Color(0xFF1474FF)
    val cyan = Color(0xFF40C7F4)
    val success = Color(0xFF61C86C)
    val disabled = Color(0xFF263A49)
}

private val InterRegular = FontFamily(Font(R.font.inter_regular))
private val InterSemiBold = FontFamily(Font(R.font.inter_semibold))

@Composable
internal fun CoolingDashboardHeader(
    isOnline: Boolean,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoolingHeaderIcon(
            iconRes = R.drawable.ic_back,
            contentDescription = stringResource(R.string.change_email_back_button_desc),
            onClick = onBackClick
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = stringResource(R.string.device_family_cooling),
                style = coolingTextStyle(
                    size = 25.sp,
                    lineHeight = 31.sp,
                    color = CoolingDashboardPalette.textPrimary,
                    semiBold = true
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOnline) {
                            CoolingDashboardPalette.success
                        } else {
                            CoolingDashboardPalette.textMuted
                        }
                    )
            )
            BasicText(
                text = stringResource(
                    if (isOnline) R.string.device_online else R.string.device_offline
                ),
                modifier = Modifier.padding(start = 7.dp),
                style = coolingTextStyle(
                    size = 13.sp,
                    lineHeight = 17.sp,
                    color = CoolingDashboardPalette.textSecondary
                )
            )
        }
        CoolingHeaderIcon(
            iconRes = R.drawable.ic_settings,
            contentDescription = stringResource(R.string.device_cooling_open_settings_description),
            onClick = onSettingsClick
        )
    }
}

@Composable
private fun CoolingHeaderIcon(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(27.dp),
            colorFilter = ColorFilter.tint(CoolingDashboardPalette.textPrimary)
        )
    }
}

@Composable
internal fun CoolingAquariumHero(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(CARD_RADIUS)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .clip(shape)
            .background(CoolingDashboardPalette.surface)
            .border(1.dp, CoolingDashboardPalette.outline, shape)
    ) {
        Image(
            painter = painterResource(R.drawable.nature_aquarium),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xD2000B18),
                        0.35f to Color(0xA500162A),
                        1f to Color(0xE800101D)
                    )
                )
        )
        CoolingWaterOverlay(modifier = Modifier.fillMaxSize())
        Image(
            painter = painterResource(R.drawable.ic_device_cooling),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 7.dp, top = 8.dp)
                .width(144.dp)
                .height(108.dp),
            contentScale = ContentScale.Crop
        )
        CoolingSensorBadge(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 116.dp, top = 2.dp)
        )
    }
}

@Composable
private fun CoolingWaterOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val top = size.height * 0.15f
        repeat(4) { index ->
            val path = Path()
            val y = top + index * size.height * 0.018f
            path.moveTo(0f, y)
            path.cubicTo(
                size.width * 0.18f,
                y - 7f,
                size.width * 0.34f,
                y + 8f,
                size.width * 0.51f,
                y
            )
            path.cubicTo(
                size.width * 0.68f,
                y - 6f,
                size.width * 0.85f,
                y + 7f,
                size.width,
                y - 1f
            )
            drawPath(
                path = path,
                color = Color(0xFF88BFFF).copy(alpha = 0.55f - index * 0.08f),
                style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        repeat(5) { index ->
            val path = Path()
            val startX = size.width * 0.17f
            val startY = size.height * (0.36f + index * 0.015f)
            path.moveTo(startX, startY)
            path.cubicTo(
                size.width * 0.26f,
                size.height * (0.43f + index * 0.018f),
                size.width * 0.24f,
                size.height * (0.63f + index * 0.012f),
                size.width * 0.39f,
                size.height * (0.72f + index * 0.01f)
            )
            drawPath(
                path = path,
                color = CoolingDashboardPalette.accent.copy(alpha = 0.64f - index * 0.08f),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun CoolingSensorBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .widthIn(min = 174.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xC8071421))
            .border(1.dp, Color(0xFF536172), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0xFF657184), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CoolingGlyph(
                type = CoolingGlyphType.SNOWFLAKE,
                color = CoolingDashboardPalette.textPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.padding(start = 11.dp)) {
            BasicText(
                text = stringResource(R.string.device_cooling_temperature_sensor),
                style = coolingTextStyle(
                    size = 13.sp,
                    lineHeight = 17.sp,
                    color = CoolingDashboardPalette.textPrimary
                )
            )
            BasicText(
                text = stringResource(R.string.device_cooling_current_temperature_value),
                modifier = Modifier.padding(top = 4.dp),
                style = coolingTextStyle(
                    size = 19.sp,
                    lineHeight = 24.sp,
                    color = CoolingDashboardPalette.textPrimary,
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
            Canvas(modifier = Modifier.size(86.dp)) {
                val stroke = 10.dp.toPx()
                drawArc(
                    color = CoolingDashboardPalette.disabled,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            CoolingDashboardPalette.cyan,
                            CoolingDashboardPalette.accent,
                            CoolingDashboardPalette.accent
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
                    style = coolingTextStyle(
                        size = 24.sp,
                        lineHeight = 29.sp,
                        color = CoolingDashboardPalette.textPrimary,
                        semiBold = true
                    )
                )
                BasicText(
                    text = stringResource(R.string.device_cooling_mode_automatic),
                    modifier = Modifier.padding(top = 2.dp),
                    style = coolingTextStyle(
                        size = 11.sp,
                        lineHeight = 14.sp,
                        color = CoolingDashboardPalette.cyan
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
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
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
            .height(37.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0xFF0D2133) else Color.Transparent)
            .border(
                1.dp,
                if (selected) Color(0xFF405267) else CoolingDashboardPalette.outlineSoft,
                RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) {
                        CoolingDashboardPalette.accent
                    } else {
                        CoolingDashboardPalette.textMuted
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(CoolingDashboardPalette.accent)
                )
            }
        }
        BasicText(
            text = text,
            modifier = Modifier.padding(start = 9.dp),
            style = coolingTextStyle(
                size = 12.sp,
                lineHeight = 16.sp,
                color = CoolingDashboardPalette.textPrimary
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
        Row(
            modifier = Modifier.padding(top = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(29.dp)
                    .clip(CircleShape)
                    .background(CoolingDashboardPalette.surfaceRaised)
                    .border(1.dp, CoolingDashboardPalette.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CoolingGlyph(
                    type = CoolingGlyphType.LIGHTNING,
                    color = CoolingDashboardPalette.textPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }
            BasicText(
                text = stringResource(R.string.device_cooling_power_value),
                modifier = Modifier.padding(start = 6.dp),
                style = coolingTextStyle(
                    size = 15.sp,
                    lineHeight = 19.sp,
                    color = CoolingDashboardPalette.textPrimary,
                    semiBold = true
                )
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        BasicText(
            text = stringResource(R.string.device_cooling_estimated_consumption),
            style = coolingTextStyle(
                size = 10.sp,
                lineHeight = 13.sp,
                color = CoolingDashboardPalette.textSecondary
            )
        )
        BasicText(
            text = stringResource(R.string.device_cooling_consumption_value),
            modifier = Modifier.padding(top = 4.dp),
            style = coolingTextStyle(
                size = 15.sp,
                lineHeight = 19.sp,
                color = CoolingDashboardPalette.textPrimary
            )
        )
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
                .padding(top = 11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                value = stringResource(if (isOnline) R.string.device_online else R.string.device_offline),
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
            color = CoolingDashboardPalette.textPrimary,
            modifier = Modifier.size(15.dp)
        )
        BasicText(
            text = label,
            modifier = Modifier
                .padding(start = 7.dp)
                .weight(1f),
            style = coolingTextStyle(
                size = 10.sp,
                lineHeight = 13.sp,
                color = CoolingDashboardPalette.textSecondary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = value,
            modifier = Modifier.padding(start = 5.dp),
            style = coolingTextStyle(
                size = 10.sp,
                lineHeight = 13.sp,
                color = if (success) {
                    CoolingDashboardPalette.success
                } else {
                    CoolingDashboardPalette.textMuted
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
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                if (selected) CoolingDashboardPalette.accent.copy(alpha = 0.82f) else Color.Transparent
            )
            .border(
                1.dp,
                if (selected) CoolingDashboardPalette.accent else CoolingDashboardPalette.outline,
                RoundedCornerShape(13.dp)
            )
            .padding(horizontal = 3.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CoolingGlyph(
            type = glyph,
            color = CoolingDashboardPalette.textPrimary,
            modifier = Modifier.size(18.dp)
        )
        BasicText(
            text = text,
            modifier = Modifier.padding(top = 4.dp),
            style = coolingTextStyle(
                size = 8.sp,
                lineHeight = 10.sp,
                color = CoolingDashboardPalette.textPrimary,
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
            modifier = Modifier.padding(top = 2.dp),
            style = coolingTextStyle(
                size = 9.sp,
                lineHeight = 12.sp,
                color = CoolingDashboardPalette.textSecondary
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
                modifier = Modifier.padding(bottom = 3.dp),
                style = coolingTextStyle(
                    size = 10.sp,
                    lineHeight = 13.sp,
                    color = CoolingDashboardPalette.textSecondary
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
                    style = coolingTextStyle(
                        size = 11.sp,
                        lineHeight = 14.sp,
                        color = CoolingDashboardPalette.textPrimary,
                        semiBold = true
                    )
                )
                CoolingDisabledSlider(
                    progress = PLACEHOLDER_FAN_FRACTION,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(23.dp)
                )
            }
            BasicText(
                text = stringResource(R.string.device_cooling_range_maximum),
                modifier = Modifier.padding(bottom = 3.dp),
                style = coolingTextStyle(
                    size = 10.sp,
                    lineHeight = 13.sp,
                    color = CoolingDashboardPalette.textSecondary
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
        val radius = 7.dp.toPx()
        val startX = radius
        val endX = size.width - radius
        val thumbX = startX + (endX - startX) * progress
        drawLine(
            color = CoolingDashboardPalette.disabled,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(CoolingDashboardPalette.accent, CoolingDashboardPalette.cyan)
            ),
            start = Offset(startX, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = CoolingDashboardPalette.textPrimary,
            radius = radius,
            center = Offset(thumbX, centerY)
        )
    }
}

@Composable
internal fun CoolingDashboardCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(CARD_RADIUS)
    Column(
        modifier = modifier
            .clip(shape)
            .background(CoolingDashboardPalette.surface)
            .border(1.dp, CoolingDashboardPalette.outline, shape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
private fun CoolingCardTitle(text: String) {
    BasicText(
        text = text,
        style = coolingTextStyle(
            size = 14.sp,
            lineHeight = 18.sp,
            color = CoolingDashboardPalette.textPrimary,
            semiBold = true
        ),
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

internal fun coolingTextStyle(
    size: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    color: Color,
    semiBold: Boolean = false,
    textAlign: TextAlign = TextAlign.Start
): TextStyle = TextStyle(
    color = color,
    fontFamily = if (semiBold) InterSemiBold else InterRegular,
    fontSize = size,
    lineHeight = lineHeight,
    textAlign = textAlign
)

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

private val HEADER_HEIGHT = 68.dp
private val HERO_HEIGHT = 228.dp
private val CARD_RADIUS = 16.dp
private const val PLACEHOLDER_FAN_FRACTION = 0.60f
