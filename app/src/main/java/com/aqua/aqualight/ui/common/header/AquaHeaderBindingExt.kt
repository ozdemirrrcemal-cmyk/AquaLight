package com.aqua.aqualight.ui.common.header

import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.databinding.LayoutAquaHeaderBinding

fun LayoutAquaHeaderBinding.setupAquaHeader(
    fragment: Fragment,
    config: AquaHeaderConfig
) {
    tvTitle.text = config.title
    renderBackNavigation(fragment, config)
    renderStatusIcon(config.statusIconRes)
    renderToolbarActions(config.actions)
    renderFilledIconAction(config.filledIconAction)
    renderCardIconAction(fragment, config.cardIconAction)
    renderPillTextAction(fragment, config.pillTextAction)
    renderScoreBadge(config.scoreBadge)
    renderPrimaryAction(config.primaryAction)
    renderSearchField(config.searchField)
}

private fun LayoutAquaHeaderBinding.renderBackNavigation(
    fragment: Fragment,
    config: AquaHeaderConfig
) {
    btnBack.visibility = if (config.showBackButton) View.VISIBLE else View.GONE
    btnBack.setOnClickListener(
        if (config.showBackButton) {
            View.OnClickListener {
                config.onBackClick?.invoke()
                    ?: fragment.findNavController().popBackStack()
            }
        } else {
            null
        }
    )
}

private fun LayoutAquaHeaderBinding.renderStatusIcon(statusIconRes: Int?) {
    imgStatusIcon.visibility = if (statusIconRes == null) View.GONE else View.VISIBLE
    if (statusIconRes == null) {
        imgStatusIcon.setImageDrawable(null)
    } else {
        imgStatusIcon.setImageResource(statusIconRes)
    }
}

private fun LayoutAquaHeaderBinding.renderToolbarActions(
    actions: List<AquaHeaderAction>
) {
    val buttons = listOf(btnActionOne, btnActionTwo, btnActionThree)
    headerActionsContainer.visibility = if (actions.isEmpty()) View.GONE else View.VISIBLE

    buttons.forEachIndexed { index, button ->
        val action = actions.getOrNull(index)
        button.visibility = if (action == null) View.GONE else View.VISIBLE
        button.contentDescription = action?.contentDescription
        button.setOnClickListener(action?.let { item ->
            View.OnClickListener { item.onClick() }
        })
        if (action == null) {
            button.setImageDrawable(null)
        } else {
            button.setImageResource(action.iconRes)
        }
    }
}
