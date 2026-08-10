package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuDivider
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuGeometry
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuRow
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuRowContent
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSectionSurface
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSwitchRow
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuTone
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuColors
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuTypography

/** Static control shell with UI-only destinations and direct local controls. */
@Composable
internal fun DeviceDosingChannelDetailScreen(
    modifier: Modifier = Modifier,
    onMenuItemClick: ((DosingDetailMenuItem) -> Unit)? = null,
    onManualDoseClick: (() -> Unit)? = null,
    onResetChannelClick: (() -> Unit)? = null
) {
    val colors = aquaDeviceMenuColors()
    var missedDoseRecoveryEnabled by rememberSaveable { mutableStateOf(false) }
    val directActions = DosingDetailDirectActions(
        onManualDoseClick = onManualDoseClick,
        onResetChannelClick = onResetChannelClick
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(
            start = AquaDeviceMenuGeometry.screenHorizontalPadding,
            top = AquaDeviceMenuGeometry.screenTopPadding,
            end = AquaDeviceMenuGeometry.screenHorizontalPadding,
            bottom = AquaDeviceMenuGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.sectionGap)
    ) {
        item(key = DETAIL_HERO_KEY) {
            DosingDetailHero()
        }

        items(
            items = DOSING_DETAIL_MENU_SECTIONS,
            key = DosingDetailMenuSection::titleRes
        ) { section ->
            DosingDetailSection(
                section = section,
                onMenuItemClick = onMenuItemClick,
                directActions = directActions,
                missedDoseRecoveryEnabled = missedDoseRecoveryEnabled,
                onMissedDoseRecoveryChange = { enabled ->
                    missedDoseRecoveryEnabled = enabled
                }
            )
        }
    }
}

@Composable
private fun DosingDetailHero() {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)

    AquaDeviceMenuSectionSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(AquaDeviceMenuGeometry.heroPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(AquaDeviceMenuGeometry.heroAccentWidth)
                    .height(AquaDeviceMenuGeometry.heroAccentHeight)
                    .clip(RoundedCornerShape(AquaDeviceMenuGeometry.heroAccentRadius))
                    .background(colors.accent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AquaDeviceMenuGeometry.heroContentGap),
                verticalArrangement = Arrangement.spacedBy(
                    AquaDeviceMenuGeometry.rowTextGap
                )
            ) {
                BasicText(
                    text = stringResource(R.string.device_dosing_detail_hero_eyebrow),
                    style = typography.eyebrow
                )
                BasicText(
                    text = stringResource(R.string.device_dosing_detail_hero_title),
                    style = typography.heroTitle
                )
                BasicText(
                    text = stringResource(R.string.device_dosing_detail_hero_description),
                    style = typography.heroBody
                )
            }
        }
    }
}

@Composable
private fun DosingDetailSection(
    section: DosingDetailMenuSection,
    onMenuItemClick: ((DosingDetailMenuItem) -> Unit)?,
    directActions: DosingDetailDirectActions,
    missedDoseRecoveryEnabled: Boolean,
    onMissedDoseRecoveryChange: (Boolean) -> Unit
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)

    Column(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = stringResource(section.titleRes),
            modifier = Modifier.padding(
                start = AquaDeviceMenuGeometry.rowHorizontalPadding,
                bottom = AquaDeviceMenuGeometry.sectionLabelBottomSpacing
            ),
            style = typography.sectionLabel
        )
        AquaDeviceMenuSectionSurface(modifier = Modifier.fillMaxWidth()) {
            section.items.forEachIndexed { index, item ->
                if (index > 0) {
                    AquaDeviceMenuDivider()
                }
                AquaDeviceMenuRow(
                    content = AquaDeviceMenuRowContent(
                        title = stringResource(item.titleRes),
                        description = stringResource(item.descriptionRes),
                        iconRes = item.iconRes,
                        tone = item.tone
                    ),
                    onClick = onMenuItemClick?.let { callback ->
                        { callback(item) }
                    }
                )
            }
            if (section.hasMissedDoseRecoverySwitch) {
                DosingMissedDoseRecoverySwitch(
                    checked = missedDoseRecoveryEnabled,
                    onCheckedChange = onMissedDoseRecoveryChange
                )
            }
            if (section.hasManualDoseAction) {
                if (section.items.isNotEmpty()) {
                    AquaDeviceMenuDivider()
                }
                DosingManualDoseAction(onClick = directActions.onManualDoseClick)
            }
            if (section.hasResetChannelAction) {
                if (section.items.isNotEmpty() || section.hasManualDoseAction) {
                    AquaDeviceMenuDivider()
                }
                DosingResetChannelAction(onClick = directActions.onResetChannelClick)
            }
        }
    }
}

@Composable
private fun DosingMissedDoseRecoverySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val stateLabel = stringResource(
        if (checked) {
            R.string.device_dosing_detail_state_on
        } else {
            R.string.device_dosing_detail_state_off
        }
    )

    AquaDeviceMenuDivider()
    AquaDeviceMenuSwitchRow(
        content = AquaDeviceMenuRowContent(
            title = stringResource(R.string.device_dosing_detail_missed_dose_title),
            description = stringResource(R.string.device_dosing_detail_missed_dose_description),
            iconRes = R.drawable.ic_dosing_recovery_24
        ),
        checked = checked,
        toggleContentDescription = stringResource(
            R.string.device_dosing_detail_missed_dose_toggle_description,
            stateLabel
        ),
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun DosingManualDoseAction(onClick: (() -> Unit)?) {
    AquaDeviceMenuRow(
        content = AquaDeviceMenuRowContent(
            title = stringResource(R.string.device_dosing_detail_manual_title),
            description = stringResource(R.string.device_dosing_detail_manual_description),
            iconRes = R.drawable.ic_dosing_manual_24
        ),
        onClick = onClick,
        showTrailingIcon = false
    )
}

@Composable
private fun DosingResetChannelAction(onClick: (() -> Unit)?) {
    AquaDeviceMenuRow(
        content = AquaDeviceMenuRowContent(
            title = stringResource(R.string.device_dosing_detail_reset_title),
            description = stringResource(R.string.device_dosing_detail_reset_description),
            iconRes = R.drawable.ic_dosing_reset_24,
            tone = AquaDeviceMenuTone.DANGER
        ),
        onClick = onClick,
        showTrailingIcon = false
    )
}

private data class DosingDetailDirectActions(
    val onManualDoseClick: (() -> Unit)?,
    val onResetChannelClick: (() -> Unit)?
)

internal data class DosingDetailMenuSection(
    @StringRes val titleRes: Int,
    val items: List<DosingDetailMenuItem>,
    val hasMissedDoseRecoverySwitch: Boolean = false,
    val hasManualDoseAction: Boolean = false,
    val hasResetChannelAction: Boolean = false
)

internal enum class DosingDetailMenuItem(
    val routeKey: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val tone: AquaDeviceMenuTone = AquaDeviceMenuTone.ACCENT
) {
    DOSING_PLAN(
        routeKey = "dosing-plan",
        titleRes = R.string.device_dosing_detail_plan_title,
        descriptionRes = R.string.device_dosing_detail_plan_description,
        iconRes = R.drawable.ic_dosing_schedule_24
    ),
    CALIBRATION(
        routeKey = "calibration",
        titleRes = R.string.device_dosing_detail_calibration_title,
        descriptionRes = R.string.device_dosing_detail_calibration_description,
        iconRes = R.drawable.ic_dosing_calibration_24
    ),
    RESERVOIR(
        routeKey = "reservoir",
        titleRes = R.string.device_dosing_detail_reservoir_title,
        descriptionRes = R.string.device_dosing_detail_reservoir_description,
        iconRes = R.drawable.ic_care_fertilizer_24
    );

    companion object {
        private val byRouteKey = entries.associateBy(DosingDetailMenuItem::routeKey)

        fun fromRouteKey(routeKey: String): DosingDetailMenuItem? =
            byRouteKey[routeKey.trim()]
    }
}

internal val DOSING_DETAIL_MENU_SECTIONS = listOf(
    DosingDetailMenuSection(
        titleRes = R.string.device_dosing_detail_planning_section,
        items = listOf(DosingDetailMenuItem.DOSING_PLAN),
        hasMissedDoseRecoverySwitch = true
    ),
    DosingDetailMenuSection(
        titleRes = R.string.device_dosing_detail_accuracy_section,
        items = listOf(
            DosingDetailMenuItem.CALIBRATION,
            DosingDetailMenuItem.RESERVOIR
        )
    ),
    DosingDetailMenuSection(
        titleRes = R.string.device_dosing_detail_control_section,
        items = emptyList(),
        hasManualDoseAction = true,
        hasResetChannelAction = true
    )
)

private const val DETAIL_HERO_KEY = "dosing-detail-hero"
