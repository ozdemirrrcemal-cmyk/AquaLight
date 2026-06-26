package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.databinding.FragmentTankSettingsOthersBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.export.TankPdfExporter
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TankSettingsOthersFragment : Fragment(R.layout.fragment_tank_settings_others) {

    private var _binding: FragmentTankSettingsOthersBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()


    private var tankId: Long = 0L
    private var currentTank: SavedAquariumTank? = null

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
        observeTank()
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
            binding.switchCareReminderNotifications.isChecked =
            !binding.switchCareReminderNotifications.isChecked
        }

        binding.switchCareReminderNotifications.setOnCheckedChangeListener {
            _, isChecked ->
            if (!isUpdatingSwitchesProgrammatically) {
                updateCareRemindersEnabled(
                    enabled = isChecked
                )
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

                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = getString(R.string.aquarium_tank_not_found_title),
                    message = getString(R.string.aquarium_tank_no_longer_exists_message),
                    onDismiss = {
                        findNavController().navigateUp()
                    }
                )

                return@observe
            }

            currentTank = tank
            updateSwitchesFromTank(tank)
        }
    }

    private fun updateSwitchesFromTank(
        tank: SavedAquariumTank
    ) {
        isUpdatingSwitchesProgrammatically = true

        binding.switchSmartCareSuggestions.isChecked =
        tank.smartCareEnabled

        binding.switchCareReminderNotifications.isChecked =
        tank.careRemindersEnabled

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

                isUpdatingSwitchesProgrammatically = true
                binding.switchCareReminderNotifications.isChecked = !enabled
                isUpdatingSwitchesProgrammatically = false

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

        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.INFO,
            title = getString(R.string.aquarium_duplicate_tank_title),
            message = getString(R.string.aquarium_duplicate_tank_message, tank.name),
            confirmTextResId = R.string.duplicate,
            cancelTextResId = R.string.cancel,
            onConfirm = {
                duplicateCurrentTank()
            }
        )
    }

    private fun duplicateCurrentTank() {
        if (isDuplicatingTank) {
            return
        }

        isDuplicatingTank = true

        val baseActivity = activity as? BaseActivity
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
        val baseActivity = activity as? BaseActivity

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

        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = getString(R.string.aquarium_delete_tank_title),
            message = getString(R.string.aquarium_delete_tank_message, tank.name),
            confirmTextResId = R.string.delete,
            cancelTextResId = R.string.cancel,
            onConfirm = {
                deleteCurrentTank()
            }
        )
    }

    private fun deleteCurrentTank() {
        if (isDeletingTank) {
            return
        }

        isDeletingTank = true

        (parentFragment as? TankSettingsFragment)
        ?.markTankDeletionInProgress()

        val baseActivity = activity as? BaseActivity
        setFragmentGlobalLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.deleteTanks(
                    tankIds = listOf(tankId)
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

                isDeletingTank = false
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
