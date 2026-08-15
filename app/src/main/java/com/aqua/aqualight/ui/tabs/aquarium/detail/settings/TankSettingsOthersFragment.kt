package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.DeleteAquariumTanksResult
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentTankSettingsOthersBinding
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.common.notification.NotificationEnablementCallbacks
import com.aqua.aqualight.ui.common.notification.NotificationEnablementCoordinator
import com.aqua.aqualight.ui.common.notification.NotificationEnablementDependencies
import com.aqua.aqualight.ui.common.notification.NotificationEnablementRequest
import com.aqua.aqualight.ui.common.notification.NotificationEnablementState
import com.aqua.aqualight.ui.common.notification.NotificationEnablementStep
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.export.TankPdfExporter
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions") // Fragment lifecycle and feature intents stay deliberately local.
class TankSettingsOthersFragment : Fragment(R.layout.fragment_tank_settings_others) {

    private var _binding: FragmentTankSettingsOthersBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private val appContainer by lazy {
        requireContext().requireAppContainer()
    }
    private val notificationEnablementCoordinator = NotificationEnablementCoordinator(
        fragment = this,
        instanceKey = "tank-care-reminders",
        dependencies = NotificationEnablementDependencies(
            notificationPreferencesProvider = {
                appContainer.notificationPreferenceUseCase
            },
            ownerUidProvider = {
                appContainer.authenticatedOwnerIdentity.requireOwnerUid()
            },
            requestResolver = { actionToken ->
                if (actionToken == ACTION_ENABLE_CARE_REMINDERS) {
                    NotificationEnablementRequest(
                        category = NotificationCategory.CARE_REMINDERS,
                        requiresPreciseReminders = true
                    )
                } else {
                    null
                }
            }
        ),
        callbacks = NotificationEnablementCallbacks(
            onReady = { actionToken ->
                if (actionToken == ACTION_ENABLE_CARE_REMINDERS) {
                    enableCareRemindersAfterAccess()
                }
            },
            onStateChanged = { actionToken, state ->
                if (actionToken == ACTION_ENABLE_CARE_REMINDERS) {
                    careReminderNotificationState = state
                    renderCareReminderNotificationState()
                }
            },
            onFailure = { actionToken, _ ->
                if (actionToken == ACTION_ENABLE_CARE_REMINDERS) {
                    handleCareReminderAccessFailure()
                }
            }
        )
    )

    private var tankId: Long = 0L
    private var currentTank: AquariumTankSnapshot? = null
    private var careReminderNotificationState: NotificationEnablementState? = null

    private var isDeletingTank: Boolean = false
    private var isDuplicatingTank: Boolean = false
    private var isExportingTank: Boolean = false
    private var isUpdatingSwitchesProgrammatically: Boolean = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentTankSettingsOthersBinding.bind(view)

        setupClickListeners()
        setupFeedbackResultListener()
        observeTank()
    }

    override fun onResume() {
        super.onResume()
        refreshCareReminderNotificationState()
    }

    private fun setupFeedbackResultListener() {
        childFragmentManager.setFragmentResultListener(
            OTHER_SETTINGS_FEEDBACK_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            when (result.getString(FeedbackBottomSheet.RESULT_ACTION_ID)) {
                ACTION_DUPLICATE -> if (
                    result.getString(FeedbackBottomSheet.RESULT_KEY) ==
                    FeedbackBottomSheet.RESULT_PRIMARY
                ) duplicateCurrentTank()
                ACTION_DELETE -> if (
                    result.getString(FeedbackBottomSheet.RESULT_KEY) ==
                    FeedbackBottomSheet.RESULT_PRIMARY
                ) deleteCurrentTank()
                ACTION_MISSING -> findNavController().navigateUp()
            }
        }
    }

    private fun setupClickListeners() {
        binding.rowSmartCareSuggestions.setOnClickListener {
            binding.switchSmartCareSuggestions.isChecked =
                !binding.switchSmartCareSuggestions.isChecked
        }

        binding.switchSmartCareSuggestions.setOnCheckedChangeListener {
                _, isChecked ->
            if (!isUpdatingSwitchesProgrammatically) {
                updateSmartCareEnabled(
                    enabled = isChecked
                )
            }
        }

        binding.rowCareReminderNotifications.setOnClickListener {
            if (
                currentTank?.careRemindersEnabled == true &&
                careReminderNotificationState?.canDeliver == false
            ) {
                notificationEnablementCoordinator.requestEnable(
                    ACTION_ENABLE_CARE_REMINDERS
                )
            } else {
                binding.switchCareReminderNotifications.isChecked =
                    !binding.switchCareReminderNotifications.isChecked
            }
        }

        binding.switchCareReminderNotifications.setOnCheckedChangeListener {
                _, isChecked ->
            if (!isUpdatingSwitchesProgrammatically) {
                if (isChecked) {
                    setCareReminderSwitchChecked(false)
                    notificationEnablementCoordinator.requestEnable(
                        ACTION_ENABLE_CARE_REMINDERS
                    )
                } else {
                    notificationEnablementCoordinator.cancelPending()
                    updateCareRemindersEnabled(enabled = false)
                }
            }
        }

        binding.rowDuplicateTank.setOnClickListener {
            showDuplicateTankConfirmationDialog()
        }

        binding.rowExportTankData.setOnClickListener {
            exportTankDataAsPdf()
        }

        binding.rowDeleteTank.setOnClickListener {
            showDeleteTankConfirmationDialog()
        }
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) {
                tanks ->
            val tank = tanks.firstOrNull {
                    savedTank ->
                savedTank.id == tankId
            }

            if (tank == null) {
                if (isDeletingTank) {
                    return@observe
                }

                FeedbackBottomSheet.show(
                    fragmentManager = childFragmentManager,
                    title = getString(R.string.aquarium_tank_not_found_title),
                    message = getString(R.string.aquarium_tank_no_longer_exists_message),
                    primaryText = getString(R.string.ok),
                    cancelText = null,
                    tone = FeedbackBottomSheet.FeedbackTone.ERROR,
                    requestKey = OTHER_SETTINGS_FEEDBACK_REQUEST_KEY,
                    actionId = ACTION_MISSING
                )

                return@observe
            }

            currentTank = tank
            updateSwitchesFromTank(tank)
        }
    }

    private fun updateSwitchesFromTank(
        tank: AquariumTankSnapshot
    ) {
        isUpdatingSwitchesProgrammatically = true

        binding.switchSmartCareSuggestions.isChecked =
            tank.smartCareEnabled

        binding.switchCareReminderNotifications.isChecked =
            tank.careRemindersEnabled

        isUpdatingSwitchesProgrammatically = false
        renderCareReminderNotificationState()
    }

    private fun refreshCareReminderNotificationState() {
        notificationEnablementCoordinator.refresh(ACTION_ENABLE_CARE_REMINDERS)
    }

    private fun enableCareRemindersAfterAccess() {
        if (_binding == null) return
        if (currentTank?.careRemindersEnabled == true) {
            renderCareReminderNotificationState()
            return
        }
        setCareReminderSwitchChecked(true)
        updateCareRemindersEnabled(enabled = true)
    }

    private fun renderCareReminderNotificationState() {
        val currentBinding = _binding ?: return
        val featureEnabled = currentTank?.careRemindersEnabled == true
        val notificationState = careReminderNotificationState
        currentBinding.tvCareReminderNotificationsSubtitle.setText(
            when {
                !featureEnabled || notificationState == null ||
                    notificationState.canDeliver -> {
                    R.string.aquarium_text_send_reminder_notifications_for_this_tank_s_care_tasks
                }
                notificationState.step != NotificationEnablementStep.READY -> {
                    R.string.notification_feature_enabled_android_blocked_tap_to_fix
                }
                else -> R.string.notification_feature_owner_preference_disabled_tap_to_enable
            }
        )
    }

    private fun handleCareReminderAccessFailure() {
        if (_binding == null) return
        setCareReminderSwitchChecked(currentTank?.careRemindersEnabled == true)
        renderCareReminderNotificationState()
        showSnackBar(
            message = getString(R.string.notification_feature_access_check_failed),
            type = BaseActivity.SnackType.ERROR
        )
    }

    private fun setCareReminderSwitchChecked(checked: Boolean) {
        val currentBinding = _binding ?: return
        isUpdatingSwitchesProgrammatically = true
        currentBinding.switchCareReminderNotifications.isChecked = checked
        isUpdatingSwitchesProgrammatically = false
    }

    private fun updateSmartCareEnabled(
        enabled: Boolean
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.updateSmartCareEnabled(
                    tankId = tankId,
                    enabled = enabled
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                isUpdatingSwitchesProgrammatically = true
                binding.switchSmartCareSuggestions.isChecked = !enabled
                isUpdatingSwitchesProgrammatically = false

                showSnackBar(
                    message = getString(R.string.aquarium_error_smart_care_save_failed),
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    private fun updateCareRemindersEnabled(
        enabled: Boolean
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.updateCareRemindersEnabled(
                    tankId = tankId,
                    enabled = enabled
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                setCareReminderSwitchChecked(!enabled)

                showSnackBar(
                    message = getString(R.string.aquarium_error_care_reminder_save_failed),
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
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

    private fun showDuplicateTankConfirmationDialog() {
        val tank = currentTank ?: return

        if (isDuplicatingTank) {
            return
        }

        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.aquarium_duplicate_tank_title),
            message = getString(R.string.aquarium_duplicate_tank_message, tank.name),
            primaryText = getString(R.string.duplicate),
            cancelText = getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.INFO,
            requestKey = OTHER_SETTINGS_FEEDBACK_REQUEST_KEY,
            actionId = ACTION_DUPLICATE
        )
    }

    private fun duplicateCurrentTank() {
        if (isDuplicatingTank) {
            return
        }

        isDuplicatingTank = true

        setFragmentGlobalLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.duplicateTank(
                    tankId = tankId
                )

                setFragmentGlobalLoading(false)

                val popped = findNavController().popBackStack(
                    R.id.aquariumFragment,
                    false
                )

                if (!popped) {
                    findNavController().navigate(
                        TankSettingsFragmentDirections.actionTankSettingsFragmentToAquariumFragment()
                    )
                }
            } catch (exception: Exception) {
                exception.printStackTrace()

                isDuplicatingTank = false
                setFragmentGlobalLoading(false)

                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = getString(R.string.aquarium_duplicate_failed_title),
                    message = getString(R.string.aquarium_error_tank_duplicate_failed)
                )
            }
        }
    }

    private fun exportTankDataAsPdf() {
        val tank = currentTank ?: return

        if (isExportingTank) {
            return
        }

        isExportingTank = true

        val appContext = requireContext().applicationContext

        setFragmentGlobalLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pdfUri = withContext(Dispatchers.IO) {
                    TankPdfExporter.createTankReportPdf(
                        context = appContext,
                        tank = tank
                    )
                }

                setFragmentGlobalLoading(false)
                isExportingTank = false

                TankPdfExporter.shareTankReportPdf(
                    context = requireContext(),
                    pdfUri = pdfUri,
                    tankName = tank.name
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                isExportingTank = false
                setFragmentGlobalLoading(false)

                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = getString(R.string.aquarium_export_failed_title),
                    message = getString(R.string.aquarium_error_tank_report_create_failed)
                )
            }
        }
    }

    private fun showDeleteTankConfirmationDialog() {
        val tank = currentTank ?: return

        if (isDeletingTank) {
            return
        }

        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.aquarium_delete_tank_title),
            message = getString(R.string.aquarium_delete_tank_message, tank.name),
            primaryText = getString(R.string.delete),
            cancelText = getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.DANGER,
            requestKey = OTHER_SETTINGS_FEEDBACK_REQUEST_KEY,
            actionId = ACTION_DELETE
        )
    }

    private fun deleteCurrentTank() {
        if (isDeletingTank) {
            return
        }

        isDeletingTank = true

        (parentFragment as? TankSettingsFragment)
            ?.markTankDeletionInProgress()

        setFragmentGlobalLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (
                    val result = aquariumTankViewModel.deleteTanks(
                        tankIds = listOf(tankId)
                    )
                ) {
                    DeleteAquariumTanksResult.NoOp -> {
                        throw IllegalArgumentException(
                            "Tank deletion requires a valid tank id."
                        )
                    }

                    DeleteAquariumTanksResult.DeleteFailed -> {
                        throw IllegalStateException("Tank deletion failed.")
                    }

                    is DeleteAquariumTanksResult.Deleted -> {
                        if (result.hasCleanupIssues) {
                            showSnackBar(
                                message = getString(
                                    R.string.aquarium_delete_cleanup_warning_message
                                ),
                                type = BaseActivity.SnackType.ERROR
                            )
                        }
                    }
                }

                setFragmentGlobalLoading(false)

                val popped = findNavController().popBackStack(
                    R.id.aquariumFragment,
                    false
                )

                if (!popped) {
                    findNavController().navigate(
                        TankSettingsFragmentDirections.actionTankSettingsFragmentToAquariumFragment()
                    )
                }
            } catch (exception: CancellationException) {
                (parentFragment as? TankSettingsFragment)
                    ?.markTankDeletionFinished()
                throw exception
            } catch (exception: Exception) {
                exception.printStackTrace()

                isDeletingTank = false
                (parentFragment as? TankSettingsFragment)
                    ?.markTankDeletionFinished()
                setFragmentGlobalLoading(false)

                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = getString(R.string.aquarium_delete_failed_title),
                    message = getString(R.string.aquarium_error_tank_delete_failed)
                )
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"
        private const val OTHER_SETTINGS_FEEDBACK_REQUEST_KEY = "tank_other_settings_feedback"
        private const val ACTION_DUPLICATE = "duplicate"
        private const val ACTION_DELETE = "delete"
        private const val ACTION_MISSING = "missing"
        private const val ACTION_ENABLE_CARE_REMINDERS = "enable-tank-care-reminders"

        fun newInstance(
            tankId: Long
        ): TankSettingsOthersFragment {
            return TankSettingsOthersFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}
