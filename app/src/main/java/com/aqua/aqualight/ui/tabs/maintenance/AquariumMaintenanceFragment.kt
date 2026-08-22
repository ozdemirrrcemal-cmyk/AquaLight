package com.aqua.aqualight.ui.tabs.maintenance

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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.databinding.FragmentAquariumMaintenanceBinding
import com.aqua.aqualight.i18n.LocalDayKey
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetAction
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetActionStyle
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetDetailRow
import com.aqua.aqualight.ui.common.bottomsheet.GlobalActionBottomSheet
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.timeline.TimelineAxisView
import com.aqua.aqualight.ui.common.timeline.TimelineDayResolver
import com.aqua.aqualight.ui.common.timeline.TimelineDayStatus
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.careprofile.CareProfileCalculator
import com.aqua.aqualight.ui.tabs.aquarium.navigation.AquariumTabArgs
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import com.aqua.aqualight.ui.tabs.maintenance.model.MaintenanceTab
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class AquariumMaintenanceFragment :
    Fragment(R.layout.fragment_aquarium_maintenance) {

    private var _binding: FragmentAquariumMaintenanceBinding? = null
    private val binding get() = _binding!!

    private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private lateinit var adapter: CareTaskAdapter

    private var currentSelectedTab: MaintenanceTab = MaintenanceTab.ALL
    private var latestTanks: List<AquariumTankSnapshot> = emptyList()
    private var careProfileTargetTankId: Long = 0L
    private var tasksById: Map<Long, CareTaskUi> = emptyMap()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentAquariumMaintenanceBinding.bind(view)

        setupHeader()
        setupRecycler()
        setupClickListeners()
        setupHistoryActionResultListeners()
        observeTanks()
        observeSelectedTab()
        observeCareTasks()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.maintenance_title),
                showBackButton = false,
                primaryAction = AquaHeaderPrimaryAction(
                    text = getString(R.string.maintenance_action_add),
                    contentDescription = getString(R.string.maintenance_action_add_care_task),
                    onClick = {
                        openAddCareTaskScreen()
                    }
                )
            )
        )
    }

    private fun setupRecycler() {
        adapter = CareTaskAdapter(
            context = requireContext(),
            onTaskClick = { task ->
                openTaskDetailScreen(task)
            }
        )

        binding.rvCareTasks.layoutManager = LinearLayoutManager(
            requireContext()
        )

        binding.rvCareTasks.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.tabAll.setOnClickListener {
            maintenanceViewModel.selectTab(
                MaintenanceTab.ALL
            )
        }

        binding.tabToday.setOnClickListener {
            maintenanceViewModel.selectTab(
                MaintenanceTab.TODAY
            )
        }

        binding.tabUpcoming.setOnClickListener {
            maintenanceViewModel.selectTab(
                MaintenanceTab.UPCOMING
            )
        }

        binding.tabHistory.setOnClickListener {
            maintenanceViewModel.selectTab(
                MaintenanceTab.HISTORY
            )
        }

        binding.btnEmptyAddCareTask.setOnClickListener {
            openAddCareTaskScreen()
        }

        binding.cardCareProfileWarning.setOnClickListener {
            openCareProfileTargetTankSettings()
        }
    }


    private fun setupHistoryActionResultListeners() {
        childFragmentManager.setFragmentResultListener(
            HISTORY_ACTION_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(GlobalActionBottomSheet.RESULT_KEY) !=
                GlobalActionBottomSheet.RESULT_ACTION
            ) return@setFragmentResultListener
            if (result.getString(GlobalActionBottomSheet.RESULT_ACTION_ID) != ACTION_DELETE) {
                return@setFragmentResultListener
            }
            result.getString(GlobalActionBottomSheet.RESULT_PAYLOAD_ID)
                ?.toLongOrNull()
                ?.let(::showDeleteHistoryTaskDialog)
        }

        childFragmentManager.setFragmentResultListener(
            HISTORY_DELETE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(FeedbackBottomSheet.RESULT_KEY) !=
                FeedbackBottomSheet.RESULT_PRIMARY
            ) return@setFragmentResultListener
            result.getString(FeedbackBottomSheet.RESULT_ACTION_ID)
                ?.toLongOrNull()
                ?.let(::deleteHistoryTask)
        }
    }

    private fun observeTanks() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            latestTanks = tanks

            maintenanceViewModel.setTanks(tanks)
            renderCareProfileWarning()
        }
    }

    private fun observeSelectedTab() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                maintenanceViewModel.selectedTab.collect { selectedTab ->
                    currentSelectedTab = selectedTab

                    renderSelectedTab(selectedTab)
                    renderEmptyText(selectedTab)
                    renderCareProfileWarning()
                }
            }
        }
    }

    private fun observeCareTasks() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                maintenanceViewModel.taskItems.collect { tasks ->
                    tasksById = tasks.associateBy(CareTaskUi::id)

                    if (currentSelectedTab == MaintenanceTab.HISTORY) {
                        adapter.submitCareTasks(
                            tasks = emptyList(),
                            showDateHeaders = false
                        )

                        renderHistoryTimeline(tasks)
                    } else {
                        adapter.submitCareTasks(
                            tasks = tasks,
                            showDateHeaders = true
                        )

                        binding.historyTimelineContainer.removeAllViews()
                    }

                    renderTaskListState(tasks)
                }
            }
        }
    }

    private fun renderCareProfileWarning() {
        if (_binding == null) {
            return
        }

        if (
            currentSelectedTab == MaintenanceTab.HISTORY ||
            latestTanks.isEmpty()
        ) {
            binding.cardCareProfileWarning.isVisible = false
            careProfileTargetTankId = 0L
            return
        }

        val incompleteProfiles = latestTanks
            .map { tank ->
                tank to CareProfileCalculator.calculate(requireContext(), tank)
            }
            .filter { (_, result) ->
                result.percent < 100
            }

        if (incompleteProfiles.isEmpty()) {
            binding.cardCareProfileWarning.isVisible = false
            careProfileTargetTankId = 0L
            return
        }

        val targetProfile = incompleteProfiles.minBy { (_, result) ->
            result.percent
        }

        val targetTank = targetProfile.first
        val targetResult = targetProfile.second

        careProfileTargetTankId = targetTank.id

        binding.cardCareProfileWarning.isVisible = true
        binding.tvCareProfileWarningPercent.text = getString(
            R.string.maintenance_percent_value,
            targetResult.percent
        )

        if (incompleteProfiles.size == 1) {
            binding.tvCareProfileWarningTitle.text =
                getString(R.string.maintenance_care_profile_incomplete)

            binding.tvCareProfileWarningMessage.text =
                getString(R.string.maintenance_improve_automatic_care_tasks)
        } else {
            binding.tvCareProfileWarningTitle.text =
                getString(R.string.maintenance_care_profiles_incomplete)

            binding.tvCareProfileWarningMessage.text =
                getString(
                    R.string.maintenance_aquariums_need_more_details,
                    incompleteProfiles.size
                )
        }
    }

    private fun openCareProfileTargetTankSettings() {
        if (careProfileTargetTankId <= 0L) {
            return
        }

        val navController =
            findNavController()

        if (navController.currentDestination?.id != R.id.aquariumMaintenanceFragment) {
            return
        }

        AppRouteNavigator.openTankSettings(
            navController = navController,
            tankId = careProfileTargetTankId,
            startTab = AquariumTabArgs.BASIC
        )
    }

    private fun renderTaskListState(
        tasks: List<CareTaskUi>
    ) {
        val hasTasks = tasks.isNotEmpty()
        val isHistory = currentSelectedTab == MaintenanceTab.HISTORY

        binding.rvCareTasks.isVisible = hasTasks && !isHistory
        binding.historyScrollView.isVisible = hasTasks && isHistory
        binding.emptyStateContainer.isVisible = !hasTasks
    }

    private fun renderSelectedTab(
        selectedTab: MaintenanceTab
    ) {
        renderTab(
            tabView = binding.tabAll,
            selected = selectedTab == MaintenanceTab.ALL
        )

        renderTab(
            tabView = binding.tabToday,
            selected = selectedTab == MaintenanceTab.TODAY
        )

        renderTab(
            tabView = binding.tabUpcoming,
            selected = selectedTab == MaintenanceTab.UPCOMING
        )

        renderTab(
            tabView = binding.tabHistory,
            selected = selectedTab == MaintenanceTab.HISTORY
        )
    }

    private fun renderTab(
        tabView: TextView,
        selected: Boolean
    ) {
        tabView.setBackgroundResource(
            if (selected) {
                R.drawable.bg_maintenance_tab_selected
            } else {
                R.drawable.bg_maintenance_tab_unselected
            }
        )

        tabView.setTextColor(
            if (selected) {
                ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark)
            } else {
                ContextCompat.getColor(requireContext(), R.color.aqua_content_secondary)
            }
        )

        tabView.setTypeface(
            null,
            if (selected) {
                Typeface.BOLD
            } else {
                Typeface.NORMAL
            }
        )
    }

    private fun renderEmptyText(
        selectedTab: MaintenanceTab
    ) {
        when (selectedTab) {
            MaintenanceTab.ALL -> {
                binding.tvEmptyMaintenanceTitle.text = getString(R.string.maintenance_empty_no_tasks_title)
                binding.tvEmptyMaintenanceMessage.text =
                    getString(R.string.maintenance_empty_no_tasks_message)
            }

            MaintenanceTab.TODAY -> {
                binding.tvEmptyMaintenanceTitle.text = getString(R.string.maintenance_empty_today_title)
                binding.tvEmptyMaintenanceMessage.text =
                    getString(R.string.maintenance_empty_today_message)
            }

            MaintenanceTab.UPCOMING -> {
                binding.tvEmptyMaintenanceTitle.text = getString(R.string.maintenance_empty_upcoming_title)
                binding.tvEmptyMaintenanceMessage.text =
                    getString(R.string.maintenance_empty_upcoming_message)
            }

            MaintenanceTab.HISTORY -> {
                binding.tvEmptyMaintenanceTitle.text = getString(R.string.maintenance_empty_history_title)
                binding.tvEmptyMaintenanceMessage.text =
                    getString(R.string.maintenance_empty_history_message)
            }
        }
    }

    private fun renderHistoryTimeline(
        tasks: List<CareTaskUi>
    ) {
        binding.historyTimelineContainer.removeAllViews()

        if (tasks.isEmpty()) {
            return
        }

        val groupedTasks = tasks
            .sortedByDescending { task ->
                task.completedAtMillis ?: task.dueAtMillis
            }
            .groupBy { task ->
                getHistoryDateKey(
                    task.completedAtMillis ?: task.dueAtMillis
                )
            }

        groupedTasks.forEach { (_, dayTasks) ->
            val firstTask = dayTasks.firstOrNull() ?: return@forEach
            val dateMillis = firstTask.completedAtMillis ?: firstTask.dueAtMillis

            binding.historyTimelineContainer.addView(
                createHistoryDateHeader(
                    millis = dateMillis
                )
            )

            dayTasks.forEach { task ->
                binding.historyTimelineContainer.addView(
                    createHistoryTaskRow(task)
                )
            }
        }
    }

    private fun createHistoryDateHeader(
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
            text = formatHistoryDate(millis)
            setTextSizeResource(R.dimen.aqua_text_size_caption_plus)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark))
            setTypeface(null, Typeface.NORMAL)
            includeFontPadding = false

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginStart = resources.getDimensionPixelOffset(R.dimen.aqua_size_4)
            layoutParams = params
        }

        val statusText = TextView(requireContext()).apply {
            text = getString(
                TimelineDayResolver.getStatusTextRes(dayStatus)
            )
            setTextSizeResource(R.dimen.aqua_text_size_caption_plus)
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    TimelineDayResolver.getStatusTextColorRes(dayStatus)
                )
            )
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
        }

        row.addView(axisView)
        row.addView(dateText)
        row.addView(statusText)

        return row
    }

    private fun createHistoryTaskRow(
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
            createHistoryTaskCard(task)
        )

        return row
    }

    private fun createHistoryTaskCard(
        task: CareTaskUi
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = resources.getDimensionPixelOffset(R.dimen.aqua_size_18).toFloat()
            strokeWidth = resources.getDimensionPixelOffset(R.dimen.aqua_size_1)
            strokeColor = ContextCompat.getColor(requireContext(), R.color.aqua_outline_positive)
            setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.aqua_surface_positive))
            cardElevation = 0f
            useCompatPadding = false

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.bottomMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_12)
            layoutParams = params
        }

        card.setOnClickListener {
            showHistoryTaskBottomSheet(task)
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP

            setPadding(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_12),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_11),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_12),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_11)
            )
        }

        val iconBox = FrameLayout(requireContext()).apply {
            background = createHistoryIconBackground(
                color = task.accentColor
            )

            val params = LinearLayout.LayoutParams(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_38),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_38)
            )
            params.topMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_3)
            layoutParams = params
        }

        val icon = ImageView(requireContext()).apply {
            setImageResource(task.iconRes)
            setColorFilter(ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark))

            val params = FrameLayout.LayoutParams(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_19),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_19),
                Gravity.CENTER
            )
            layoutParams = params
        }

        iconBox.addView(icon)

        val contentBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginStart = resources.getDimensionPixelOffset(R.dimen.aqua_size_11)
            layoutParams = params
        }

        val titleText = TextView(requireContext()).apply {
            text = task.title
            setTextSizeResource(R.dimen.aqua_text_size_body_precise)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark))
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val metaText = TextView(requireContext()).apply {
            text = buildHistoryMetaText(task)
            setTextSizeResource(R.dimen.aqua_text_size_caption_precise)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_content_tertiary))
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_6)
            layoutParams = params
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

        val completedText = TextView(requireContext()).apply {
            text = getString(R.string.maintenance_status_completed)
            setTextSizeResource(R.dimen.aqua_text_size_caption_precise)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_accent_positive))
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        bottomRow.addView(completedText)

        if (task.sourceLabel.isNotBlank()) {
            bottomRow.addView(
                createHistorySourceBadge(
                    sourceLabel = task.sourceLabel
                )
            )
        }

        contentBox.addView(titleText)
        contentBox.addView(metaText)
        contentBox.addView(bottomRow)

        row.addView(iconBox)
        row.addView(contentBox)

        card.addView(row)

        return card
    }

    private fun createHistorySourceBadge(
        sourceLabel: String
    ): View {
        return TextView(requireContext()).apply {
            text = sourceLabel
            setTextSizeResource(R.dimen.aqua_text_size_micro_tight)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_aquarium_maintenance_fragment_content))
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            gravity = Gravity.CENTER

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = resources.getDimensionPixelOffset(R.dimen.aqua_size_999).toFloat()
                setColor(ContextCompat.getColor(requireContext(), R.color.aqua_aquarium_maintenance_fragment_color))
                setStroke(
                    resources.getDimensionPixelOffset(R.dimen.aqua_size_1),
                    ContextCompat.getColor(requireContext(), R.color.aqua_aquarium_maintenance_fragment_color_variant_2)
                )
            }

            setPadding(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_8),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_4),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_8),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_4)
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun showHistoryTaskBottomSheet(task: CareTaskUi) {
        val completedAt = task.completedAtMillis ?: task.dueAtMillis
        GlobalActionBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = task.title,
            message = getString(R.string.maintenance_completed_care_record),
            details = listOf(
                BottomSheetDetailRow(
                    label = getString(R.string.maintenance_aquarium_label),
                    value = task.tankName
                ),
                BottomSheetDetailRow(
                    label = getString(R.string.maintenance_completed_at),
                    value = formatHistoryDateTime(completedAt)
                ),
                BottomSheetDetailRow(
                    label = getString(R.string.maintenance_source_label),
                    value = task.sourceLabel.ifBlank {
                        getString(R.string.maintenance_unknown)
                    }
                ),
                BottomSheetDetailRow(
                    label = getString(R.string.maintenance_status_label),
                    value = getString(R.string.maintenance_status_completed)
                )
            ),
            actions = listOf(
                BottomSheetAction(
                    id = ACTION_DELETE,
                    text = getString(R.string.maintenance_delete_from_history),
                    style = BottomSheetActionStyle.DANGER
                )
            ),
            requestKey = HISTORY_ACTION_REQUEST_KEY,
            payloadId = task.id.toString()
        )
    }

    private fun showDeleteHistoryTaskDialog(taskId: Long) {
        val task = tasksById[taskId] ?: return
        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.maintenance_delete_from_history_title),
            message = getString(R.string.maintenance_delete_from_history_message, task.title),
            primaryText = getString(R.string.confirm),
            cancelText = getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.DANGER,
            requestKey = HISTORY_DELETE_REQUEST_KEY,
            actionId = taskId.toString()
        )
    }

    private fun deleteHistoryTask(taskId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showGlobalLoading(true)
                maintenanceViewModel.deleteTask(taskId = taskId).join()
            } finally {
                showGlobalLoading(false)
            }
        }
    }

    private fun showGlobalLoading(
        show: Boolean
    ) {
        setFragmentGlobalLoading(show)
    }

    private fun openAddCareTaskScreen() {
        findNavController().navigate(
            AquariumMaintenanceFragmentDirections.actionAquariumMaintenanceFragmentToAddCareTaskFragment()
        )
    }

    private fun openTaskDetailScreen(
        task: CareTaskUi
    ) {
        findNavController().navigate(
            AquariumMaintenanceFragmentDirections.actionAquariumMaintenanceFragmentToTaskDetailFragment(
                taskId = task.id
            )
        )
    }

    private fun buildHistoryMetaText(
        task: CareTaskUi
    ): String {
        val completedAt = task.completedAtMillis ?: task.dueAtMillis

        return buildString {
            append(task.tankName)
            append(" • ")
            append(formatHistoryTime(completedAt))
        }
    }

    private fun formatHistoryDate(
        millis: Long
    ): String {
        return LocaleFormatter.formatDate(requireContext(), millis)
    }

    private fun formatHistoryTime(
        millis: Long
    ): String {
        return LocaleFormatter.formatTime(requireContext(), millis)
    }

    private fun formatHistoryDateTime(
        millis: Long
    ): String {
        return LocaleFormatter.formatDateTime(requireContext(), millis)
    }

    private fun getHistoryDateKey(
        millis: Long
    ) = LocalDayKey.fromEpochMillis(millis)

    private fun createHistoryIconBackground(
        color: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = resources.getDimensionPixelOffset(R.dimen.aqua_size_13).toFloat()

            setColor(
                applyAlpha(
                    color = color,
                    alpha = 0.26f
                )
            )

            setStroke(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_1),
                applyAlpha(
                    color = color,
                    alpha = 0.7f
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
    override fun onDestroyView() {
        binding.rvCareTasks.adapter = null
        _binding = null

        super.onDestroyView()
    }

    companion object {
        private const val HISTORY_ACTION_REQUEST_KEY = "maintenance_history_action_result"
        private const val HISTORY_DELETE_REQUEST_KEY = "maintenance_history_delete_result"
        private const val ACTION_DELETE = "delete"
    }
}
