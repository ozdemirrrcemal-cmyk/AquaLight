package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.databinding.FragmentTankSettingsOthersBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.export.TankPdfExporter
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TankSettingsOthersFragment : Fragment(R.layout.fragment_tank_settings_others) {

    private var _binding: FragmentTankSettingsOthersBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private lateinit var devicesStore: DevicesDataStoreManager

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
        devicesStore = DevicesDataStoreManager.create(requireContext())

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
                viewLifecycleOwner.lifecycleScope.launch {
                    aquariumTankViewModel.updateSmartCareEnabled(
                        tankId = tankId,
                        enabled = isChecked
                    )
                }
            }
        }

        binding.rowCareReminderNotifications.setOnClickListener {
            binding.switchCareReminderNotifications.isChecked =
            !binding.switchCareReminderNotifications.isChecked
        }

        binding.switchCareReminderNotifications.setOnCheckedChangeListener {
            _, isChecked ->
            if (!isUpdatingSwitchesProgrammatically) {
                viewLifecycleOwner.lifecycleScope.launch {
                    aquariumTankViewModel.updateCareRemindersEnabled(
                        tankId = tankId,
                        enabled = isChecked
                    )
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

                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = "Tank Not Found",
                    message = "This tank no longer exists.",
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

    private fun showDuplicateTankConfirmationDialog() {
        val tank = currentTank ?: return

        if (isDuplicatingTank) {
            return
        }

        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.INFO,
            title = "Duplicate Tank?",
            message = "This will create a copy of \"${tank.name}\" with the same tank data, plants and components.",
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
        baseActivity?.showLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.duplicateTank(
                    tankId = tankId
                )

                baseActivity?.showLoading(false)

                val popped = findNavController().popBackStack(
                    R.id.aquariumFragment,
                    false
                )

                if (!popped) {
                    findNavController().navigate(
                        R.id.action_tankSettingsFragment_to_aquariumFragment
                    )
                }
            } catch (exception: Exception) {
                exception.printStackTrace()

                isDuplicatingTank = false
                baseActivity?.showLoading(false)

                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = "Duplicate Failed",
                    message = "Tank could not be duplicated."
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

        baseActivity?.showLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pdfUri = withContext(Dispatchers.IO) {
                    val connectedDevices = devicesStore.devicesForTankFlow(
                        tankId = tankId
                    ).first()

                    TankPdfExporter.createTankReportPdf(
                        context = appContext,
                        tank = tank,
                        devices = connectedDevices
                    )
                }

                baseActivity?.showLoading(false)
                isExportingTank = false

                TankPdfExporter.shareTankReportPdf(
                    context = requireContext(),
                    pdfUri = pdfUri,
                    tankName = tank.name
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                isExportingTank = false
                baseActivity?.showLoading(false)

                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = "Export Failed",
                    message = "Tank report could not be created."
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
            title = "Delete Tank?",
            message = "This will permanently delete \"${tank.name}\" and all saved tank data.",
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
        baseActivity?.showLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.deleteTanks(
                    tankIds = listOf(tankId)
                )

                baseActivity?.showLoading(false)

                val popped = findNavController().popBackStack(
                    R.id.aquariumFragment,
                    false
                )

                if (!popped) {
                    findNavController().navigate(
                        R.id.action_tankSettingsFragment_to_aquariumFragment
                    )
                }
            } catch (exception: Exception) {
                exception.printStackTrace()

                isDeletingTank = false
                baseActivity?.showLoading(false)

                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = "Delete Failed",
                    message = "Tank could not be deleted."
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