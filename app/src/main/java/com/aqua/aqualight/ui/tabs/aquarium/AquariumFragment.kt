package com.aqua.aqualight.ui.tabs.aquarium

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentAquariumBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class AquariumFragment : Fragment(R.layout.fragment_aquarium) {

    private var _binding: FragmentAquariumBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()

    private var isDeleteMode = false
    private var isDeletingTanks = false
    private val selectedTankIds = mutableSetOf<Long>()

    private val tankAdapter = AquariumTankAdapter(
        onTankClick = { tank ->
            handleTankClick(tank)
        },
        onTankLongClick = { tank ->
            handleTankLongClick(tank)
        }
    )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentAquariumBinding.bind(view)

        setupHeader()
        setupRecyclerView()
        observeTanks()
        observeCareSummary()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                showBackButton = false,
                primaryAction = AquaHeaderPrimaryAction(
                    text = if (isDeleteMode) {
                        "Delete"
                    } else {
                        "+ Add"
                    },
                    contentDescription = if (isDeleteMode) {
                        "Delete selected aquariums"
                    } else {
                        "Add Aquarium"
                    },
                    onClick = {
                        if (isDeleteMode) {
                            showDeleteConfirmDialog()
                        } else {
                            findNavController().navigate(
                                R.id.action_aquariumFragment_to_createTankFragment
                            )
                        }
                    }
                )
            )
        )

        applyPrimaryActionStyle()
    }

    private fun setupRecyclerView() {
        binding.rvTanks.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        binding.rvTanks.adapter =
            tankAdapter

        binding.rvTanks.setHasFixedSize(
            false
        )
    }

    private fun observeTanks() {
        aquariumTankViewModel.tanks.observe(
            viewLifecycleOwner
        ) { tanks ->

            maintenanceViewModel.setTanks(
                tanks
            )

            tankAdapter.submitList(
                tanks
            )

            binding.rvTanks.isVisible =
                tanks.isNotEmpty()

            binding.tvEmptyState.isVisible =
                tanks.isEmpty()

            if (tanks.isEmpty()) {
                exitDeleteMode()
                return@observe
            }

            val existingTankIds =
                tanks.map { tank ->
                    tank.id
                }.toSet()

            selectedTankIds.retainAll(
                existingTankIds
            )

            if (
                isDeleteMode &&
                selectedTankIds.isEmpty()
            ) {
                exitDeleteMode()
            } else {
                updateDeleteModeUi()
            }
        }
    }

    private fun observeCareSummary() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                maintenanceViewModel.tankCareSummaryItems.collect { summaries ->
                    tankAdapter.setCareSummaryByTankId(
                        summaries
                    )
                }
            }
        }
    }

    private fun handleTankClick(
        tank: SavedAquariumTank
    ) {
        if (isDeleteMode) {
            toggleTankSelection(
                tank.id
            )
        } else {
            findNavController().navigate(
                R.id.action_aquariumFragment_to_tankDetailFragment,
                Bundle().apply {
                    putLong(
                        "tankId",
                        tank.id
                    )
                }
            )
        }
    }

    private fun handleTankLongClick(
        tank: SavedAquariumTank
    ) {
        if (!isDeleteMode) {
            enterDeleteMode()
        }

        if (!selectedTankIds.contains(tank.id)) {
            selectedTankIds.add(
                tank.id
            )

            updateDeleteModeUi()
        }
    }

    private fun enterDeleteMode() {
        isDeleteMode =
            true

        updateDeleteModeUi()
    }

    private fun exitDeleteMode() {
        isDeleteMode =
            false

        selectedTankIds.clear()

        updateDeleteModeUi()
    }

    private fun toggleTankSelection(
        tankId: Long
    ) {
        if (selectedTankIds.contains(tankId)) {
            selectedTankIds.remove(
                tankId
            )
        } else {
            selectedTankIds.add(
                tankId
            )
        }

        if (selectedTankIds.isEmpty()) {
            exitDeleteMode()
        } else {
            updateDeleteModeUi()
        }
    }

    private fun updateDeleteModeUi() {
        if (_binding == null) {
            return
        }

        setupHeader()

        tankAdapter.setDeleteMode(
            enabled = isDeleteMode,
            selectedIds = selectedTankIds
        )
    }

    private fun applyPrimaryActionStyle() {
        val button =
            binding.appHeader.btnPrimaryAction

        if (isDeleteMode) {
            button.setTextColor(
                Color.parseColor("#FF8A8A")
            )

            button.backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor("#321E2A")
                )

            button.strokeWidth =
                1.dp()

            button.strokeColor =
                ColorStateList.valueOf(
                    Color.parseColor("#7A3344")
                )
        } else {
            button.setTextColor(
                Color.WHITE
            )

            button.backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor("#1C3252")
                )

            button.strokeWidth =
                0

            button.strokeColor =
                ColorStateList.valueOf(
                    Color.TRANSPARENT
                )
        }
    }

    private fun showDeleteConfirmDialog() {
        if (isDeletingTanks) {
            return
        }

        if (selectedTankIds.isEmpty()) {
            exitDeleteMode()
            return
        }

        val tankIdsToDelete =
            selectedTankIds.toSet()

        val selectedCount =
            tankIdsToDelete.size

        val title =
            if (selectedCount == 1) {
                "Delete aquarium?"
            } else {
                "Delete aquariums?"
            }

        val message =
            if (selectedCount == 1) {
                "Selected aquarium will be permanently deleted."
            } else {
                "$selectedCount selected aquariums will be permanently deleted."
            }

        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = title,
            message = message,
            confirmTextResId = R.string.confirm,
            cancelTextResId = R.string.cancel,
            onConfirm = {
                deleteSelectedTanks(
                    tankIdsToDelete
                )
            },
            onCancel = {
                exitDeleteMode()
            }
        )
    }

    private fun deleteSelectedTanks(
        tankIdsToDelete: Set<Long>
    ) {
        if (isDeletingTanks) {
            return
        }

        if (tankIdsToDelete.isEmpty()) {
            exitDeleteMode()
            return
        }

        isDeletingTanks =
            true

        val baseActivity =
            activity as? BaseActivity

        baseActivity?.showLoading(
            true
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.deleteTanks(
                    tankIds = tankIdsToDelete.toList()
                )

                exitDeleteMode()
            } catch (exception: Exception) {
                exception.printStackTrace()

                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = "Delete Failed",
                    message = "Selected aquariums could not be deleted."
                )
            } finally {
                isDeletingTanks =
                    false

                baseActivity?.showLoading(
                    false
                )
            }
        }
    }

    private fun Int.dp(): Int {
        return (
            this * resources.displayMetrics.density
            ).toInt()
    }

    override fun onDestroyView() {
        binding.rvTanks.adapter =
            null

        _binding =
            null

        super.onDestroyView()
    }
}