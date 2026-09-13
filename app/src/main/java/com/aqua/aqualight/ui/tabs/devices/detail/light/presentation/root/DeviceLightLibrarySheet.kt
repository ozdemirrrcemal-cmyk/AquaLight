package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.light.AquaLightDashboardAlpha
import com.aqua.aqualight.ui.common.light.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.common.light.aquaLightDashboardColors
import com.aqua.aqualight.ui.common.light.aquaLightDashboardTypography
import com.aqua.aqualight.ui.common.light.aquaLightLibraryScrimColor

@Composable
internal fun DeviceLightLibrarySheet(
    visible: Boolean,
    onDismissRequest: () -> Unit
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(aquaLightLibraryScrimColor())
                .pointerInput(onDismissRequest) {
                    detectTapGestures { onDismissRequest() }
                }
        ) {
            DeviceLightLibraryContent(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .pointerInput(Unit) { detectTapGestures { } }
            )
        }
    }
}

@Composable
private fun DeviceLightLibraryDragHandle() {
    val colors = aquaLightDashboardColors()
    Box(
        modifier = Modifier
            .padding(top = AquaLightDashboardGeometry.libraryDragHandleTopPadding)
            .width(AquaLightDashboardGeometry.libraryDragHandleWidth)
            .height(AquaLightDashboardGeometry.libraryDragHandleHeight)
            .clip(RoundedCornerShape(AquaLightDashboardGeometry.libraryDragHandleHeight))
            .background(
                colors.secondaryText.copy(alpha = AquaLightDashboardAlpha.libraryHandle)
            )
    )
}

@Composable
private fun DeviceLightLibraryContent(modifier: Modifier) {
    val colors = aquaLightDashboardColors()
    val typography = aquaLightDashboardTypography(colors)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AquaLightDashboardGeometry.librarySheetMinimumHeight)
            .clip(AquaLightDashboardGeometry.librarySheetShape)
            .background(colors.surface)
            .navigationBarsPadding()
            .padding(
                start = AquaLightDashboardGeometry.librarySheetHorizontalPadding,
                end = AquaLightDashboardGeometry.librarySheetHorizontalPadding,
                bottom = AquaLightDashboardGeometry.librarySheetBottomPadding
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DeviceLightLibraryDragHandle()
        BasicText(
            text = stringResource(R.string.device_light_library_title),
            style = typography.title.copy(
                color = colors.primaryText,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AquaLightDashboardGeometry.libraryTitleTopPadding)
        )
        Spacer(modifier = Modifier.height(AquaLightDashboardGeometry.libraryEmptyTopPadding))
        DeviceLightLibraryEmptyState()
    }
}

@Composable
private fun DeviceLightLibraryEmptyState() {
    val colors = aquaLightDashboardColors()
    val typography = aquaLightDashboardTypography(colors)
    Box(
        modifier = Modifier
            .size(AquaLightDashboardGeometry.libraryEmptyIconContainerSize)
            .clip(CircleShape)
            .background(
                colors.accent.copy(alpha = AquaLightDashboardAlpha.libraryIconSurface)
            )
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = colors.accent.copy(alpha = AquaLightDashboardAlpha.libraryIconOutline),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_light_library),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.accent),
            modifier = Modifier.size(AquaLightDashboardGeometry.libraryEmptyIconSize)
        )
    }
    BasicText(
        text = stringResource(R.string.device_light_library_empty_title),
        style = typography.title.copy(
            color = colors.primaryText,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AquaLightDashboardGeometry.libraryEmptyTitleTopPadding)
    )
    BasicText(
        text = stringResource(R.string.device_light_library_empty_description),
        style = typography.caption.copy(
            color = colors.secondaryText,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AquaLightDashboardGeometry.libraryEmptyDescriptionTopPadding)
    )
}
