package com.aqua.aqualight.ui.common.header

import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.LayoutAquaHeaderBinding

internal object AquaHeaderComponentBinder {

    fun bind(
        binding: LayoutAquaHeaderBinding,
        fragment: Fragment,
        config: AquaHeaderConfig
    ) {
        bindTitleBackAndStatus(binding, fragment, config)
        bindActions(binding, config.actions)
        bindFilledIconAction(binding, config.filledIconAction)
        bindCardIconAction(binding, fragment, config.cardIconAction)
        bindPillTextAction(binding, fragment, config.pillTextAction)
        bindScoreBadge(binding, config.scoreBadge)
        bindPrimaryAction(binding, config.primaryAction)
        bindSearchField(binding, config.searchField)
    }

    private fun bindTitleBackAndStatus(
        binding: LayoutAquaHeaderBinding,
        fragment: Fragment,
        config: AquaHeaderConfig
    ) {
        val navController = fragment.findNavController()
        binding.tvTitle.text = config.titleOverride
            ?: navController.currentDestination?.label?.toString().orEmpty()
        binding.btnBack.visibility = if (config.showBackButton) View.VISIBLE else View.GONE
        binding.btnBack.setOnClickListener(
            if (config.showBackButton) {
                View.OnClickListener {
                    config.onBackClick?.invoke() ?: navController.popBackStack()
                }
            } else {
                null
            }
        )

        val statusIcon = config.statusIcon
        binding.imgStatusIcon.visibility = if (statusIcon == null) View.GONE else View.VISIBLE
        if (statusIcon == null) {
            binding.imgStatusIcon.setImageDrawable(null)
            binding.imgStatusIcon.imageTintList = null
            binding.imgStatusIcon.contentDescription = null
        } else {
            binding.imgStatusIcon.setImageResource(statusIcon.iconRes)
            binding.imgStatusIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(fragment.requireContext(), statusIcon.tintColorRes)
            )
            binding.imgStatusIcon.contentDescription = statusIcon.contentDescription
        }
    }

    private fun bindActions(
        binding: LayoutAquaHeaderBinding,
        actions: List<AquaHeaderAction>
    ) {
        binding.headerActionsContainer.visibility =
            if (actions.isEmpty()) View.GONE else View.VISIBLE
        listOf(
            binding.btnActionOne,
            binding.btnActionTwo,
            binding.btnActionThree
        ).forEachIndexed { index, button ->
            bindActionButton(button, actions.getOrNull(index))
        }
    }

    private fun bindActionButton(button: ImageButton, action: AquaHeaderAction?) {
        if (action == null) {
            button.visibility = View.GONE
            button.setOnClickListener(null)
            button.setImageDrawable(null)
            button.contentDescription = null
            button.isEnabled = true
            button.alpha = ACTION_ENABLED_ALPHA
        } else {
            button.visibility = View.VISIBLE
            button.setImageResource(action.iconRes)
            button.contentDescription = action.contentDescription
            button.isEnabled = action.enabled
            button.alpha = if (action.enabled) ACTION_ENABLED_ALPHA else ACTION_DISABLED_ALPHA
            button.setOnClickListener { action.onClick() }
        }
    }

    private fun bindFilledIconAction(
        binding: LayoutAquaHeaderBinding,
        action: AquaHeaderFilledIconAction?
    ) {
        val button = binding.btnFilledIconAction
        if (action == null) {
            button.visibility = View.GONE
            button.setImageDrawable(null)
            button.contentDescription = null
            button.isEnabled = true
            button.alpha = ACTION_ENABLED_ALPHA
            button.setOnClickListener(null)
        } else {
            button.visibility = View.VISIBLE
            button.setImageResource(action.iconRes)
            binding.btnFilledIconAction.contentDescription = action.contentDescription
            button.isEnabled = action.enabled
            button.alpha = if (action.enabled) ACTION_ENABLED_ALPHA else ACTION_DISABLED_ALPHA
            button.setOnClickListener { action.onClick() }
        }
    }

    private fun bindCardIconAction(
        binding: LayoutAquaHeaderBinding,
        fragment: Fragment,
        action: AquaHeaderCardIconAction?
    ) {
        val button = binding.btnCardIconAction
        if (action == null) {
            button.visibility = View.GONE
            button.contentDescription = null
            button.isClickable = false
            button.isFocusable = false
            button.isEnabled = true
            button.alpha = ACTION_ENABLED_ALPHA
            button.setOnClickListener(null)
            binding.ivCardIconAction.setImageDrawable(null)
        } else {
            button.visibility = View.VISIBLE
            button.setCardBackgroundColor(
                action.backgroundColor
                    ?: fragment.resolveCardIconActionBackgroundColor(action.tone)
            )
            button.strokeColor = action.strokeColor
                ?: fragment.resolveCardIconActionStrokeColor(action.tone)
            binding.btnCardIconAction.contentDescription = action.contentDescription
            button.isClickable = action.enabled
            button.isFocusable = action.enabled
            button.isEnabled = action.enabled
            button.alpha = if (action.enabled) ACTION_ENABLED_ALPHA else ACTION_DISABLED_ALPHA
            binding.ivCardIconAction.setImageResource(action.iconRes)
            binding.ivCardIconAction.imageTintList = ColorStateList.valueOf(
                action.iconTintColor ?: ContextCompat.getColor(
                    fragment.requireContext(),
                    R.color.aqua_content_on_dark
                )
            )
            button.setOnClickListener { action.onClick() }
        }
    }

    private fun bindPillTextAction(
        binding: LayoutAquaHeaderBinding,
        fragment: Fragment,
        action: AquaHeaderPillTextAction?
    ) {
        val button = binding.btnPillTextAction
        if (action == null) {
            button.visibility = View.GONE
            button.text = null
            button.contentDescription = null
            button.isEnabled = true
            button.alpha = ACTION_ENABLED_ALPHA
            button.setOnClickListener(null)
        } else {
            button.visibility = View.VISIBLE
            button.text = action.text
            button.contentDescription = action.contentDescription
            button.setTextColor(
                action.textColor ?: ContextCompat.getColor(
                    fragment.requireContext(),
                    R.color.aqua_content_on_dark
                )
            )
            button.setBackgroundResource(action.backgroundRes)
            button.isEnabled = action.enabled
            button.alpha = if (action.enabled) ACTION_ENABLED_ALPHA else ACTION_DISABLED_ALPHA
            button.setOnClickListener { action.onClick() }
        }
    }

    private fun bindScoreBadge(
        binding: LayoutAquaHeaderBinding,
        scoreBadge: AquaHeaderScoreBadge?
    ) {
        val container = binding.scoreContainer
        if (scoreBadge == null) {
            container.visibility = View.GONE
            binding.tvScore.text = null
            container.contentDescription = null
            container.isClickable = false
            container.isFocusable = false
            container.setOnClickListener(null)
        } else {
            container.visibility = View.VISIBLE
            binding.tvScore.text = scoreBadge.text
            binding.tvScore.setTextColor(scoreBadge.textColor)
            container.strokeColor = scoreBadge.strokeColor
            container.contentDescription = scoreBadge.contentDescription
            container.isClickable = scoreBadge.onClick != null
            container.isFocusable = scoreBadge.onClick != null
            container.setOnClickListener(scoreBadge.onClick?.let { onClick ->
                View.OnClickListener { onClick() }
            })
        }
    }

    private fun bindPrimaryAction(
        binding: LayoutAquaHeaderBinding,
        action: AquaHeaderPrimaryAction?
    ) {
        val button = binding.btnPrimaryAction
        if (action == null) {
            button.visibility = View.GONE
            button.text = null
            button.contentDescription = null
            button.setOnClickListener(null)
        } else {
            button.visibility = View.VISIBLE
            button.text = action.text
            button.contentDescription = action.contentDescription
            button.setOnClickListener { action.onClick() }
        }
    }

    private fun bindSearchField(
        binding: LayoutAquaHeaderBinding,
        searchField: AquaHeaderSearchField?
    ) {
        (binding.etHeaderSearch.tag as? TextWatcher)?.let { oldWatcher ->
            binding.etHeaderSearch.removeTextChangedListener(oldWatcher)
            binding.etHeaderSearch.tag = null
        }
        if (searchField == null) {
            binding.searchContainer.visibility = View.GONE
            binding.titleStatusContainer.visibility = View.VISIBLE
            binding.headerRightContainer.visibility = View.VISIBLE
            binding.etHeaderSearch.setText("")
            binding.btnClearHeaderSearch.visibility = View.GONE
            binding.btnClearHeaderSearch.setOnClickListener(null)
        } else {
            binding.searchContainer.visibility = View.VISIBLE
            binding.titleStatusContainer.visibility = View.GONE
            binding.headerRightContainer.visibility = View.GONE
            binding.etHeaderSearch.hint = searchField.hint
            if (binding.etHeaderSearch.text.toString() != searchField.text) {
                binding.etHeaderSearch.setText(searchField.text)
                binding.etHeaderSearch.setSelection(binding.etHeaderSearch.text.length)
            }
            binding.btnClearHeaderSearch.visibility =
                if (binding.etHeaderSearch.text.isNullOrBlank()) View.GONE else View.VISIBLE
            val watcher = createSearchWatcher(binding, searchField)
            binding.etHeaderSearch.addTextChangedListener(watcher)
            binding.etHeaderSearch.tag = watcher
            binding.btnClearHeaderSearch.setOnClickListener {
                binding.etHeaderSearch.setText("")
                searchField.onClearClick?.invoke()
            }
        }
    }

    private fun createSearchWatcher(
        binding: LayoutAquaHeaderBinding,
        searchField: AquaHeaderSearchField
    ) = object : TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence?,
            start: Int,
            count: Int,
            after: Int
        ) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val value = s?.toString().orEmpty()
            binding.btnClearHeaderSearch.visibility =
                if (value.isBlank()) View.GONE else View.VISIBLE
            searchField.onTextChanged(value)
        }

        override fun afterTextChanged(s: Editable?) = Unit
    }

    private const val ACTION_ENABLED_ALPHA = 1f
    private const val ACTION_DISABLED_ALPHA = 0.45f
}
