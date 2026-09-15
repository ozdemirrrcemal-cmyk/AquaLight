package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToLong

internal fun Modifier.automaticCycleDialInput(
    enabled: Boolean,
    timeStepMs: Long,
    currentState: () -> AutomaticCycleDialState,
    onTimeChanged: (DeviceLightAutomaticTimeField, Long) -> Unit
): Modifier = pointerInput(enabled, timeStepMs) {
    if (!enabled) return@pointerInput
    var dragSession: AutomaticCycleDragSession? = null
    detectDragGestures(
        onDragStart = { position ->
            dragSession = createAutomaticCycleDragSession(
                position = position,
                width = size.width,
                height = size.height,
                timeStepMs = timeStepMs,
                state = currentState()
            )
        },
        onDragEnd = { dragSession = null },
        onDragCancel = { dragSession = null },
        onDrag = { change, _ ->
            change.consume()
            dragSession?.move(
                position = change.position,
                width = size.width,
                height = size.height,
                timeStepMs = timeStepMs
            )?.let { update ->
                dragSession = update.session
                update.changedTimeMs?.let { timeMs ->
                    onTimeChanged(update.session.field, timeMs)
                }
            }
        }
    )
}

private fun createAutomaticCycleDragSession(
    position: Offset,
    width: Int,
    height: Int,
    timeStepMs: Long,
    state: AutomaticCycleDialState
): AutomaticCycleDragSession {
    val touchedTime = position.toAutomaticCycleTime(width, height, timeStepMs)
    val field = nearestAutomaticCycleField(
        touchedTime,
        state.startTimeMs,
        state.endTimeMs
    )
    val fieldTime = when (field) {
        DeviceLightAutomaticTimeField.START -> state.startTimeMs
        DeviceLightAutomaticTimeField.END -> state.endTimeMs
    } ?: touchedTime
    return AutomaticCycleDragSession(
        field = field,
        previousDegrees = position.toAutomaticCycleDegrees(width, height),
        currentTimeMs = fieldTime.toDouble(),
        emittedTimeMs = fieldTime
    )
}

private fun AutomaticCycleDragSession.move(
    position: Offset,
    width: Int,
    height: Int,
    timeStepMs: Long
): AutomaticCycleDragUpdate {
    val degrees = position.toAutomaticCycleDegrees(width, height)
    val radiusFraction = position.automaticCycleRadiusFraction(width, height)
    val sensitivity = if (radiusFraction <= PRECISION_RADIUS_FRACTION) {
        PRECISE_DRAG_SENSITIVITY
    } else {
        NORMAL_DRAG_SENSITIVITY
    }
    val updatedTimeMs = if (radiusFraction >= MINIMUM_DRAG_RADIUS_FRACTION) {
        advanceAutomaticCycleDragTime(
            currentTimeMs = currentTimeMs,
            deltaDegrees = automaticCycleSignedDeltaDegrees(previousDegrees, degrees),
            sensitivity = sensitivity
        )
    } else {
        currentTimeMs
    }
    val snappedTimeMs = snapAutomaticCycleTime(updatedTimeMs.roundToLong(), timeStepMs)
    return AutomaticCycleDragUpdate(
        session = copy(
            previousDegrees = degrees,
            currentTimeMs = updatedTimeMs,
            emittedTimeMs = snappedTimeMs
        ),
        changedTimeMs = snappedTimeMs.takeUnless { timeMs -> timeMs == emittedTimeMs }
    )
}

private fun Offset.toAutomaticCycleTime(width: Int, height: Int, stepMs: Long): Long =
    automaticCycleTimeFromClockDegrees(toAutomaticCycleDegrees(width, height), stepMs)

private fun Offset.toAutomaticCycleDegrees(width: Int, height: Int): Double {
    val center = Offset(width / HALF_DIVISOR, height / HALF_DIVISOR)
    val rawDegrees = Math.toDegrees(
        kotlin.math.atan2((y - center.y).toDouble(), (x - center.x).toDouble())
    ) + QUARTER_TURN_DEGREES
    return (rawDegrees + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
}

private fun Offset.automaticCycleRadiusFraction(width: Int, height: Int): Float {
    val center = Offset(width / HALF_DIVISOR, height / HALF_DIVISOR)
    val radius = minOf(width, height) / HALF_DIVISOR
    return (this - center).getDistance() / radius
}

private data class AutomaticCycleDragSession(
    val field: DeviceLightAutomaticTimeField,
    val previousDegrees: Double,
    val currentTimeMs: Double,
    val emittedTimeMs: Long
)

private data class AutomaticCycleDragUpdate(
    val session: AutomaticCycleDragSession,
    val changedTimeMs: Long?
)

private const val HALF_DIVISOR = 2f
private const val QUARTER_TURN_DEGREES = 90.0
private const val FULL_CIRCLE_DEGREES = 360.0
private const val NORMAL_DRAG_SENSITIVITY = 1.0
private const val PRECISE_DRAG_SENSITIVITY = 0.25
private const val PRECISION_RADIUS_FRACTION = 0.72f
private const val MINIMUM_DRAG_RADIUS_FRACTION = 0.30f
