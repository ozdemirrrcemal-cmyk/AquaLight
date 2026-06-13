package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.databinding.FragmentTankDetailActivityBinding
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetAction
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetActionStyle
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetDetailRow
import com.aqua.aqualight.ui.common.bottomsheet.CareTaskTypeBottomSheetFragment
import com.aqua.aqualight.ui.common.bottomsheet.GlobalActionBottomSheet
import com.aqua.aqualight.ui.common.timeline.TimelineAxisView
import com.aqua.aqualight.ui.common.timeline.TimelineDayResolver
import com.aqua.aqualight.ui.common.timeline.TimelineDayStatus
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.ui.tabs.maintenance.TankActivityUiState
import com.aqua.aqualight.ui.tabs.maintenance.TankNextCareStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.google.android.material.card.MaterialCardView

class TankDetailActivityFragment : Fragment(R.layout.fragment_tank_detail_activity) {

    private var _binding: FragmentTankDetailActivityBinding? = null
    private val binding get() = _binding!!

    private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()

    private var tankId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankDetailActivityBinding.bind(view)

        setupClickListeners()
        setupCareTaskTypeResultListener()
        observeTankActivity()
    }

    private fun setupClickListeners() {
        binding.btnAddActivity.setOnClickListener {
            showAddActivitySheet()
        }
    }

    fun showAddActivitySheet() {
        CareTaskTypeBottomSheetFragment.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.aquarium_add_activity_title),
            resultRequestKey = CareTaskTypeBottomSheetFragment.REQUEST_KEY_ADD_ACTIVITY
        )
    }

    private fun setupCareTaskTypeResultListener() {
        childFragmentManager.setFragmentResultListener(
            CareTaskTypeBottomSheetFragment.REQUEST_KEY_ADD_ACTIVITY,
            viewLifecycleOwner
        ) {
            _, result ->

            val typeName = result.getString(
                CareTaskTypeBottomSheetFragment.RESULT_TASK_TYPE
            ) ?: return@setFragmentResultListener

            val selectedType = runCatching {
                CareTaskType.valueOf(typeName)
            }.getOrNull() ?: return@setFragmentResultListener

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    showGlobalLoading(true)

                    maintenanceViewModel.addCompletedActivity(
                        tankId = tankId,
                        type = selectedType,
                        completedAtMillis = System.currentTimeMillis()
                    ).join()
                } catch (exception: Exception) {
                    exception.printStackTrace()

                    showSnackBar(
                        message = getString(R.string.aquarium_error_activity_add_failed),
                        type = BaseActivity.SnackType.ERROR
                    )
                } finally {
                    showGlobalLoading(false)
                }
            }
        }
    }

    private fun observeTankActivity() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                maintenanceViewModel.tankActivityStateFlow(
                    tankId = tankId
                ).collect {
                    state ->
                    renderActivitySummary(state)
                }
            }
        }
    }

    private fun renderActivitySummary(
        state: TankActivityUiState
    ) {
        binding.tvLastTrimValue.text = state.lastTrimText
        binding.tvLastWaterChangeValue.text = state.lastWaterChangeText
        binding.tvLastFilterValue.text = state.lastFilterMaintenanceText

        if (state.nextCareTask == null) {
            binding.tvNextCareTaskTitle.text = getString(R.string.aquarium_no_value_placeholder)
            binding.tvNextCareValue.text = getString(R.string.aquarium_no_upcoming_care)
            binding.tvNextCareValue.setTextColor(
                Color.parseColor("#8FA4BE")
            )
        } else {
            binding.tvNextCareTaskTitle.text = state.nextCareTask.title
            binding.tvNextCareValue.text = state.nextCareText
            binding.tvNextCareValue.setTextColor(
                getNextCareStatusColor(state.nextCareStatus)
            )
        }

        renderActivityTimeline(
            tasks = state.completedTasks
        )
    }

    private fun getNextCareStatusColor(
        status: TankNextCareStatus
    ): Int {
        return when (status) {
            TankNextCareStatus.OVERDUE -> {
                Color.parseColor("#D85C5C")
            }

            TankNextCareStatus.TODAY -> {
                Color.parseColor("#F2C94C")
            }

            TankNextCareStatus.TOMORROW -> {
                Color.parseColor("#5FD6B4")
            }

            TankNextCareStatus.NONE,
            TankNextCareStatus.FUTURE -> {
                Color.parseColor("#8FA4BE")
            }
        }
    }

    private fun renderActivityTimeline(
        tasks: List<CareTaskUi>
    ) {
        binding.activityTimelineContainer.removeAllViews()

        val hasActivity = tasks.isNotEmpty()

        binding.tvTimelineEmpty.isVisible = !hasActivity
        binding.activityTimelineContainer.isVisible = hasActivity

        if (!hasActivity) {
            return
        }

        val groupedTasks = tasks
        .sortedByDescending {
            task ->
            task.completedAtMillis ?: task.dueAtMillis
        }
        .groupBy {
            task ->
            formatActivityDateKey(
                task.completedAtMillis ?: task.dueAtMillis
            )
        }

        groupedTasks.forEach {
            (_, dayTasks) ->
            val firstTask = dayTasks.firstOrNull() ?: return@forEach
            val dateMillis = firstTask.completedAtMillis ?: firstTask.dueAtMillis

            binding.activityTimelineContainer.addView(
                createActivityDateHeader(
                    millis = dateMillis
                )
            )

            dayTasks.forEach {
                task ->
                binding.activityTimelineContainer.addView(
                    createActivityTaskRow(task)
                )
            }
        }
    }

    private fun showActivityTaskActionBottomSheet(
        task: CareTaskUi
    ) {
        val completedAt = task.completedAtMillis ?: task.dueAtMillis

        GlobalActionBottomSheet.show(
            context = requireContext(),
            title = task.title,
            message = getString(R.string.aquarium_completed_activity_record),
            details = listOf(
                BottomSheetDetailRow(
                    label = getString(R.string.aquarium_completed_at_label),
                    value = formatActivityDateTime(completedAt)
                ),
                BottomSheetDetailRow(
                    label = getString(R.string.aquarium_source_label),
                    value = task.sourceLabel.ifBlank {
                        "-"
                    }
                ),
                BottomSheetDetailRow(
                    label = getString(R.string.aquarium_status_label),
                    value = getString(R.string.aquarium_completed_label)
                )
            ),
            actions = listOf(
                BottomSheetAction(
                    text = getString(R.string.aquarium_change_date_action),
                    style = BottomSheetActionStyle.PRIMARY,
                    onClick = {
                        showChangeActivityDatePicker(task)
                    }
                ),
                BottomSheetAction(
                    text = getString(R.string.common_delete),
                    style = BottomSheetActionStyle.DANGER,
                    onClick = {
                        showDeleteActivityTaskDialog(task)
                    }
                )
            )
        )
    }

    private fun showChangeActivityDatePicker(
        task: CareTaskUi
    ) {
        val currentMillis = task.completedAtMillis ?: task.dueAtMillis

        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentMillis
        }

        DatePickerDialog(
            requireContext(),
            {
                _, year, month, dayOfMonth ->

                calendar.set(
                    Calendar.YEAR,
                    year
                )

                calendar.set(
                    Calendar.MONTH,
                    month
                )

                calendar.set(
                    Calendar.DAY_OF_MONTH,
                    dayOfMonth
                )

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        showGlobalLoading(true)

                        maintenanceViewModel.updateCompletedTaskDate(
                            taskId = task.id,
                            completedAtMillis = calendar.timeInMillis
                        ).join()
                    } catch (exception: Exception) {
                        exception.printStackTrace()

                        showSnackBar(
                            message = getString(R.string.aquarium_error_activity_date_update_failed),
                            type = BaseActivity.SnackType.ERROR
                        )
                    } finally {
                        showGlobalLoading(false)
                    }
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showDeleteActivityTaskDialog(
        task: CareTaskUi
    ) {
        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = getString(R.string.aquarium_delete_activity_title),
            message = getString(R.string.aquarium_delete_activity_message, task.title),
            confirmTextResId = R.string.confirm,
            cancelTextResId = R.string.cancel,
            onConfirm = {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        showGlobalLoading(true)

                        maintenanceViewModel.deleteTask(
                            taskId = task.id
                        ).join()
                    } catch (exception: Exception) {
                        exception.printStackTrace()

                        showSnackBar(
                            message = getString(R.string.aquarium_error_activity_delete_failed),
                            type = BaseActivity.SnackType.ERROR
                        )
                    } finally {
                        showGlobalLoading(false)
                    }
                }
            }
        )
    }

    private fun createActivityDateHeader(
        millis: Long
    ): View {
        val dayStatus = TimelineDayResolver.resolve(millis)

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 4.dp()
            params.bottomMargin = 0
            layoutParams = params
        }

        val axisView = TimelineAxisView(requireContext()).apply {
            bind(
                status = dayStatus,
                showNode = true
            )

            layoutParams = LinearLayout.LayoutParams(
                ACTIVITY_AXIS_WIDTH_DP.dp(),
                38.dp()
            )
        }

        val dateText = TextView(requireContext()).apply {
            text = formatActivityDate(millis)
            textSize = 12.5f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginStart = 4.dp()
            layoutParams = params
        }

        row.addView(axisView)
        row.addView(dateText)

        return row
    }

    private fun createActivityTaskRow(
        task: CareTaskUi
    ): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val axisView = TimelineAxisView(requireContext()).apply {
            bind(
                status = TimelineDayStatus.PAST,
                showNode = false
            )

            layoutParams = LinearLayout.LayoutParams(
                ACTIVITY_AXIS_WIDTH_DP.dp(),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        row.addView(axisView)
        row.addView(
            createActivityTaskCard(task)
        )

        return row
    }

    private fun createActivityTaskCard(
        task: CareTaskUi
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = 18.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#10233A"))
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.bottomMargin = 12.dp()
            layoutParams = params

            setOnClickListener {
                showActivityTaskActionBottomSheet(task)
            }

            setOnLongClickListener {
                showActivityTaskActionBottomSheet(task)
                true
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                12.dp(),
                11.dp(),
                12.dp(),
                11.dp()
            )
        }

        val iconBox = FrameLayout(requireContext()).apply {
            background = createActivityIconBackground(
                color = Color.parseColor(task.accentColor)
            )

            layoutParams = LinearLayout.LayoutParams(
                38.dp(),
                38.dp()
            )
        }

        val icon = ImageView(requireContext()).apply {
            setImageResource(task.iconRes)
            setColorFilter(Color.WHITE)

            val params = FrameLayout.LayoutParams(
                19.dp(),
                19.dp(),
                Gravity.CENTER
            )
            layoutParams = params
        }

        iconBox.addView(icon)

        val textBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginStart = 12.dp()
            layoutParams = params
        }

        val titleText = TextView(requireContext()).apply {
            text = task.title
            textSize = 13.4f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val bottomRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 7.dp()
            layoutParams = params
        }

        val metaText = TextView(requireContext()).apply {
            text = buildActivityMetaText(task)
            textSize = 12f
            setTextColor(Color.parseColor("#B8C7D9"))
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val completedText = TextView(requireContext()).apply {
            text = getString(R.string.aquarium_completed_label)
            textSize = 11.8f
            setTextColor(Color.parseColor("#5FD6B4"))
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginStart = 10.dp()
            layoutParams = params
        }

        bottomRow.addView(metaText)
        bottomRow.addView(completedText)

        textBox.addView(titleText)
        textBox.addView(bottomRow)

        row.addView(iconBox)
        row.addView(textBox)

        card.addView(row)

        return card
    }

    private fun buildActivityMetaText(
        task: CareTaskUi
    ): String {
        val completedAt = task.completedAtMillis ?: task.dueAtMillis

        return buildString {
            append(formatActivityTime(completedAt))

            if (task.sourceLabel.isNotBlank()) {
                append(" • ")
                append(task.sourceLabel)
            }
        }
    }

    private fun formatActivityDateTime(
        millis: Long
    ): String {
        return SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            Locale.getDefault()
        ).format(Date(millis))
    }

    private fun formatActivityDate(
        millis: Long
    ): String {
        val dateText = SimpleDateFormat(
            "dd.MM.yyyy",
            Locale.getDefault()
        ).format(Date(millis))

        return when {
            isActivityToday(millis) -> {
                getString(
                    R.string.aquarium_activity_date_today_format,
                    dateText
                )
            }

            isActivityYesterday(millis) -> {
                getString(
                    R.string.aquarium_activity_date_yesterday_format,
                    dateText
                )
            } else -> {
                dateText
            }
        }
    }

    private fun formatActivityTime(
        millis: Long
    ): String {
        return SimpleDateFormat(
            "HH:mm",
            Locale.getDefault()
        ).format(Date(millis))
    }

    private fun formatActivityDateKey(
        millis: Long
    ): String {
        return SimpleDateFormat(
            "yyyyMMdd",
            Locale.getDefault()
        ).format(Date(millis))
    }

    private fun isActivityToday(
        millis: Long
    ): Boolean {
        val target = Calendar.getInstance().apply {
            timeInMillis = millis
        }

        val today = Calendar.getInstance()

        return target.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    private fun isActivityYesterday(
        millis: Long
    ): Boolean {
        val target = Calendar.getInstance().apply {
            timeInMillis = millis
        }

        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }

        return target.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        target.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
    }

    private fun createActivityIconBackground(
        color: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 13.dp().toFloat()

            setColor(
                applyAlpha(
                    color = color,
                    alpha = 0.24f
                )
            )

            setStroke(
                1.dp(),
                applyAlpha(
                    color = color,
                    alpha = 0.65f
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

    private fun showGlobalLoading(
        show: Boolean
    ) {
        setFragmentGlobalLoading(show)
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = type
        )
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"
        private const val ACTIVITY_AXIS_WIDTH_DP = 36

        fun newInstance(
            tankId: Long
        ): TankDetailActivityFragment {
            return TankDetailActivityFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}
