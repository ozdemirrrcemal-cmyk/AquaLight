package com.aqua.aqualight.ui.tabs.devices.detail.settings

/** Returns an empty value for nullable presentation text without narrowing it to [String]. */
internal fun CharSequence?.orEmpty(): CharSequence = this ?: ""
