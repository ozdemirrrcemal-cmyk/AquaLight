package com.aqua.aqualight.ui.common.bottomsheet

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTypeCatalog
import com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTypeDefinition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CareTaskTypeBottomSheetFragment : BottomSheetDialogFragment() {

    private val sheetTitle: String
        get() = arguments?.getString(ARG_TITLE) ?: getString(R.string.maintenance_select_task_type)

    private val resultRequestKey: String
        get() = arguments?.getString(ARG_RESULT_REQUEST_KEY) ?: REQUEST_KEY_DEFAULT

    private val selectedType: CareTaskType?
        get() {
            val typeName = arguments?.getString(ARG_SELECTED_TYPE) ?: return null
            return runCatching { CareTaskType.valueOf(typeName) }.getOrNull()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_care_task_type, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvTaskTypeBottomSheetTitle).text = sheetTitle
        view.findViewById<NestedScrollView>(R.id.taskTypeScrollView).apply {
            isNestedScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        renderTaskTypesIntoContainer(view.findViewById(R.id.typeOptionsContainer))
    }

    override fun onStart() {
        super.onStart()
        val bottomSheetDialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        val sheetHeight = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply { height = sheetHeight }
        bottomSheet.requestLayout()
        bottomSheetDialog.behavior.apply {
            isFitToContents = true
            state = BottomSheetBehavior.STATE_EXPANDED
            peekHeight = sheetHeight
            skipCollapsed = true
            isHideable = true
            isDraggable = true
        }
    }

    private fun renderTaskTypesIntoContainer(container: LinearLayout) {
        container.removeAllViews()
        var renderedCategoryCount = 0

        CareTaskTypeCatalog.categoryResIds.forEach { categoryRes ->
            val items = CareTaskTypeCatalog.byCategoryRes(categoryRes)
            if (items.isEmpty()) return@forEach

            container.addView(
                createCategoryTitle(
                    title = getString(categoryRes),
                    isFirstCategory = renderedCategoryCount == 0
                )
            )
            val grid = GridLayout(requireContext()).apply {
                columnCount = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            items.forEachIndexed { itemIndex, item ->
                grid.addView(createTaskTypeCard(item, itemIndex))
            }
            container.addView(grid)
            renderedCategoryCount++
        }
    }

    private fun createCategoryTitle(
        title: String,
        isFirstCategory: Boolean
    ): View {
        return TextView(requireContext()).apply {
            text = title
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.care_sheet_category_text_size)
            )
            setTextColor(
                ContextCompat.getColor(requireContext(), R.color.aqua_card_text_secondary)
            )
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(
                    if (isFirstCategory) {
                        R.dimen.care_sheet_category_first_top_margin
                    } else {
                        R.dimen.care_sheet_category_top_margin
                    }
                )
                bottomMargin = resources.getDimensionPixelSize(
                    R.dimen.care_sheet_category_bottom_margin
                )
            }
        }
    }

    private fun createTaskTypeCard(
        item: CareTaskTypeDefinition,
        itemIndex: Int
    ): View {
        val selected = item.type == selectedType
        val accentColor = ContextCompat.getColor(requireContext(), item.accentColorRes)
        val isRightColumn = itemIndex % 2 == 1
        val horizontalPadding = resources.getDimensionPixelSize(
            R.dimen.care_sheet_card_horizontal_padding
        )
        val verticalPadding = resources.getDimensionPixelSize(
            R.dimen.care_sheet_card_vertical_padding
        )
        val gridGap = resources.getDimensionPixelSize(R.dimen.care_sheet_grid_gap)

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isSelected = selected
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_aqua_selection_row_compact
            )
            setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
            )
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = resources.getDimensionPixelSize(R.dimen.care_sheet_card_height)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(0, 0, if (isRightColumn) 0 else gridGap, gridGap)
            }
            setOnClickListener {
                publishSelectedTaskType(item)
                dismiss()
            }
        }

        val iconContainerSize = resources.getDimensionPixelSize(
            R.dimen.care_sheet_icon_container_size
        )
        val iconContainer = FrameLayout(requireContext()).apply {
            background = createIconBackground(accentColor, selected)
            layoutParams = LinearLayout.LayoutParams(iconContainerSize, iconContainerSize)
        }

        val iconSize = resources.getDimensionPixelSize(R.dimen.care_sheet_icon_size)
        val icon = ImageView(requireContext()).apply {
            setImageResource(item.iconRes)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        }
        iconContainer.addView(icon)

        val title = TextView(requireContext()).apply {
            text = item.title(requireContext())
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.care_sheet_title_text_size)
            )
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.aqua_card_text_primary
                    else R.color.aqua_card_text_secondary
                )
            )
            setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = resources.getDimensionPixelSize(
                    R.dimen.care_sheet_title_start_margin
                )
            }
        }

        card.addView(iconContainer)
        card.addView(title)
        return card
    }

    private fun publishSelectedTaskType(item: CareTaskTypeDefinition) {
        parentFragmentManager.setFragmentResult(
            resultRequestKey,
            bundleOf(
                RESULT_TASK_TYPE to item.type.name,
                RESULT_TASK_TYPE_TITLE to item.title(requireContext()),
                RESULT_TASK_TYPE_CATEGORY to item.category(requireContext())
            )
        )
    }

    private fun createIconBackground(
        color: Int,
        selected: Boolean
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = resources.getDimension(R.dimen.care_sheet_icon_radius)
            setColor(
                applyAlpha(
                    color = color,
                    alpha = if (selected) SELECTED_BACKGROUND_ALPHA else BACKGROUND_ALPHA
                )
            )
            setStroke(
                resources.getDimensionPixelSize(R.dimen.care_sheet_icon_stroke),
                applyAlpha(
                    color = color,
                    alpha = if (selected) SELECTED_STROKE_ALPHA else STROKE_ALPHA
                )
            )
        }
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        return Color.argb(
            (255 * alpha).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    companion object {
        private const val TAG = "CareTaskTypeBottomSheetFragment"
        private const val ARG_TITLE = "title"
        private const val ARG_RESULT_REQUEST_KEY = "resultRequestKey"
        private const val ARG_SELECTED_TYPE = "selectedType"
        private const val SHEET_HEIGHT_RATIO = 0.92f
        private const val SELECTED_BACKGROUND_ALPHA = 0.34f
        private const val BACKGROUND_ALPHA = 0.22f
        private const val SELECTED_STROKE_ALPHA = 0.9f
        private const val STROKE_ALPHA = 0.55f

        const val REQUEST_KEY_DEFAULT = "care_task_type_result"
        const val REQUEST_KEY_ADD_ACTIVITY = "care_task_type_add_activity_result"
        const val REQUEST_KEY_SELECT_TASK_TYPE = "care_task_type_select_task_type_result"

        const val RESULT_TASK_TYPE = "taskType"
        const val RESULT_TASK_TYPE_TITLE = "taskTypeTitle"
        const val RESULT_TASK_TYPE_CATEGORY = "taskTypeCategory"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            resultRequestKey: String = REQUEST_KEY_DEFAULT,
            selectedType: CareTaskType? = null
        ) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            val args = bundleOf(
                ARG_TITLE to title,
                ARG_RESULT_REQUEST_KEY to resultRequestKey
            )
            selectedType?.let { args.putString(ARG_SELECTED_TYPE, it.name) }
            CareTaskTypeBottomSheetFragment().apply {
                arguments = args
            }.show(fragmentManager, TAG)
        }
    }
}
