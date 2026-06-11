package com.aqua.aqualight.ui.common.bottomsheet

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.care.catalog.CareTaskTypeCatalog
import com.aqua.aqualight.data.care.catalog.CareTaskTypeDefinition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView

class CareTaskTypeBottomSheetFragment : BottomSheetDialogFragment() {

  private val sheetTitle: String
    get() = arguments?.getString(ARG_TITLE) ?: DEFAULT_TITLE

  private val resultRequestKey: String
    get() = arguments?.getString(ARG_RESULT_REQUEST_KEY) ?: REQUEST_KEY_DEFAULT

  private val selectedType: CareTaskType?
    get() {
      val typeName = arguments?.getString(ARG_SELECTED_TYPE) ?: return null

      return runCatching {
        CareTaskType.valueOf(typeName)
      }.getOrNull()
    }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return inflater.inflate(
      R.layout.bottom_sheet_care_task_type,
      container,
      false
    )
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    view.findViewById<TextView>(
      R.id.tvTaskTypeBottomSheetTitle
    ).text = sheetTitle

    view.findViewById<NestedScrollView>(
      R.id.taskTypeScrollView
    ).apply {
      isNestedScrollingEnabled = true
      overScrollMode = View.OVER_SCROLL_NEVER
    }

    val typeOptionsContainer = view.findViewById<LinearLayout>(
      R.id.typeOptionsContainer
    )

    renderTaskTypesIntoContainer(typeOptionsContainer)
  }

  override fun onStart() {
    super.onStart()

    val bottomSheetDialog = dialog as? BottomSheetDialog ?: return

    val bottomSheet = bottomSheetDialog.findViewById<View>(
      com.google.android.material.R.id.design_bottom_sheet
    ) ?: return

    bottomSheet.setBackgroundColor(Color.TRANSPARENT)

    val sheetHeight = (
      resources.displayMetrics.heightPixels * 0.92f
    ).toInt()

    bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
      height = sheetHeight
    }

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

  private fun renderTaskTypesIntoContainer(
    container: LinearLayout
  ) {
    container.removeAllViews()

    var renderedCategoryCount = 0

    CareTaskTypeCatalog.categories.forEach { category ->

      val items = CareTaskTypeCatalog.byCategory(category)

      if (items.isEmpty()) {
        return@forEach
      }

      container.addView(
        createCategoryTitle(
          title = category,
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
        grid.addView(
          createTaskTypeCard(
            item = item,
            itemIndex = itemIndex
          )
        )
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
      textSize = 12f
      setTextColor(Color.parseColor("#8FA4BE"))
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false

      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = if (isFirstCategory) {
          2.dp()
        } else {
          14.dp()
        }

        bottomMargin = 9.dp()
      }
    }
  }

  private fun createTaskTypeCard(
    item: CareTaskTypeDefinition,
    itemIndex: Int
  ): View {
    val selected = item.type == selectedType
    val accentColor = parseColorOrDefault(item.accentColor)
    val isRightColumn = itemIndex % 2 == 1

    val card = MaterialCardView(requireContext()).apply {
      radius = 16.dp().toFloat()
      strokeWidth = 1.dp()

      strokeColor = if (selected) {
        accentColor
      } else {
        Color.parseColor("#223A57")
      }

      setCardBackgroundColor(
        if (selected) {
          Color.parseColor("#1C3D63")
        } else {
          Color.parseColor("#10233A")
        }
      )

      cardElevation = 0f
      useCompatPadding = false
      isClickable = true
      isFocusable = true

      layoutParams = GridLayout.LayoutParams().apply {
        width = 0
        height = 58.dp()

        columnSpec = GridLayout.spec(
          GridLayout.UNDEFINED,
          1f
        )

        setMargins(
          0,
          0,
          if (isRightColumn) {
            0
          } else {
            8.dp()
          },
          8.dp()
        )
      }

      setOnClickListener {
        publishSelectedTaskType(item)
        dismiss()
      }
    }

    val row = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL

      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )

      setPadding(
        9.dp(),
        8.dp(),
        9.dp(),
        8.dp()
      )
    }

    val iconContainer = FrameLayout(requireContext()).apply {
      background = createIconBackground(
        color = accentColor,
        selected = selected
      )

      layoutParams = LinearLayout.LayoutParams(
        36.dp(),
        36.dp()
      )
    }

    val icon = ImageView(requireContext()).apply {
      setImageResource(item.iconRes)
      setColorFilter(Color.WHITE)
      scaleType = ImageView.ScaleType.CENTER_INSIDE

      layoutParams = FrameLayout.LayoutParams(
        20.dp(),
        20.dp(),
        Gravity.CENTER
      )
    }

    iconContainer.addView(icon)

    val title = TextView(requireContext()).apply {
      text = item.title
      textSize = 12.2f
      setTextColor(Color.WHITE)

      setTypeface(
        null,
        if (selected) {
          Typeface.BOLD
        } else {
          Typeface.NORMAL
        }
      )

      includeFontPadding = false
      maxLines = 2

      layoutParams = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
      ).apply {
        marginStart = 10.dp()
      }
    }

    row.addView(iconContainer)
    row.addView(title)

    card.addView(row)

    return card
  }

  private fun publishSelectedTaskType(
    item: CareTaskTypeDefinition
  ) {
    parentFragmentManager.setFragmentResult(
      resultRequestKey,
      bundleOf(
        RESULT_TASK_TYPE to item.type.name,
        RESULT_TASK_TYPE_TITLE to item.title,
        RESULT_TASK_TYPE_CATEGORY to item.category
      )
    )
  }

  private fun createIconBackground(
    color: Int,
    selected: Boolean
  ): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 13.dp().toFloat()

      setColor(
        applyAlpha(
          color = color,
          alpha = if (selected) {
            0.34f
          } else {
            0.22f
          }
        )
      )

      setStroke(
        1.dp(),
        applyAlpha(
          color = color,
          alpha = if (selected) {
            0.9f
          } else {
            0.55f
          }
        )
      )
    }
  }

  private fun applyAlpha(
    color: Int,
    alpha: Float
  ): Int {
    return Color.argb(
      (255 * alpha).toInt(),
      Color.red(color),
      Color.green(color),
      Color.blue(color)
    )
  }

  private fun parseColorOrDefault(
    color: String
  ): Int {
    return runCatching {
      Color.parseColor(color)
    }.getOrDefault(
      Color.parseColor("#2196F3")
    )
  }

  private fun Int.dp(): Int {
    return (this * resources.displayMetrics.density).toInt()
  }

  companion object {
    private const val TAG = "CareTaskTypeBottomSheetFragment"

    private const val ARG_TITLE = "title"
    private const val ARG_RESULT_REQUEST_KEY = "resultRequestKey"
    private const val ARG_SELECTED_TYPE = "selectedType"

    private const val DEFAULT_TITLE = "Select Task Type"

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
      if (fragmentManager.findFragmentByTag(TAG) != null) {
        return
      }

      val args = bundleOf(
        ARG_TITLE to title,
        ARG_RESULT_REQUEST_KEY to resultRequestKey
      )

      selectedType?.let {
        args.putString(
          ARG_SELECTED_TYPE,
          it.name
        )
      }

      CareTaskTypeBottomSheetFragment().apply {
        arguments = args
      }.show(
        fragmentManager,
        TAG
      )
    }
  }
}