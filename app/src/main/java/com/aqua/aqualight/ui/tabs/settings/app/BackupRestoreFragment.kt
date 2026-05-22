package com.aqua.aqualight.ui.tabs.settings.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.backup.BackupConstants
import com.aqua.aqualight.data.backup.BackupManager
import com.aqua.aqualight.databinding.FragmentBackupRestoreBinding
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class BackupRestoreFragment : Fragment(R.layout.fragment_backup_restore) {

    private var _binding: FragmentBackupRestoreBinding? = null
    private val binding get() = _binding!!

    private lateinit var backupManager: BackupManager

    private val createBackupDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(
            BackupConstants.BACKUP_MIME_TYPE
        )
    ) { uri ->
        if (uri != null) {
            exportBackup(uri)
        }
    }

    private val openBackupDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            confirmRestoreBackup(uri)
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentBackupRestoreBinding.bind(view)
        backupManager = BackupManager.create(requireContext())

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnExportBackup.setOnClickListener {
            createBackupDocumentLauncher.launch(
                backupManager.createSuggestedFileName()
            )
        }

        binding.btnRestoreBackup.setOnClickListener {
            openBackupDocumentLauncher.launch(
                arrayOf(
                    BackupConstants.BACKUP_MIME_TYPE,
                    "application/octet-stream",
                    "*/*"
                )
            )
        }
    }

    private fun exportBackup(
        outputUri: Uri
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showLoading(true)

                val result = backupManager.exportBackupToUri(
                    outputUri = outputUri
                )

                showSnackBar(
                    message = "Backup created: ${result.tankCount} tanks, ${result.deviceCount} devices.",
                    type = BaseActivity.SnackType.SUCCESS
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                showSnackBar(
                    message = "Backup could not be created.",
                    type = BaseActivity.SnackType.ERROR
                )
            } finally {
                showLoading(false)
            }
        }
    }

    private fun confirmRestoreBackup(
        inputUri: Uri
    ) {
        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = "Restore Backup?",
            message = "This will replace your current tanks, devices and care history with the selected backup.",
            confirmTextResId = R.string.confirm,
            cancelTextResId = R.string.cancel,
            onConfirm = {
                restoreBackup(inputUri)
            }
        )
    }

    private fun restoreBackup(
        inputUri: Uri
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showLoading(true)

                val result = backupManager.restoreBackupFromUri(
                    inputUri = inputUri
                )

                showSnackBar(
                    message = "Backup restored: ${result.tankCount} tanks, ${result.deviceCount} devices.",
                    type = BaseActivity.SnackType.SUCCESS
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                showSnackBar(
                    message = "Backup could not be restored.",
                    type = BaseActivity.SnackType.ERROR
                )
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(
        show: Boolean
    ) {
        (activity as? BaseActivity)?.showLoading(show)
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
        _binding = null
        super.onDestroyView()
    }
}