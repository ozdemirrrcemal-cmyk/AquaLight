package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

internal fun DrawScope.dosingDropPath(): Path = Path().apply {
    moveTo(size.width * DROP_CENTER_X, size.height * DROP_TOP_Y)
    cubicTo(
        size.width * DROP_RIGHT_UPPER_X,
        size.height * DROP_UPPER_CONTROL_Y,
        size.width * DROP_RIGHT_X,
        size.height * DROP_MIDDLE_CONTROL_Y,
        size.width * DROP_RIGHT_X,
        size.height * DROP_BODY_Y
    )
    cubicTo(
        size.width * DROP_RIGHT_X,
        size.height * DROP_BOTTOM_CONTROL_Y,
        size.width * DROP_LEFT_X,
        size.height * DROP_BOTTOM_CONTROL_Y,
        size.width * DROP_LEFT_X,
        size.height * DROP_BODY_Y
    )
    cubicTo(
        size.width * DROP_LEFT_X,
        size.height * DROP_MIDDLE_CONTROL_Y,
        size.width * DROP_LEFT_UPPER_X,
        size.height * DROP_UPPER_CONTROL_Y,
        size.width * DROP_CENTER_X,
        size.height * DROP_TOP_Y
    )
    close()
}

private const val DROP_CENTER_X = 0.50f
private const val DROP_TOP_Y = 0.05f
private const val DROP_RIGHT_UPPER_X = 0.63f
private const val DROP_LEFT_UPPER_X = 0.37f
private const val DROP_RIGHT_X = 0.84f
private const val DROP_LEFT_X = 0.16f
private const val DROP_UPPER_CONTROL_Y = 0.22f
private const val DROP_MIDDLE_CONTROL_Y = 0.48f
private const val DROP_BODY_Y = 0.66f
private const val DROP_BOTTOM_CONTROL_Y = 0.93f
