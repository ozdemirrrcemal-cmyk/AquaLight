package com.aqua.aqualight.ui.tabs.aquarium.detail

import com.aqua.aqualight.ui.common.text.setTextSizeResource
import androidx.core.content.ContextCompat
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
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.timeline.TimelineAxisView
import com.aqua.aqualight.ui.common.text.resolve
import com.aqua.aqualight.ui.common.timeline.TimelineDayResolver
import com.aqua.aqualight.ui.common.timeline.TimelineDayStatus
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.ui.tabs.maintenance.TankActivityUiState
import com.aqua.aqualight.ui.tabs.maintenance.TankNextCareStatus
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
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
    private var completedTasksById: Map<Long, CareTaskUi> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankDetailActivityBinding.bind(view)

        setupClickListeners()
        setupCareTaskTypeResultListener()
        setupActionResultListeners()
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


    private fun setupActionResultListeners() {
        childFragmentManager.setFragmentResultListener(
            ACTIVITY_ACTION_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(GlobalActionBottomSheet.RESULT_KEY) !=
                GlobalActionBottomSheet.RESULT_ACTION
            ) return@setFragmentResultListener

            val taskId = result.getString(GlobalActionBottomSheet.RESULT_PAYLOAD_ID)
                ?.toLongOrNull()
                ?: return@setFragmentResultListener
            when (result.getString(GlobalActionBottomSheet.RESULT_ACTION_ID)) {
                ACTION_CHANGE_DATE -> showChangeActivityDatePicker(taskId)
                ACTION_DELETE -> showDeleteActivityTaskDialog(taskId)
            }
        }

        childFragmentManager.setFragmentResultListener(
            ACTIVITY_DELETE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(FeedbackBottomSheet.RESULT_KEY) !=
                FeedbackBottomSheet.RESULT_PRIMARY
            ) return@setFragmentResultListener
            result.getString(FeedbackBottomSheet.RESULT_ACTION_ID)
                ?.toLongOrNull()
                ?.let(::deleteActivityTask)
        }

        childFragmentManager.setFragmentResultListener(
            ACTIVITY_DATE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(AppDatePickerDialogFragment.RESULT_KEY) !=
                AppDatePickerDialogFragment.RESULT_SELECTED
            ) return@setFragmentResultListener
            val taskId = result.getString(AppDatePickerDialogFragment.RESULT_PAYLOAD_ID)
                ?.toLongOrNull()
                ?: return@setFragmentResultListener
            updateCompletedTaskDate(
                taskId = taskId,
                millis = result.getLong(AppDatePickerDialogFragment.RESULT_MILLIS)
            )
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
        completedTasksById = state.completedTasks.associateBy(CareTaskUi::id)
        val context = requireContext()
        binding.tvLastTrimValue.text = context.resolve(state.lastTrimText)
        binding.tvLastWaterChangeValue.text = context.resolve(state.lastWaterChangeText)
        binding.tvLastFilterValue.text = context.resolve(state.lastFilterMaintenanceText)

        if (state.nextCareTask == null) {
            binding.tvNextCareTaskTitle.text = getString(R.string.aquarium_no_value_placeholder)
            binding.tvNextCareValue.text = getString(R.string.aquarium_no_upcoming_care)
            binding.tvNextCareValue.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.aqua_palette_hex_8fa4be)
            )
        } else {
            binding.tvNextCareTaskTitle.text = state.nextCareTask.title
            binding.tvNextCareValue.text = context.resolve(state.nextCareText)
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
                ContextCompat.getColor(requireContext(), R.color.aqua_palette_hex_d85c5c)
            }

            TankNextCareStatus.TODAY -> {
                ContextCompat.getColor(requireContext(), R.color.aqua_palette_hex_f2c94c)
            }

            TankNextCareStatus.TOMORROW -> {
                ContextCompat.getColor(requireContext(), R.color.aqua_palette_hex_5fd6b4)
            }

            TankNextCareStatus.NONE,
            TankNextCareStatus.FUTURE -> {
                ContextCompat.getColor(requireContext(), R.color.aqua_palette_hex_8fa4be)
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
            fragmentManager = childFragmentManager,
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
                        getString(R.string.common_not_available_symbol)
                    }
                ),
                BottomSheetDetailRow(
                    label = getString(R.string.aquarium_status_label),
                    value = getString(R.string.aquarium_completed_label)
                )
            ),
            actions = listOf(
                BottomSheetAction(
                    id = ACTION_CHANGE_DATE,
                    text = getString(R.string.aquarium_change_date_action),
                    style = BottomSheetActionStyle.PRIMARY
                ),
                BottomSheetAction(
                    id = ACTION_DELETE,
                    text = getString(R.string.common_delete),
                    style = BottomSheetActionStyle.DANGER
                )
            ),
            requestKey = ACTIVITY_ACTION_REQUEST_KEY,
            payloadId = task.id.toString()
        )
    }

    private fun showChangeActivityDatePicker(taskId: Long) {
        val task = completedTasksById[taskId] ?: return
        AppDatePickerDialogFragment.show(
            fragmentManager = childFragmentManager,
            requestKey = ACTIVITY_DATE_REQUEST_KEY,
            initialMillis = task.completedAtMillis ?: task.dueAtMillis,
            payloadId = taskId.toString()
        )
    }

    private fun updateCompletedTaskDate(taskId: Long, millis: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showGlobalLoading(true)
                maintenanceViewModel.updateCompletedTaskDate(
                    taskId = taskId,
                    completedAtMillis = millis
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
    }

    private fun showDeleteActivityTaskDialog(taskId: Long) {
        val task = completedTasksById[taskId] ?: return
        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.aquarium_delete_activity_title),
            message = getString(R.string.aquarium_delete_activity_message, task.title),
            primaryText = getString(R.string.confirm),
            cancelText = getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.WARNING,
            requestKey = ACTIVITY_DELETE_REQUEST_KEY,
            actionId = taskId.toString()
        )
    }

    private fun deleteActivityTask(taskId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showGlobalLoading(true)
                maintenanceViewModel.deleteTask(taskId = taskId).join()
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
            params.topMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_4)
            params.bottomMargin = 0
            layoutParams = params
        }

        val axisView = TimelineAxisView(requireContext()).apply {
            bind(
                status = dayStatus,
                showNode = true
            )

            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_36),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_38)
            )
        }

        val dateText = TextView(requireContext()).apply {
            text = formatActivityDate(millis)
            setTextSizeResource(R.dimen.aqua_text_size_caption_plus)
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
            params.marginStart = resources.getDimensionPixelOffset(R.dimen.aqua_size_4)
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
                resources.getDimensionPixelOffset(R.dimen.aqua_size_36),
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
            radius = resources.getDimensionPixelOffset(R.dimen.aqua_size_18).toFloat()
            strokeWidth = resources.getDimensionPixelOffset(R.dimen.aqua_size_1)
            strokeColor = ContextCompat.getColor(requireContext(), R.color.aqua_palette_hex_223a57)
            setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.aqua_palette_hex_10233a))
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.bottomMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_12)
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
                resources.getDimensionPixelOffset(R.dimen.aqua_size_12),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_11),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_12),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_11)
            )
        }

        val iconBox = FrameLayout(requireContext()).apply {
            background = createActivityIconBackground(
                color = task.accentColor
            )

            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_38),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_38)
            )
        }

        val icon = ImageView(requireContext()).apply {
            setImageResource(task.iconRes)
            setColorFilter(Color.WHITE)

            val params = FrameLayout.LayoutParams(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_19),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_19),
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
            params.marginStart = resources.getDimensionPixelOffset(R.dimen.aqua_size_12)
            layoutParams = params
        }

        val titleText = TextView(requireContext()).apply {
            text = task.title
            setTextSizeResource(R.dimen.aqua_text_size_body_precise_medium)
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
            params.topMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_7)
            layoutParams = params
        }

        val metaText = TextView(requireContext()).apply {
            text = buildActivityMetaText(task)
            setTextSizeResource(R.dimen.aqua_text_size_caption)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_palette_hex_b8c7d9))
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
            setTextSizeResource(R.dimen.aqua_text_size_caption_precise)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_palette_hex_5fd6b4))
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginStart = resources.getDimensionPixelOffset(R.dimen.aqua_size_10)
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
            cornerRadius = resources.getDimensionPixelOffset(R.dimen.aqua_size_13).toFloat()

            setColor(
                applyAlpha(
                    color = color,
                    alpha = 0.24f
                )
            )

            setStroke(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_1),
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
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"
        private const val ACTIVITY_ACTION_REQUEST_KEY = "tank_activity_action_result"
        private const val ACTIVITY_DELETE_REQUEST_KEY = "tank_activity_delete_result"
        private const val ACTIVITY_DATE_REQUEST_KEY = "tank_activity_date_result"
        private const val ACTION_CHANGE_DATE = "change_date"
        private const val ACTION_DELETE = "delete"

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
