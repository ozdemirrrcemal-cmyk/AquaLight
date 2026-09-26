package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.widget.LinearLayout
import android.widget.Space
import androidx.fragment.app.Fragment

internal class WaterAnalysisParameterGrid(
    private val fragment: Fragment
) {
    fun createRow(): LinearLayout =
        LinearLayout(fragment.requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }

    fun rowLayoutParams(
        rowIndex: Int,
        rowSpacing: Int
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            if (rowIndex > 0) topMargin = rowSpacing
        }

    fun cellLayoutParams(
        column: Int,
        spacing: Int
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginEnd = if (column == 0) spacing else 0
            marginStart = if (column == 0) 0 else spacing
        }

    fun addSpacer(row: LinearLayout, spacing: Int) {
        row.addView(
            Space(fragment.requireContext()),
            LinearLayout.LayoutParams(0, 1, 1f).apply {
                marginStart = spacing
            }
        )
    }
}
