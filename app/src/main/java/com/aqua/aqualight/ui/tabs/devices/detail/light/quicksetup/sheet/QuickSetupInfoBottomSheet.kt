package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.sheet

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetLightQuickSetupInfoBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class QuickSetupInfoBottomSheet(
    private val dialog: BottomSheetDialog,
    private val binding: BottomSheetLightQuickSetupInfoBinding
) {

    private var lastTouchY: Float = 0f

    fun show(
        title: String,
        subtitle: String,
        items: List<String>
    ) {
        if (items.isEmpty()) {
            return
        }

        binding.tvSheetTitle.text = title
        binding.tvSheetSubtitle.text = subtitle

        renderRows(items)

        binding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        configureScrollBehavior()
        configureBottomSheetOnShow()

        dialog.show()

        adjustScrollHeight()
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

    private fun configureBottomSheetOnShow() {
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener

            runCatching {
                val behavior = BottomSheetBehavior.from(bottomSheet)

                behavior.skipCollapsed = true
                behavior.isHideable = true
                behavior.isDraggable = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun configureScrollBehavior() {
        binding.infoScrollView.isVerticalScrollBarEnabled = false
        binding.infoScrollView.overScrollMode = View.OVER_SCROLL_NEVER
        binding.infoScrollView.isNestedScrollingEnabled = true

        binding.infoScrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchY = event.rawY
                    requestParentInterceptDisallow(true)
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - lastTouchY
                    val draggingDown = deltaY > 0f
                    val canScrollUp = binding.infoScrollView.canScrollVertically(-1)

                    val shouldSheetHandleGesture =
                        draggingDown && !canScrollUp

                    requestParentInterceptDisallow(
                        disallow = !shouldSheetHandleGesture
                    )

                    lastTouchY = event.rawY
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    requestParentInterceptDisallow(false)
                }
            }

            false
        }
    }

    private fun requestParentInterceptDisallow(
        disallow: Boolean
    ) {
        runCatching {
            binding.infoScrollView.parent?.requestDisallowInterceptTouchEvent(disallow)
            binding.root.parent?.requestDisallowInterceptTouchEvent(disallow)
        }
    }

    private fun adjustScrollHeight() {
        binding.infoScrollView.post {
            val screenHeight =
                binding.root.resources.displayMetrics.heightPixels

            val maxScrollHeight =
                (screenHeight * MAX_SCROLL_HEIGHT_RATIO).toInt()

            val contentHeight =
                binding.infoRowsContainer.measuredHeight

            if (contentHeight <= 0) {
                return@post
            }

            val targetHeight =
                contentHeight.coerceAtMost(maxScrollHeight)

            binding.infoScrollView.layoutParams =
                binding.infoScrollView.layoutParams.apply {
                    height = targetHeight
                }

            binding.infoScrollView.requestLayout()
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
            maxLines = 5
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
        private const val MAX_SCROLL_HEIGHT_RATIO = 0.52f

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