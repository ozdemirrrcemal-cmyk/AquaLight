package com.aqua.aqualight.ui.tabs.maintenance

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
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetAction
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetActionStyle
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetDetailRow
import com.aqua.aqualight.ui.common.bottomsheet.GlobalActionBottomSheet
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
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import com.aqua.aqualight.ui.tabs.maintenance.model.MaintenanceTab
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AquariumMaintenanceFragment :
    Fragment(R.layout.fragment_aquarium_maintenance) {

    private var _binding: FragmentAquariumMaintenanceBinding? = null
    private val binding get() = _binding!!

    private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private lateinit var adapter: CareTaskAdapter

    private var currentSelectedTab: MaintenanceTab = MaintenanceTab.ALL
    private var latestTanks: List<SavedAquariumTank> = emptyList()
    private var careProfileTargetTankId: Long = 0L

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
        observeTanks()
        observeSelectedTab()
        observeCareTasks()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = "Maintenance",
                showBackButton = false,
                primaryAction = AquaHeaderPrimaryAction(
                    text = "+ Add",
                    contentDescription = "Add care task",
                    onClick = {
                        openAddCareTaskScreen()
                    }
                )
            )
        )
    }

    private fun setupRecycler() {
        adapter = CareTaskAdapter(
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
        binding.tvCareProfileWarningPercent.text = "${targetResult.percent}%"

        if (incompleteProfiles.size == 1) {
            binding.tvCareProfileWarningTitle.text =
                "Care profile incomplete"

            binding.tvCareProfileWarningMessage.text =
                "Improve automatic care tasks"
        } else {
            binding.tvCareProfileWarningTitle.text =
                "Care profiles incomplete"

            binding.tvCareProfileWarningMessage.text =
                "${incompleteProfiles.size} aquariums need more details"
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
                Color.WHITE
            } else {
                Color.parseColor("#8FA4BE")
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
                binding.tvEmptyMaintenanceTitle.text = "No care tasks yet"
                binding.tvEmptyMaintenanceMessage.text =
                    "Add manual reminders or let AquaLight create smart care tasks based on your aquarium setup."
            }

            MaintenanceTab.TODAY -> {
                binding.tvEmptyMaintenanceTitle.text = "Nothing due today"
                binding.tvEmptyMaintenanceMessage.text =
                    "There are no care tasks scheduled for today."
            }

            MaintenanceTab.UPCOMING -> {
                binding.tvEmptyMaintenanceTitle.text = "No upcoming tasks"
                binding.tvEmptyMaintenanceMessage.text =
                    "Future care reminders will appear here."
            }

            MaintenanceTab.HISTORY -> {
                binding.tvEmptyMaintenanceTitle.text = "No completed tasks"
                binding.tvEmptyMaintenanceMessage.text =
                    "Completed maintenance tasks will be listed here."
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
                HISTORY_AXIS_WIDTH_DP.dp(),
                38.dp()
            )
        }

        val dateText = TextView(requireContext()).apply {
            text = formatHistoryDate(millis)
            textSize = 12.5f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.NORMAL)
            includeFontPadding = false

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginStart = 4.dp()
            layoutParams = params
        }

        val statusText = TextView(requireContext()).apply {
            text = TimelineDayResolver.getStatusText(dayStatus)
            textSize = 12.5f
            setTextColor(
                TimelineDayResolver.getStatusTextColor(dayStatus)
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
                HISTORY_AXIS_WIDTH_DP.dp(),
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
            radius = 18.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = Color.parseColor("#2B6F5A")
            setCardBackgroundColor(Color.parseColor("#12382F"))
            cardElevation = 0f
            useCompatPadding = false

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.bottomMargin = 12.dp()
            layoutParams = params
        }

        card.setOnClickListener {
            showHistoryTaskBottomSheet(task)
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP

            setPadding(
                12.dp(),
                11.dp(),
                12.dp(),
                11.dp()
            )
        }

        val iconBox = FrameLayout(requireContext()).apply {
            background = createHistoryIconBackground(
                color = Color.parseColor(task.accentColor)
            )

            val params = LinearLayout.LayoutParams(
                38.dp(),
                38.dp()
            )
            params.topMargin = 3.dp()
            layoutParams = params
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

        val contentBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginStart = 11.dp()
            layoutParams = params
        }

        val titleText = TextView(requireContext()).apply {
            text = task.title
            textSize = 13.2f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val metaText = TextView(requireContext()).apply {
            text = buildHistoryMetaText(task)
            textSize = 11.8f
            setTextColor(Color.parseColor("#B8C7D9"))
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 6.dp()
            layoutParams = params
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

        val completedText = TextView(requireContext()).apply {
            text = "Completed"
            textSize = 11.8f
            setTextColor(Color.parseColor("#5FD6B4"))
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
            textSize = 10.4f
            setTextColor(Color.parseColor("#8FE7D5"))
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            gravity = Gravity.CENTER

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999.dp().toFloat()
                setColor(Color.parseColor("#173F38"))
                setStroke(
                    1.dp(),
                    Color.parseColor("#2F7D70")
                )
            }

            setPadding(
                8.dp(),
                4.dp(),
                8.dp(),
                4.dp()
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun showHistoryTaskBottomSheet(
        task: CareTaskUi
    ) {
        val completedAt = task.completedAtMillis ?: task.dueAtMillis

        GlobalActionBottomSheet.show(
            context = requireContext(),
            title = task.title,
            message = "Completed care record",
            details = listOf(
                BottomSheetDetailRow(
                    label = "Aquarium",
                    value = task.tankName
                ),
                BottomSheetDetailRow(
                    label = "Completed at",
                    value = formatHistoryDateTime(completedAt)
                ),
                BottomSheetDetailRow(
                    label = "Source",
                    value = task.sourceLabel.ifBlank {
                        "Unknown"
                    }
                ),
                BottomSheetDetailRow(
                    label = "Status",
                    value = "Completed"
                )
            ),
            actions = listOf(
                BottomSheetAction(
                    text = "Delete from history",
                    style = BottomSheetActionStyle.DANGER,
                    onClick = {
                        showDeleteHistoryTaskDialog(task)
                    }
                )
            )
        )
    }

    private fun showDeleteHistoryTaskDialog(
        task: CareTaskUi
    ) {
        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.ERROR,
            title = "Delete from history?",
            message = "\"${task.title}\" will be removed from completed history.",
            confirmTextResId = R.string.confirm,
            cancelTextResId = R.string.cancel,
            onConfirm = {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        showGlobalLoading(true)

                        maintenanceViewModel.deleteTask(
                            taskId = task.id
                        ).join()
                    } finally {
                        showGlobalLoading(false)
                    }
                }
            }
        )
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
        return SimpleDateFormat(
            "dd.MM.yyyy",
            Locale.getDefault()
        ).format(Date(millis))
    }

    private fun formatHistoryTime(
        millis: Long
    ): String {
        return SimpleDateFormat(
            "HH:mm",
            Locale.getDefault()
        ).format(Date(millis))
    }

    private fun formatHistoryDateTime(
        millis: Long
    ): String {
        return SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            Locale.getDefault()
        ).format(Date(millis))
    }

    private fun getHistoryDateKey(
        millis: Long
    ): String {
        return SimpleDateFormat(
            "yyyyMMdd",
            Locale.getDefault()
        ).format(Date(millis))
    }

    private fun createHistoryIconBackground(
        color: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 13.dp().toFloat()

            setColor(
                applyAlpha(
                    color = color,
                    alpha = 0.26f
                )
            )

            setStroke(
                1.dp(),
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

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        binding.rvCareTasks.adapter = null
        _binding = null

        super.onDestroyView()
    }

    companion object {
        private const val HISTORY_AXIS_WIDTH_DP = 36
    }
}
