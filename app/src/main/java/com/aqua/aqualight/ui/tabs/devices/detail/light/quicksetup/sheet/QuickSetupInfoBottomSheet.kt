package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.sheet

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetLightQuickSetupInfoBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class QuickSetupInfoBottomSheet(
    private val dialog: BottomSheetDialog,
    private val binding: BottomSheetLightQuickSetupInfoBinding
) {

    fun show(
        title: String,
        subtitle: String,
        items: List<String>
    ) {
        binding.tvSheetTitle.text = title
        binding.tvSheetSubtitle.text = subtitle

        renderRows(items)

        binding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun renderRows(
        items: List<String>
    ) {
        binding.infoRowsContainer.removeAllViews()

        items.forEachIndexed { index, text ->
            binding.infoRowsContainer.addView(
                createInfoRow(
                    index = index + 1,
                    text = text
                )
            )
        }
    }

    private fun createInfoRow(
        index: Int,
        text: String
    ): LinearLayout {
        val context = binding.root.context

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_light_program_time_panel)
            setPadding(
                11.dp(context),
                9.dp(context),
                11.dp(context),
                9.dp(context)
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    8.dp(context)
                )
            }
        }

        val indexBadge = TextView(context).apply {
            setTextAppearance(R.style.TextAppearance_Aqua_Light_Caption)
            this.text = index.toString()
            gravity = Gravity.CENTER
            includeFontPadding = false
            setBackgroundResource(R.drawable.bg_light_action_icon)

            layoutParams = LinearLayout.LayoutParams(
                28.dp(context),
                28.dp(context)
            ).apply {
                setMargins(
                    0,
                    0,
                    11.dp(context),
                    0
                )
            }
        }

        val label = TextView(context).apply {
            setTextAppearance(R.style.TextAppearance_Aqua_Light_SheetRowSubtitle)
            this.text = text
            includeFontPadding = false
            setLineSpacing(
                0f,
                1.05f
            )
            maxLines = 4
            ellipsize = null

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        row.addView(indexBadge)
        row.addView(label)

        return row
    }

    private fun Int.dp(
        context: Context
    ): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    companion object {

        fun create(
            context: Context
        ): QuickSetupInfoBottomSheet {
            val dialog = BottomSheetDialog(context)

            val binding = BottomSheetLightQuickSetupInfoBinding.inflate(
    LayoutInflater.from(context)
)

            dialog.setContentView(binding.root)

            return QuickSetupInfoBottomSheet(
                dialog = dialog,
                binding = binding
            )
        }
    }
}