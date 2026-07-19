package com.aqua.aqualight.base.accessibility

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Replaces only explicitly tagged labels when the system font scale approaches 200%.
 * Normal font scales keep the original XML copy and therefore the existing visual design.
 */
object LargeFontCompactCopyInstaller {

    private const val COMPACT_TAG_PREFIX = "aqua_large_font_text:"
    private const val COMPACT_FONT_SCALE_THRESHOLD = 1.8f

    fun install(root: View) {
        if (root.resources.configuration.fontScale < COMPACT_FONT_SCALE_THRESHOLD) return

        fun visit(view: View) {
            val compactResourceName = (view.tag as? String)
                ?.takeIf { tag -> tag.startsWith(COMPACT_TAG_PREFIX) }
                ?.removePrefix(COMPACT_TAG_PREFIX)
                ?.takeIf(String::isNotBlank)

            if (view is TextView && compactResourceName != null) {
                val compactTextRes = view.resources.getIdentifier(
                    compactResourceName,
                    "string",
                    view.context.packageName
                )
                check(compactTextRes != 0) {
                    "Missing compact large-font string: $compactResourceName"
                }
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
}
