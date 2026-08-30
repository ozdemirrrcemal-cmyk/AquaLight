package com.aqua.aqualight.ui.common.header

import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.LayoutAquaHeaderBinding

fun LayoutAquaHeaderBinding.setupAquaHeader(
    fragment: Fragment,
    config: AquaHeaderConfig = AquaHeaderConfig()
) {
    AquaHeaderComponentBinder.bind(
        binding = this,
        fragment = fragment,
        config = config
    )
}

internal fun Fragment.resolveCardIconActionBackgroundColor(
    tone: AquaHeaderCardIconTone
): Int {
    val colorRes = when (tone) {
        AquaHeaderCardIconTone.SUCCESS -> R.color.aqua_toolbar_action_success_container
        AquaHeaderCardIconTone.PRIMARY -> R.color.aqua_toolbar_action_primary_container
        AquaHeaderCardIconTone.NEUTRAL -> R.color.aqua_toolbar_action_neutral_container
        AquaHeaderCardIconTone.DANGER -> R.color.aqua_toolbar_action_danger_container
    }

    return ContextCompat.getColor(requireContext(), colorRes)
}

internal fun Fragment.resolveCardIconActionStrokeColor(
    tone: AquaHeaderCardIconTone
): Int {
    val colorRes = when (tone) {
        AquaHeaderCardIconTone.SUCCESS -> R.color.aqua_toolbar_action_success_outline
        AquaHeaderCardIconTone.PRIMARY -> R.color.aqua_toolbar_action_primary_outline
        AquaHeaderCardIconTone.NEUTRAL -> R.color.aqua_toolbar_action_neutral_outline
        AquaHeaderCardIconTone.DANGER -> R.color.aqua_toolbar_action_danger_outline
    }

    return ContextCompat.getColor(requireContext(), colorRes)
}
