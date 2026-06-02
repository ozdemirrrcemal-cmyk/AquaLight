package com.aqua.aqualight.ui.common.header

import android.view.View
import com.aqua.aqualight.databinding.LayoutDeviceDetailHeaderBinding

fun LayoutDeviceDetailHeaderBinding.setupAquaHeader(
    config: AquaHeaderConfig
) {
    tvTitle.text = config.title

    btnBack.visibility = if (config.showBackButton) View.VISIBLE else View.GONE
    btnBack.setOnClickListener {
        config.onBackClick?.invoke()
    }

    if (config.statusIconRes == null) {
        imgStatusIcon.visibility = View.GONE
        imgStatusIcon.setImageDrawable(null)
    } else {
        imgStatusIcon.visibility = View.VISIBLE
        imgStatusIcon.setImageResource(config.statusIconRes)
    }

    val actionButtons = listOf(
        btnActionOne,
        btnActionTwo,
        btnActionThree
    )

    headerActionsContainer.visibility =
        if (config.actions.isEmpty()) View.GONE else View.VISIBLE

    actionButtons.forEachIndexed { index, button ->
        val action = config.actions.getOrNull(index)

        if (action == null) {
            button.visibility = View.GONE
            button.setOnClickListener(null)
            button.setImageDrawable(null)
            button.contentDescription = null
        } else {
            button.visibility = View.VISIBLE
            button.setImageResource(action.iconRes)
            button.contentDescription = action.contentDescription
            button.setOnClickListener {
                action.onClick()
            }
        }
    }
}