package com.aqua.aqualight.ui.common.header

import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.LayoutAquaHeaderBinding

fun LayoutAquaHeaderBinding.setupAquaHeader(
    fragment: Fragment,
    config: AquaHeaderConfig = AquaHeaderConfig()
) {
    val navController =
        fragment.findNavController()

    val resolvedTitle =
        config.titleOverride
            ?: navController.currentDestination
                ?.label
                ?.toString()
                .orEmpty()

    tvTitle.text =
        resolvedTitle

    if (config.showBackButton) {
        btnBack.visibility =
            View.VISIBLE

        btnBack.setOnClickListener {
            config.onBackClick?.invoke()
                ?: navController.popBackStack()
        }
    } else {
        btnBack.visibility =
            View.GONE

        btnBack.setOnClickListener(null)
    }

    val statusIcon = config.statusIcon
    if (statusIcon == null) {
        imgStatusIcon.visibility =
            View.GONE

        imgStatusIcon.setImageDrawable(null)
        imgStatusIcon.imageTintList = null
        imgStatusIcon.contentDescription = null
    } else {
        imgStatusIcon.visibility =
            View.VISIBLE

        imgStatusIcon.setImageResource(
            statusIcon.iconRes
        )

        imgStatusIcon.imageTintList =
            ColorStateList.valueOf(
                ContextCompat.getColor(fragment.requireContext(), statusIcon.tintColorRes)
            )

        imgStatusIcon.contentDescription =
            statusIcon.contentDescription
    }

    val actionButtons =
        listOf(
            btnActionOne,
            btnActionTwo,
            btnActionThree
        )

    headerActionsContainer.visibility =
        if (config.actions.isEmpty()) {
            View.GONE
        } else {
            View.VISIBLE
        }

    actionButtons.forEachIndexed { index, button ->
        val action =
            config.actions.getOrNull(index)

        if (action == null) {
            button.visibility =
                View.GONE

            button.setOnClickListener(null)

            button.setImageDrawable(null)

            button.contentDescription =
                null

            button.isEnabled =
                true

            button.alpha =
                ACTION_ENABLED_ALPHA
        } else {
            button.visibility =
                View.VISIBLE

            button.setImageResource(
                action.iconRes
            )

            button.contentDescription =
                action.contentDescription

            button.isEnabled =
                action.enabled

            button.alpha =
                if (action.enabled) ACTION_ENABLED_ALPHA else ACTION_DISABLED_ALPHA

            button.setOnClickListener(
                if (action.enabled) {
                    View.OnClickListener { action.onClick() }
                } else {
                    null
                }
            )
        }
    }

    val filledIconAction =
        config.filledIconAction

    if (filledIconAction == null) {
        btnFilledIconAction.visibility =
            View.GONE

        btnFilledIconAction.setImageDrawable(null)

        btnFilledIconAction.contentDescription =
            null

        btnFilledIconAction.isEnabled =
            true

        btnFilledIconAction.alpha =
            ACTION_ENABLED_ALPHA

        btnFilledIconAction.setOnClickListener(null)
    } else {
        btnFilledIconAction.visibility =
            View.VISIBLE

        btnFilledIconAction.setImageResource(
            filledIconAction.iconRes
        )

        btnFilledIconAction.contentDescription =
            filledIconAction.contentDescription

        btnFilledIconAction.isEnabled =
            filledIconAction.enabled

        btnFilledIconAction.alpha =
            if (filledIconAction.enabled) {
                ACTION_ENABLED_ALPHA
            } else {
                ACTION_DISABLED_ALPHA
            }

        btnFilledIconAction.setOnClickListener(
            if (filledIconAction.enabled) {
                View.OnClickListener {
                    filledIconAction.onClick()
                }
            } else {
                null
            }
        )
    }

    val cardIconAction =
        config.cardIconAction

    if (cardIconAction == null) {
        btnCardIconAction.visibility =
            View.GONE

        btnCardIconAction.contentDescription =
            null

        btnCardIconAction.isClickable =
            false

        btnCardIconAction.isFocusable =
            false

        btnCardIconAction.isEnabled =
            true

        btnCardIconAction.alpha =
            ACTION_ENABLED_ALPHA

        btnCardIconAction.setOnClickListener(null)

        ivCardIconAction.setImageDrawable(null)
    } else {
        btnCardIconAction.visibility =
            View.VISIBLE

        btnCardIconAction.setCardBackgroundColor(
            cardIconAction.backgroundColor
                ?: fragment.resolveCardIconActionBackgroundColor(cardIconAction.tone)
        )

        btnCardIconAction.strokeColor =
            cardIconAction.strokeColor
                ?: fragment.resolveCardIconActionStrokeColor(cardIconAction.tone)

        btnCardIconAction.contentDescription =
            cardIconAction.contentDescription

        btnCardIconAction.isClickable =
            cardIconAction.enabled

        btnCardIconAction.isFocusable =
            cardIconAction.enabled

        btnCardIconAction.isEnabled =
            cardIconAction.enabled

        btnCardIconAction.alpha =
            if (cardIconAction.enabled) {
                ACTION_ENABLED_ALPHA
            } else {
                ACTION_DISABLED_ALPHA
            }

        ivCardIconAction.setImageResource(
            cardIconAction.iconRes
        )

        ivCardIconAction.imageTintList =
            ColorStateList.valueOf(
                cardIconAction.iconTintColor
                    ?: ContextCompat.getColor(fragment.requireContext(), R.color.aqua_content_on_dark)
            )

        btnCardIconAction.setOnClickListener(
            if (cardIconAction.enabled) {
                View.OnClickListener {
                    cardIconAction.onClick()
                }
            } else {
                null
            }
        )
    }

    val pillTextAction =
        config.pillTextAction

    if (pillTextAction == null) {
        btnPillTextAction.visibility =
            View.GONE

        btnPillTextAction.text =
            null

        btnPillTextAction.contentDescription =
            null

        btnPillTextAction.isEnabled =
            true

        btnPillTextAction.alpha =
            ACTION_ENABLED_ALPHA

        btnPillTextAction.setOnClickListener(null)
    } else {
        btnPillTextAction.visibility =
            View.VISIBLE

        btnPillTextAction.text =
            pillTextAction.text

        btnPillTextAction.contentDescription =
            pillTextAction.contentDescription

        btnPillTextAction.setTextColor(
            pillTextAction.textColor
                ?: ContextCompat.getColor(fragment.requireContext(), R.color.aqua_content_on_dark)
        )

        btnPillTextAction.setBackgroundResource(
            pillTextAction.backgroundRes
        )

        btnPillTextAction.isEnabled =
            pillTextAction.enabled

        btnPillTextAction.alpha =
            if (pillTextAction.enabled) {
                ACTION_ENABLED_ALPHA
            } else {
                ACTION_DISABLED_ALPHA
            }

        btnPillTextAction.setOnClickListener(
            if (pillTextAction.enabled) {
                View.OnClickListener {
                    pillTextAction.onClick()
                }
            } else {
                null
            }
        )
    }

    val scoreBadge =
        config.scoreBadge

    if (scoreBadge == null) {
        scoreContainer.visibility =
            View.GONE

        tvScore.text =
            null

        scoreContainer.contentDescription =
            null

        scoreContainer.isClickable =
            false

        scoreContainer.isFocusable =
            false

        scoreContainer.setOnClickListener(null)
    } else {
        scoreContainer.visibility =
            View.VISIBLE

        tvScore.text =
            scoreBadge.text

        tvScore.setTextColor(
            scoreBadge.textColor
        )

        scoreContainer.strokeColor =
            scoreBadge.strokeColor

        scoreContainer.contentDescription =
            scoreBadge.contentDescription

        scoreContainer.isClickable =
            scoreBadge.onClick != null

        scoreContainer.isFocusable =
            scoreBadge.onClick != null

        scoreContainer.setOnClickListener(
            scoreBadge.onClick?.let { onClick ->
                View.OnClickListener {
                    onClick()
                }
            }
        )
    }

    val primaryAction =
        config.primaryAction

    if (primaryAction == null) {
        btnPrimaryAction.visibility =
            View.GONE

        btnPrimaryAction.text =
            null

        btnPrimaryAction.contentDescription =
            null

        btnPrimaryAction.setOnClickListener(null)
    } else {
        btnPrimaryAction.visibility =
            View.VISIBLE

        btnPrimaryAction.text =
            primaryAction.text

        btnPrimaryAction.contentDescription =
            primaryAction.contentDescription

        btnPrimaryAction.setOnClickListener {
            primaryAction.onClick()
        }
    }

    val oldWatcher =
        etHeaderSearch.tag as? TextWatcher

    if (oldWatcher != null) {
        etHeaderSearch.removeTextChangedListener(oldWatcher)
        etHeaderSearch.tag = null
    }

    val searchField =
        config.searchField

    if (searchField == null) {
        searchContainer.visibility =
            View.GONE

        titleStatusContainer.visibility =
            View.VISIBLE

        headerRightContainer.visibility =
            View.VISIBLE

        etHeaderSearch.setText("")

        btnClearHeaderSearch.visibility =
            View.GONE

        btnClearHeaderSearch.setOnClickListener(null)
    } else {
        searchContainer.visibility =
            View.VISIBLE

        titleStatusContainer.visibility =
            View.GONE

        headerRightContainer.visibility =
            View.GONE

        etHeaderSearch.hint =
            searchField.hint

        if (etHeaderSearch.text.toString() != searchField.text) {
            etHeaderSearch.setText(
                searchField.text
            )

            etHeaderSearch.setSelection(
                etHeaderSearch.text.length
            )
        }

        btnClearHeaderSearch.visibility =
            if (etHeaderSearch.text.isNullOrBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        val watcher =
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    val value =
                        s?.toString().orEmpty()

                    btnClearHeaderSearch.visibility =
                        if (value.isBlank()) {
                            View.GONE
                        } else {
                            View.VISIBLE
                        }

                    searchField.onTextChanged(value)
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }

        etHeaderSearch.addTextChangedListener(watcher)
        etHeaderSearch.tag = watcher

        btnClearHeaderSearch.setOnClickListener {
            etHeaderSearch.setText("")
            searchField.onClearClick?.invoke()
        }
    }
}

private const val ACTION_ENABLED_ALPHA = 1f
private const val ACTION_DISABLED_ALPHA = 0.45f

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
