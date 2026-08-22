package com.aqua.aqualight.ui.common.header

import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.LayoutAquaHeaderBinding

internal fun LayoutAquaHeaderBinding.renderFilledIconAction(
    action: AquaHeaderFilledIconAction?
) {
    btnFilledIconAction.visibility = action.visibleState()
    btnFilledIconAction.contentDescription = action?.contentDescription
    btnFilledIconAction.renderAvailability(action?.enabled ?: true, action?.onClick)
    if (action == null) {
        btnFilledIconAction.setImageDrawable(null)
    } else {
        btnFilledIconAction.setImageResource(action.iconRes)
    }
}

internal fun LayoutAquaHeaderBinding.renderCardIconAction(
    fragment: Fragment,
    action: AquaHeaderCardIconAction?
) {
    btnCardIconAction.visibility = action.visibleState()
    btnCardIconAction.contentDescription = action?.contentDescription
    btnCardIconAction.renderAvailability(action?.enabled ?: true, action?.onClick)
    btnCardIconAction.isClickable = action?.enabled == true
    btnCardIconAction.isFocusable = action?.enabled == true

    if (action == null) {
        ivCardIconAction.setImageDrawable(null)
        return
    }

    btnCardIconAction.setCardBackgroundColor(
        action.backgroundColor ?: fragment.resolveCardIconActionBackgroundColor(action.tone)
    )
    btnCardIconAction.strokeColor =
        action.strokeColor ?: fragment.resolveCardIconActionStrokeColor(action.tone)
    ivCardIconAction.setImageResource(action.iconRes)
    ivCardIconAction.imageTintList = ColorStateList.valueOf(
        action.iconTintColor ?: ContextCompat.getColor(
            fragment.requireContext(),
            R.color.aqua_content_on_dark
        )
    )
}

internal fun LayoutAquaHeaderBinding.renderPillTextAction(
    fragment: Fragment,
    action: AquaHeaderPillTextAction?
) {
    btnPillTextAction.visibility = action.visibleState()
    btnPillTextAction.text = action?.text
    btnPillTextAction.contentDescription = action?.contentDescription
    btnPillTextAction.renderAvailability(action?.enabled ?: true, action?.onClick)
    if (action == null) return

    btnPillTextAction.setTextColor(
        action.textColor ?: ContextCompat.getColor(
            fragment.requireContext(),
            R.color.aqua_content_on_dark
        )
    )
    btnPillTextAction.setBackgroundResource(action.backgroundRes)
}

internal fun LayoutAquaHeaderBinding.renderScoreBadge(scoreBadge: AquaHeaderScoreBadge?) {
    scoreContainer.visibility = scoreBadge.visibleState()
    tvScore.text = scoreBadge?.text
    scoreContainer.contentDescription = scoreBadge?.contentDescription
    scoreContainer.isClickable = scoreBadge?.onClick != null
    scoreContainer.isFocusable = scoreBadge?.onClick != null
    scoreContainer.setOnClickListener(scoreBadge?.onClick?.asClickListener())
    if (scoreBadge == null) return

    tvScore.setTextColor(scoreBadge.textColor)
    scoreContainer.strokeColor = scoreBadge.strokeColor
}

internal fun LayoutAquaHeaderBinding.renderPrimaryAction(
    action: AquaHeaderPrimaryAction?
) {
    btnPrimaryAction.visibility = action.visibleState()
    btnPrimaryAction.text = action?.text
    btnPrimaryAction.contentDescription = action?.contentDescription
    btnPrimaryAction.setOnClickListener(action?.onClick?.asClickListener())
}

private fun View.renderAvailability(enabled: Boolean, onClick: (() -> Unit)?) {
    isEnabled = enabled
    alpha = if (enabled) ENABLED_ACTION_ALPHA else DISABLED_ACTION_ALPHA
    setOnClickListener(onClick?.takeIf { enabled }?.asClickListener())
}

private fun Any?.visibleState(): Int = if (this == null) View.GONE else View.VISIBLE

private fun (() -> Unit).asClickListener() = View.OnClickListener { invoke() }

private fun Fragment.resolveCardIconActionBackgroundColor(
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

private fun Fragment.resolveCardIconActionStrokeColor(
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

private const val ENABLED_ACTION_ALPHA = 1f
private const val DISABLED_ACTION_ALPHA = 0.45f
