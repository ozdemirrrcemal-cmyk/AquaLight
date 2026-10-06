package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.fragment.app.Fragment
import com.aqua.aqualight.i18n.LocaleFormatter

internal fun formatLivestockHealthDateTime(
    fragment: Fragment,
    millis: Long
): String =
    LocaleFormatter.formatDate(fragment.requireContext(), millis) +
        " · " +
        LocaleFormatter.formatTime(fragment.requireContext(), millis)
