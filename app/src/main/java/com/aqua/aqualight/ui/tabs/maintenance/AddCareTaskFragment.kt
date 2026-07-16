package com.aqua.aqualight.ui.tabs.maintenance

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.data.care.catalog.CareTaskTypeCatalog
import com.aqua.aqualight.application.care.CareTaskSnapshotSource
import com.aqua.aqualight.application.care.CareTaskSnapshotStatus
import com.aqua.aqualight.application.care.CareTaskSnapshotType
import com.aqua.aqualight.databinding.FragmentAddCareTaskBinding
import com.aqua.aqualight.ui.common.bottomsheet.CareTaskTypeBottomSheetFragment
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import com.aqua.aqualight.ui.tabs.settings.app.NotificationsBottomSheet
import com.aqua.aqualight.utils.NotificationHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class AddCareTaskFragment : Fragment(R.layout.fragment_add_care_task) {

    private val args: AddCareTaskFragmentArgs by navArgs()

    private var _binding: FragmentAddCareTaskBinding? = null
    private val binding get() = _binding!!

    private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private val userSettingsOperations by lazy {
        requireContext().requireAppContainer().userSettingsOperations
    }

    private var taskId: Long = -1L
    private val isEditMode: Boolean
        get() = taskId > 0L

    private var hasLoadedEditTask = false
    private var currentEditTask: CareTaskUi? = null
    private var selectedType: CareTaskType? = null
    private var selectedTankId: Long = 0L
    private var selectedWaterChangePercent: Int? = null
    private var latestTanks: List<AquariumTankSnapshot> = emptyList()
    private var pendingSaveAfterNotificationPermission = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (_binding == null) {
            return@registerForActivityResult
        }

        val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(
            requireContext()
        )
        val canUseNotifications = granted && systemEnabled

        viewLifecycleOwner.lifecycleScope.launch {
            userSettingsOperations.updateNotificationsEnabled(
                canUseNotifications
            )

            if (canUseNotifications && pendingSaveAfterNotificationPermission) {
                pendingSaveAfterNotificationPermission = false
                saveTaskInternal()
            } else {
                pendingSaveAfterNotificationPermission = false
                openNotificationPermissionSheet()
            }
        }
    }

    private val selectedCalendar: Calendar = Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAddCareTaskBinding.bind(view)
        taskId = args.taskId

        setupInitialUi()
        setupClickListeners()
        observeTaskTypeSelection()
        observeTanks()

        if (isEditMode) {
            observeEditTask()
        }

        updateDateTimeText()
        updateSelectedTaskTypeUi()
        updateSelectedAquariumUi()
        updateDynamicSections()
        updateSaveButtonState()
    }

    private fun setupInitialUi() {
        setupHeader(
            if (isEditMode) {
                getString(R.string.maintenance_edit_care_task_title)
            } else {
                getString(R.string.maintenance_add_care_task_title)
            }
        )
        binding.btnSaveTask.text = if (isEditMode) {
            getString(R.string.maintenance_update_task)
        } else {
            getString(R.string.maintenance_save_task)
        }
        binding.switchReminder.isChecked = true
        binding.switchMissedReminder.isChecked = true
        binding.switchRepeat.isChecked = false
    }

    private fun setupHeader(title: String) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = title,
                onBackClick = ::closeForm
            )
        )
    }

    private fun setupClickListeners() = with(binding) {
        rowTaskType.setOnClickListener {
            showTaskTypeBottomSheet()
        }
        rowAquarium.setOnClickListener {
            showAquariumBottomSheet()
        }
        rowDueDate.setOnClickListener {
            showDatePicker()
        }
        rowDueTime.setOnClickListener {
            showTimePicker()
        }
        switchRepeat.setOnCheckedChangeListener { _, _ ->
            updateDynamicSections()
        }
        switchReminder.setOnCheckedChangeListener { _, _ ->
            updateDynamicSections()
        }
        switchMissedReminder.setOnCheckedChangeListener { _, _ ->
            updateDynamicSections()
        }
        btnSaveTask.setOnClickListener {
            saveTask()
        }
    }

    private fun observeTanks() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            latestTanks = tanks
            maintenanceViewModel.setTanks(tanks)

            if (
                selectedTankId != 0L &&
                tanks.none { tank -> tank.id == selectedTankId }
            ) {
                selectedTankId = 0L
            }

            updateSelectedAquariumUi()
            updateSaveButtonState()
        }
    }

    private fun observeEditTask() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                maintenanceViewModel.taskByIdFlow(taskId).collect { task ->
                    if (task == null) {
                        return@collect
                    }
                    if (
                        task.source != CareTaskSource.MANUAL ||
                        task.status != CareTaskStatus.PENDING
                    ) {
                        showSnackBar(
                            getString(
                                R.string.maintenance_only_pending_manual_tasks_editable
                            ),
                            BaseActivity.SnackType.WARNING
                        )
                        closeForm()
                        return@collect
                    }

                    currentEditTask = task
                    if (!hasLoadedEditTask) {
                        hasLoadedEditTask = true
                        populateEditForm(task)
                    }
                }
            }
        }
    }

    private fun populateEditForm(task: CareTaskUi) {
        selectedType = task.type
        selectedTankId = task.tankId
        selectedWaterChangePercent = task.waterChangePercent
        selectedCalendar.timeInMillis = task.dueAtMillis

        binding.switchRepeat.isChecked = task.repeatEnabled
        binding.etRepeatDays.setText(
            task.repeatIntervalDays.coerceAtLeast(1).toString()
        )
        binding.switchReminder.isChecked = task.reminderEnabled
        binding.switchMissedReminder.isChecked = task.missedReminderEnabled
        binding.etMissedReminderDays.setText(
            task.missedReminderDays.coerceAtLeast(1).toString()
        )
        binding.etNote.setText(task.note)
        binding.etCustomTitle.setText(
            if (task.type == CareTaskType.CUSTOM) task.title else ""
        )

        updateDateTimeText()
        updateSelectedTaskTypeUi()
        updateSelectedAquariumUi()
        updateDynamicSections()
        updateSaveButtonState()
    }

    private fun showTaskTypeBottomSheet() {
        CareTaskTypeBottomSheetFragment.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.maintenance_select_task_type),
            resultRequestKey =
                CareTaskTypeBottomSheetFragment.REQUEST_KEY_SELECT_TASK_TYPE,
            selectedType = selectedType
        )
    }

    private fun observeTaskTypeSelection() {
        childFragmentManager.setFragmentResultListener(
            CareTaskTypeBottomSheetFragment.REQUEST_KEY_SELECT_TASK_TYPE,
            viewLifecycleOwner
        ) { _, bundle ->
            val type = runCatching {
                CareTaskType.valueOf(
                    bundle.getString(
                        CareTaskTypeBottomSheetFragment.RESULT_TASK_TYPE
                    ).orEmpty()
                )
            }.getOrNull() ?: return@setFragmentResultListener
            applySelectedTaskType(type)
        }
    }

    private fun applySelectedTaskType(type: CareTaskType) {
        selectedType = type
        if (type != CareTaskType.WATER_CHANGE) {
            selectedWaterChangePercent = null
        }
        if (type != CareTaskType.CUSTOM) {
            binding.etCustomTitle.setText("")
        }

        updateSelectedTaskTypeUi()
        updateDynamicSections()
        updateSaveButtonState()

        if (type == CareTaskType.WATER_CHANGE) {
            binding.root.post {
                showWaterChangePercentBottomSheet()
            }
        }
    }

    private fun showWaterChangePercentBottomSheet() {
        val dialog = BottomSheetDialog(
            requireContext(),
            R.style.AquaBottomSheetDialogTheme
        )
        val contentView = LayoutInflater.from(requireContext()).inflate(
            R.layout.bottom_sheet_water_change_percent,
            null,
            false
        )
        val container = contentView.findViewById<LinearLayout>(
            R.id.percentOptionsContainer
        )
        renderWaterChangePercentOptions(container, dialog)
        dialog.setContentView(contentView)
        dialog.setOnShowListener {
            dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    private fun renderWaterChangePercentOptions(
        container: LinearLayout,
        dialog: BottomSheetDialog
    ) {
        container.removeAllViews()
        val grid = GridLayout(requireContext()).apply {
            columnCount = 4
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
            .forEach { percent ->
                grid.addView(createPercentChip(percent, dialog))
            }
        container.addView(grid)
    }

    private fun createPercentChip(
        percent: Int,
        dialog: BottomSheetDialog
    ): View {
        val selected = percent == selectedWaterChangePercent
        return TextView(requireContext()).apply {
            text = getString(R.string.maintenance_percent_value, percent)
            gravity = Gravity.CENTER
            textSize = 12.8f
            isSelected = selected
            background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_aqua_selection_row_compact
            )
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) {
                        R.color.aqua_card_text_primary
                    } else {
                        R.color.aqua_card_text_secondary
                    }
                )
            )
            setTypeface(
                null,
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
            includeFontPadding = false
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 42.dp()
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(0, 0, 8.dp(), 8.dp())
            }
            setOnClickListener {
                selectedWaterChangePercent = percent
                updateSelectedTaskTypeUi()
                updateSaveButtonState()
                dialog.dismiss()
            }
        }
    }

    private fun showAquariumBottomSheet() {
        if (latestTanks.isEmpty()) {
            showSnackBar(
                getString(R.string.maintenance_create_aquarium_first),
                BaseActivity.SnackType.WARNING
            )
            return
        }

        val dialog = BottomSheetDialog(
            requireContext(),
            R.style.AquaBottomSheetDialogTheme
        )
        val contentView = LayoutInflater.from(requireContext()).inflate(
            R.layout.bottom_sheet_select_aquarium,
            null,
            false
        )
        val container = contentView.findViewById<LinearLayout>(
            R.id.aquariumOptionsContainer
        )
        container.removeAllViews()
        latestTanks.forEach { tank ->
            container.addView(createAquariumOptionCard(tank, dialog))
        }
        dialog.setContentView(contentView)
        dialog.setOnShowListener {
            dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    private fun createAquariumOptionCard(
        tank: AquariumTankSnapshot,
        dialog: BottomSheetDialog
    ): View {
        val selected = tank.id == selectedTankId
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isSelected = selected
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_aqua_selection_row_compact
            )
            setPadding(14.dp(), 8.dp(), 14.dp(), 8.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52.dp()
            ).apply {
                bottomMargin = 9.dp()
            }
            setOnClickListener {
                selectedTankId = tank.id
                updateSelectedAquariumUi()
                updateSaveButtonState()
                dialog.dismiss()
            }
        }

        val title = TextView(requireContext()).apply {
            text = tank.name.ifBlank {
                getString(R.string.maintenance_unnamed_aquarium)
            }
            textSize = 13.5f
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) {
                        R.color.aqua_card_text_primary
                    } else {
                        R.color.aqua_card_text_secondary
                    }
                )
            )
            setTypeface(
                null,
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
            includeFontPadding = false
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val check = TextView(requireContext()).apply {
            text = if (selected) {
                getString(R.string.maintenance_selected)
            } else {
                ""
            }
            textSize = 11.5f
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_card_accent
                )
            )
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
        }

        row.addView(title)
        row.addView(check)
        return row
    }

    private fun updateSelectedTaskTypeUi() {
        val type = selectedType
        if (type == null) {
            binding.taskTypeIconContainer.isVisible = false
            binding.tvTaskTypeTitle.text = getString(
                R.string.maintenance_select_care_task_type
            )
            binding.tvTaskTypeTitle.setTextColor(Color.parseColor("#8FA4BE"))
            binding.tvTaskTypeSubtitle.text = getString(
                R.string.maintenance_required
            )
            binding.tvTaskTypeSubtitle.setTextColor(Color.parseColor("#6F829B"))
            return
        }

        val typeUi = CareTaskTypeCatalog.get(type)
        val typeTitle = typeUi.title(requireContext())
        val category = typeUi.category(requireContext())
        val accentColor = Color.parseColor(typeUi.accentColor)

        binding.taskTypeIconContainer.isVisible = true
        binding.taskTypeIconContainer.background = createIconBackground(
            accentColor,
            selected = true
        )
        binding.ivTaskTypeIcon.setImageResource(typeUi.iconRes)
        binding.ivTaskTypeIcon.setColorFilter(Color.WHITE)

        binding.tvTaskTypeTitle.text = if (
            type == CareTaskType.WATER_CHANGE &&
            selectedWaterChangePercent != null
        ) {
            getString(
                R.string.maintenance_task_title_with_percent,
                typeTitle,
                selectedWaterChangePercent
            )
        } else {
            typeTitle
        }
        binding.tvTaskTypeTitle.setTextColor(Color.WHITE)

        binding.tvTaskTypeSubtitle.text = when {
            type == CareTaskType.WATER_CHANGE &&
                selectedWaterChangePercent == null -> {
                getString(
                    R.string.maintenance_select_water_change_percentage
                )
            }

            type == CareTaskType.WATER_CHANGE -> {
                getString(
                    R.string.maintenance_task_category_with_percent,
                    category,
                    selectedWaterChangePercent
                )
            }

            else -> category
        }
        binding.tvTaskTypeSubtitle.setTextColor(Color.parseColor("#8FA4BE"))
    }

    private fun updateSelectedAquariumUi() {
        val selectedTank = latestTanks.firstOrNull { tank ->
            tank.id == selectedTankId
        }
        if (selectedTank == null) {
            binding.tvAquariumTitle.text = getString(
                R.string.maintenance_select_aquarium
            )
            binding.tvAquariumTitle.setTextColor(Color.parseColor("#8FA4BE"))
            binding.tvAquariumSubtitle.text = getString(
                R.string.maintenance_required
            )
            binding.tvAquariumSubtitle.setTextColor(Color.parseColor("#6F829B"))
            return
        }

        binding.tvAquariumTitle.text = selectedTank.name.ifBlank {
            getString(R.string.maintenance_unnamed_aquarium)
        }
        binding.tvAquariumTitle.setTextColor(Color.WHITE)
        binding.tvAquariumSubtitle.text = getString(
            R.string.maintenance_selected_aquarium
        )
        binding.tvAquariumSubtitle.setTextColor(Color.parseColor("#5FD6B4"))
    }

    private fun updateDynamicSections() = with(binding) {
        customTitleSection.isVisible = selectedType == CareTaskType.CUSTOM
        repeatDaysContainer.isVisible = switchRepeat.isChecked
        missedReminderSection.isVisible = switchReminder.isChecked
        missedReminderDaysContainer.isVisible =
            switchReminder.isChecked && switchMissedReminder.isChecked
        updateSaveButtonState()
    }

    private fun updateSaveButtonState() {
        val type = selectedType
        val canSave = type != null &&
            selectedTankId != 0L &&
            (
                type != CareTaskType.WATER_CHANGE ||
                    selectedWaterChangePercent != null
                )
        binding.btnSaveTask.isEnabled = canSave
        binding.btnSaveTask.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (canSave) "#2196F3" else "#35506D")
        )
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedCalendar.set(Calendar.YEAR, year)
                selectedCalendar.set(Calendar.MONTH, month)
                selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateTimeText()
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedCalendar.set(Calendar.MINUTE, minute)
                selectedCalendar.set(Calendar.SECOND, 0)
                selectedCalendar.set(Calendar.MILLISECOND, 0)
                updateDateTimeText()
            },
            selectedCalendar.get(Calendar.HOUR_OF_DAY),
            selectedCalendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateDateTimeText() {
        binding.tvDueDateValue.text = SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        ).format(Date(selectedCalendar.timeInMillis))
        binding.tvDueTimeValue.text = SimpleDateFormat(
            "HH:mm",
            Locale.getDefault()
        ).format(Date(selectedCalendar.timeInMillis))
    }

    private fun ensureNotificationPermissionBeforeSave(): Boolean {
        if (!binding.switchReminder.isChecked) {
            return true
        }

        val context = requireContext()
        val hasPermission = NotificationHelper.hasSystemPermission(context)
        val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(
            context
        )

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission
        ) {
            pendingSaveAfterNotificationPermission = true
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
            return false
        }

        if (!systemEnabled) {
            pendingSaveAfterNotificationPermission = false
            openNotificationPermissionSheet()
            return false
        }

        return true
    }

    private fun openNotificationPermissionSheet() {
        NotificationsBottomSheet(
            NotificationsBottomSheet.PermissionType.NOTIFICATION
        ).show(
            parentFragmentManager,
            "care_task_notification_sheet"
        )
    }

    private fun saveTask() {
        if (ensureNotificationPermissionBeforeSave()) {
            saveTaskInternal()
        }
    }

    private fun saveTaskInternal() {
        val type = selectedType
        if (type == null) {
            showSnackBar(
                getString(R.string.maintenance_validation_select_task_type),
                BaseActivity.SnackType.WARNING
            )
            return
        }
        if (selectedTankId == 0L) {
            showSnackBar(
                getString(R.string.maintenance_validation_select_aquarium),
                BaseActivity.SnackType.WARNING
            )
            return
        }
        if (
            type == CareTaskType.WATER_CHANGE &&
            selectedWaterChangePercent == null
        ) {
            showSnackBar(
                getString(
                    R.string.maintenance_validation_select_water_change_percentage
                ),
                BaseActivity.SnackType.WARNING
            )
            return
        }

        val typeUi = CareTaskTypeCatalog.get(type)
        val customTitle = binding.etCustomTitle.text.toString().trim()
        if (type == CareTaskType.CUSTOM && customTitle.length < 2) {
            showSnackBar(
                getString(R.string.maintenance_validation_custom_title_short),
                BaseActivity.SnackType.WARNING
            )
            return
        }

        val repeatDays = binding.etRepeatDays.text.toString()
            .toIntOrNull()
            ?.coerceAtLeast(1)
            ?: getString(R.string.maintenance_default_repeat_days).toInt()
        val missedDays = binding.etMissedReminderDays.text.toString()
            .toIntOrNull()
            ?.coerceAtLeast(1)
            ?: getString(
                R.string.maintenance_default_missed_reminder_days
            ).toInt()
        val title = if (type == CareTaskType.CUSTOM) {
            customTitle
        } else {
            typeUi.title(requireContext())
        }
        val description = typeUi.defaultDescription(requireContext())
        val waterPercent = selectedWaterChangePercent.takeIf {
            type == CareTaskType.WATER_CHANGE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            var savedSuccessfully = false
            try {
                showGlobalLoading(true)
                if (binding.switchReminder.isChecked) {
                    userSettingsOperations.updateNotificationsEnabled(true)
                }

                if (isEditMode) {
                    maintenanceViewModel.updateManualTask(
                        taskId = taskId,
                        tankId = selectedTankId,
                        title = title,
                        description = description,
                        type = type,
                        dueAtMillis = selectedCalendar.timeInMillis,
                        repeatEnabled = binding.switchRepeat.isChecked,
                        repeatIntervalDays = repeatDays,
                        reminderEnabled = binding.switchReminder.isChecked,
                        missedReminderEnabled =
                            binding.switchReminder.isChecked &&
                                binding.switchMissedReminder.isChecked,
                        missedReminderDays = missedDays,
                        waterChangePercent = waterPercent,
                        note = binding.etNote.text.toString().trim()
                    )
                } else {
                    maintenanceViewModel.addManualTask(
                        tankId = selectedTankId,
                        title = title,
                        description = description,
                        type = type,
                        dueAtMillis = selectedCalendar.timeInMillis,
                        repeatEnabled = binding.switchRepeat.isChecked,
                        repeatIntervalDays = repeatDays,
                        reminderEnabled = binding.switchReminder.isChecked,
                        missedReminderEnabled =
                            binding.switchReminder.isChecked &&
                                binding.switchMissedReminder.isChecked,
                        missedReminderDays = missedDays,
                        waterChangePercent = waterPercent,
                        note = binding.etNote.text.toString().trim()
                    )
                }
                savedSuccessfully = true
            } finally {
                showGlobalLoading(false)
            }

            if (savedSuccessfully) {
                closeForm()
            }
        }
    }

    private fun closeForm() {
        findNavController().navigateUp()
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType
    ) {
        (activity as? BaseActivity)?.showSnackBar(message, type)
    }

    private fun createIconBackground(
        color: Int,
        selected: Boolean
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 13.dp().toFloat()
            setColor(
                applyAlpha(color, if (selected) 0.34f else 0.22f)
            )
            setStroke(
                1.dp(),
                applyAlpha(color, if (selected) 0.9f else 0.55f)
            )
        }
    }

    private fun showGlobalLoading(show: Boolean) {
        setFragmentGlobalLoading(show)
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
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
        super.onDestroyView()
        _binding = null
    }
}
