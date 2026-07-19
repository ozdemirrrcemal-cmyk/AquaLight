package com.aqua.aqualight.base.accessibility

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import com.aqua.aqualight.R

/**
 * Replaces only explicitly tagged labels when the system font scale approaches 200%.
 * Normal font scales keep the original XML copy and therefore the existing visual design.
 */
object LargeFontCompactCopyInstaller {

    private const val COMPACT_FONT_SCALE_THRESHOLD = 1.8f

    fun install(root: View) {
        if (root.resources.configuration.fontScale < COMPACT_FONT_SCALE_THRESHOLD) return

        fun visit(view: View) {
            val compactTextRes = compactTextResource(
                resources = view.resources,
                tag = view.tag
            )
            if (view is TextView && compactTextRes != null) {
                view.setText(compactTextRes)
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))
                }
            }
        }

        visit(root)
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
