package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.application.user.UserDataBackupInspection
import com.aqua.aqualight.application.user.UserDataRestoreResult
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDataManagementBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.platform.documents.UserDataCreateDocumentContract
import com.aqua.aqualight.platform.documents.UserDataDocumentCreateRequest
import com.aqua.aqualight.platform.documents.UserDataOpenBackupDocumentContract
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import kotlinx.coroutines.launch

class DataManagementFragment : Fragment(R.layout.fragment_data_management) {

    private var _binding: FragmentDataManagementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DataManagementViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private val createDocumentLauncher = registerForActivityResult(
        UserDataCreateDocumentContract()
    ) { documentHandle ->
        if (documentHandle == null) {
            viewModel.cancelDocumentSelection()
        } else {
            viewModel.writePendingDocument(documentHandle)
        }
    }

    private val openBackupLauncher = registerForActivityResult(
        UserDataOpenBackupDocumentContract()
    ) { documentHandle ->
        if (documentHandle == null) {
            viewModel.cancelDocumentSelection()
        } else {
            viewModel.inspectRestoreDocument(documentHandle)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDataManagementBinding.bind(view)
        setupHeader()
        setupClicks()
        setupFeedbackResultListener()
        observeViewModel()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(fragment = this)
    }

    private fun setupClicks() = with(binding) {
        cardCreateBackup.setOnClickListener { viewModel.requestBackup() }
        cardRestoreBackup.setOnClickListener { viewModel.requestRestoreDocument() }
        cardExportData.setOnClickListener { viewModel.requestPortableExport() }
    }

    private fun setupFeedbackResultListener() {
        childFragmentManager.setFragmentResultListener(
            RESTORE_CONFIRM_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(FeedbackBottomSheet.RESULT_KEY) ==
                FeedbackBottomSheet.RESULT_PRIMARY
            ) {
                viewModel.confirmRestore()
            } else {
                viewModel.cancelRestorePreview()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderState) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun renderState(state: DataManagementUiState) {
        setFragmentGlobalLoading(state.busy)
        binding.cardCreateBackup.isEnabled = !state.busy
        binding.cardRestoreBackup.isEnabled = !state.busy
        binding.cardExportData.isEnabled = !state.busy
    }

    private fun handleEvent(event: DataManagementEvent) {
        when (event) {
            is DataManagementEvent.CreateDocument -> createDocumentLauncher.launch(
                UserDataDocumentCreateRequest(
                    suggestedFileName = event.suggestedFileName,
                    mimeType = event.mimeType
                )
            )

            DataManagementEvent.OpenBackupDocument -> openBackupLauncher.launch(Unit)
            is DataManagementEvent.ShowRestorePreview -> showRestorePreview(event.inspection)
            is DataManagementEvent.OperationSucceeded -> showOperationSuccess(event.action)
            is DataManagementEvent.OperationFailed -> showOperationFailure(event.action)
            is DataManagementEvent.RestoreSucceeded -> showRestoreSuccess(event.result)
        }
    }

    private fun showRestorePreview(inspection: UserDataBackupInspection) {
        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.data_management_restore_preview_title),
            message = getString(
                R.string.data_management_restore_preview_message,
                LocaleFormatter.formatDateTime(requireContext(), inspection.createdAtMillis),
                inspection.sourceAppVersion,
                inspection.aquariumCount,
                inspection.careTaskCount,
                inspection.deviceAssignmentCount,
                inspection.photoCount
            ),
            primaryText = getString(R.string.data_management_restore_confirm),
            cancelText = getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.WARNING,
            requestKey = RESTORE_CONFIRM_REQUEST_KEY,
            actionId = ACTION_CONFIRM_RESTORE
        )
    }

    private fun showOperationSuccess(action: DataManagementAction) {
        when (action) {
            DataManagementAction.BACKUP -> showInfo(
                title = R.string.data_management_backup_success_title,
                message = R.string.data_management_backup_success_message,
                tone = FeedbackBottomSheet.FeedbackTone.SUCCESS
            )

            DataManagementAction.EXPORT -> showInfo(
                title = R.string.data_management_export_success_title,
                message = R.string.data_management_export_success_message,
                tone = FeedbackBottomSheet.FeedbackTone.SUCCESS
            )

            DataManagementAction.RESTORE -> Unit
        }
    }

    private fun showOperationFailure(action: DataManagementAction) {
        val resources = when (action) {
            DataManagementAction.BACKUP ->
                R.string.data_management_backup_error_title to
                    R.string.data_management_backup_error_message
            DataManagementAction.RESTORE ->
                R.string.data_management_restore_error_title to
                    R.string.data_management_restore_error_message
            DataManagementAction.EXPORT ->
                R.string.data_management_export_error_title to
                    R.string.data_management_export_error_message
        }
        showInfo(
            title = resources.first,
            message = resources.second,
            tone = FeedbackBottomSheet.FeedbackTone.ERROR
        )
    }

    private fun showRestoreSuccess(result: UserDataRestoreResult) {
        if (result.reminderReconciliationWarning) {
            showInfo(
                title = R.string.data_management_restore_warning_title,
                message = R.string.data_management_restore_warning_message,
                tone = FeedbackBottomSheet.FeedbackTone.WARNING
            )
            return
        }
        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.data_management_restore_success_title),
            message = getString(
                R.string.data_management_restore_success_message,
                result.restoredAquariumCount,
                result.restoredCareTaskCount,
                result.restoredDeviceAssignmentCount,
                result.skippedDeviceAssignmentCount
            ),
            primaryText = getString(R.string.ok),
            cancelText = null,
            tone = FeedbackBottomSheet.FeedbackTone.SUCCESS,
            requestKey = RESULT_INFO_REQUEST_KEY,
            actionId = ACTION_DISMISS_INFO
        )
    }

    private fun showInfo(
        title: Int,
        message: Int,
        tone: FeedbackBottomSheet.FeedbackTone
    ) {
        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(title),
            message = getString(message),
            primaryText = getString(R.string.ok),
            cancelText = null,
            tone = tone,
            requestKey = RESULT_INFO_REQUEST_KEY,
            actionId = ACTION_DISMISS_INFO
        )
    }

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val RESTORE_CONFIRM_REQUEST_KEY = "data_management_restore_confirm"
        const val RESULT_INFO_REQUEST_KEY = "data_management_info"
        const val ACTION_CONFIRM_RESTORE = "confirm_restore"
        const val ACTION_DISMISS_INFO = "dismiss_info"
    }
}
