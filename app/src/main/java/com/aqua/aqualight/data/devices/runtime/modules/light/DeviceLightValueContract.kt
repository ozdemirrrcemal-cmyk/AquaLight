package com.aqua.aqualight.data.devices.runtime.modules.light

internal const val LIGHT_MIN_COUNT = 0
internal const val LIGHT_NON_NEGATIVE_LONG = 0L
internal const val LIGHT_NON_NEGATIVE_VALUE = 0.0
internal const val LIGHT_NORMALIZED_MIN = 0.0
internal const val LIGHT_NORMALIZED_MAX = 1.0
internal const val LIGHT_MANUAL_INACTIVE_VALUE = -1.0
internal const val LIGHT_PERCENT_MIN = 0.0
internal const val LIGHT_PERCENT_MAX = 100.0
internal const val LIGHT_MANUAL_INACTIVE_PERCENT = -100.0
internal const val LIGHT_PERCENT_SCALE = 100.0
internal const val LIGHT_LAST_DAY_MILLISECOND =
    DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY - 1L

internal fun lightValuesEquivalent(left: Double, right: Double): Boolean =
    kotlin.math.abs(left - right) <= LIGHT_VALUE_TOLERANCE

private const val LIGHT_VALUE_TOLERANCE = 0.001
