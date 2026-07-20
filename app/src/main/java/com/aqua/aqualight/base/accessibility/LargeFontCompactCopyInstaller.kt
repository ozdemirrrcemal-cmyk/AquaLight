package com.aqua.aqualight.base.accessibility

import android.content.res.Resources
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import com.aqua.aqualight.R

/**
 * Replaces only explicitly protected copy when the system font scale approaches 200%.
 * Normal font scales keep the original XML/runtime copy and therefore the existing visual design.
 */
object LargeFontCompactCopyInstaller {

    fun install(root: View) {
        val fontScale = root.resources.configuration.fontScale
        if (!LargeFontCompactCopyPolicy.shouldUseCompactCopy(fontScale)) return

        fun visit(view: View) {
            if (view is TextView) {
                compactTextResource(
                    resources = view.resources,
                    tag = view.tag
                )?.let(view::setText)

                if (view.id == R.id.btnPrimaryAction) {
                    installPrimaryActionCompactor(view)
                }
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))
                }
            }
        }

        visit(root)
    }

    private fun installPrimaryActionCompactor(view: TextView) {
        compactPrimaryAction(view)

        if (view.getTag(R.id.aqua_large_font_primary_action_watcher) is TextWatcher) {
            return
        }

        var applyingCompactText = false
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(
                sequence: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                sequence: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            override fun afterTextChanged(editable: Editable?) {
                if (applyingCompactText) return

                val compactText = compactPrimaryActionText(view)
                if (compactText.toString() == editable?.toString().orEmpty()) return

                applyingCompactText = true
                try {
                    view.text = compactText
                } finally {
                    applyingCompactText = false
                }
            }
        }

        view.addTextChangedListener(watcher)
        view.setTag(R.id.aqua_large_font_primary_action_watcher, watcher)
    }

    private fun compactPrimaryAction(view: TextView) {
        val compactText = compactPrimaryActionText(view)
        if (compactText.toString() != view.text?.toString().orEmpty()) {
            view.text = compactText
        }
    }

    private fun compactPrimaryActionText(view: TextView): CharSequence {
        return LargeFontCompactCopyPolicy.compactPrimaryActionText(
            originalText = view.text ?: "",
            plusText = view.resources.getText(R.string.common_plus),
            fontScale = view.resources.configuration.fontScale
        )
    }

    @StringRes
    private fun compactTextResource(
        resources: Resources,
        tag: Any?
    ): Int? {
        val token = (tag as? CharSequence)?.toString() ?: return null
        return when (token) {
            resources.getString(R.string.aqua_large_font_tag_maintenance_tab_next) ->
                R.string.maintenance_tab_next

            resources.getString(R.string.aqua_large_font_tag_maintenance_action_add) ->
                R.string.maintenance_action_add

            else -> null
        }
    }
}
