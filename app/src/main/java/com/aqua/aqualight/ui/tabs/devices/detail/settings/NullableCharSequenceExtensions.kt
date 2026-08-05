package com.aqua.aqualight.ui.tabs.devices.detail.settings

/** Converts nullable presentation text to the non-null String required by Android UI APIs. */
internal fun CharSequence?.orEmpty(): String = this?.toString().orEmpty()
