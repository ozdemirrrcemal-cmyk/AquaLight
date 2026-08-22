package com.aqua.aqualight.ui.tabs.aquarium

import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.DeleteAquariumTanksResult
import com.aqua.aqualight.databinding.FragmentAquariumBinding
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch

class AquariumFragment : Fragment(R.layout.fragment_aquarium) {

    private var _binding: FragmentAquariumBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()

    private var isDeleteMode = false
    private var isDeletingTanks = false
    private var isOpeningAquariumDestination = false
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
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAquariumBinding.bind(view)
        setupHeader()
        setupDeleteResultListener()
        setupRecyclerView()
        setupEmptyStateActions()
        observeTanks()
        observeCareSummary()
    }


    private fun setupDeleteResultListener() {
        childFragmentManager.setFragmentResultListener(
            DELETE_TANKS_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            when (result.getString(FeedbackBottomSheet.RESULT_KEY)) {
                FeedbackBottomSheet.RESULT_PRIMARY -> {
                    val ids = result.getString(FeedbackBottomSheet.RESULT_ACTION_ID)
                        .orEmpty()
                        .split(',')
                        .mapNotNull(String::toLongOrNull)
                        .toSet()
                    deleteSelectedTanks(ids)
                }
                FeedbackBottomSheet.RESULT_CANCEL -> exitDeleteMode()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isOpeningAquariumDestination = false
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_aquarium),
                showBackButton = false,
                primaryAction = AquaHeaderPrimaryAction(
                    text = if (isDeleteMode) {
                        getString(R.string.common_delete)
                    } else {
                        getString(R.string.aquarium_action_add)
                    },
                    contentDescription = if (isDeleteMode) {
                        getString(R.string.aquarium_action_delete_selected_aquariums)
                    } else {
                        getString(R.string.aquarium_action_add_aquarium)
                    },
                    onClick = {
                        if (isDeleteMode) {
                            showDeleteConfirmDialog()
                        } else {
                            openCreateTank()
                        }
                    }
                )
            )
        )
        applyPrimaryActionStyle()
    }

    private fun setupRecyclerView() {
        binding.rvTanks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTanks.adapter = tankAdapter
        binding.rvTanks.setHasFixedSize(false)
    }

    private fun setupEmptyStateActions() {
        binding.btnEmptyAddAquarium.setOnClickListener {
            openCreateTank()
        }
    }

    private fun openCreateTank() {
        navigateFromAquarium(
            AquariumFragmentDirections.actionAquariumFragmentToCreateTankFragment()
        )
    }

    private fun observeTanks() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            maintenanceViewModel.setTanks(tanks)
            tankAdapter.submitList(tanks)
            binding.rvTanks.isVisible = tanks.isNotEmpty()
            binding.tvEmptyState.isVisible = tanks.isEmpty()

            if (tanks.isEmpty()) {
                exitDeleteMode()
                return@observe
            }

            val existingTankIds = tanks.map(AquariumTankSnapshot::id).toSet()
            selectedTankIds.retainAll(existingTankIds)

            if (isDeleteMode && selectedTankIds.isEmpty()) {
                exitDeleteMode()
            } else {
                updateDeleteModeUi()
            }
        }
    }

    private fun observeCareSummary() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                maintenanceViewModel.tankCareSummaryItems.collect { summaries ->
                    tankAdapter.setCareSummaryByTankId(summaries)
                }
            }
        }
    }

    private fun handleTankClick(tank: AquariumTankSnapshot) {
        if (isDeleteMode) {
            toggleTankSelection(tank.id)
        } else {
            navigateFromAquarium(
                AquariumFragmentDirections.actionAquariumFragmentToTankDetailFragment(
                    tankId = tank.id
                )
            )
        }
    }

    private fun handleTankLongClick(tank: AquariumTankSnapshot) {
        if (!isDeleteMode) {
            enterDeleteMode()
        }

        if (!selectedTankIds.contains(tank.id)) {
            selectedTankIds.add(tank.id)
            updateDeleteModeUi()
        }
    }

    private fun enterDeleteMode() {
        isDeleteMode = true
        updateDeleteModeUi()
    }

    private fun exitDeleteMode() {
        isDeleteMode = false
        selectedTankIds.clear()
        updateDeleteModeUi()
    }

    private fun toggleTankSelection(tankId: Long) {
        if (selectedTankIds.contains(tankId)) {
            selectedTankIds.remove(tankId)
        } else {
            selectedTankIds.add(tankId)
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
        val button = binding.appHeader.btnPrimaryAction
        if (isDeleteMode) {
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_aquarium_fragment_button_content))
            button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.aqua_aquarium_fragment_button_icon))
            button.strokeWidth = resources.getDimensionPixelOffset(R.dimen.aqua_size_1)
            button.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.aqua_aquarium_fragment_button_outline))
        } else {
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark))
            button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.aqua_surface_action))
            button.strokeWidth = 0
            button.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.aqua_color_transparent))
        }
    }

    private fun navigateFromAquarium(directions: NavDirections) {
        if (isOpeningAquariumDestination) {
            return
        }

        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.aquariumFragment) {
            return
        }

        isOpeningAquariumDestination = true
        navController.navigate(directions)
    }

    private fun showDeleteConfirmDialog() {
        if (isDeletingTanks) {
            return
        }

        if (selectedTankIds.isEmpty()) {
            exitDeleteMode()
            return
        }

        val tankIdsToDelete = selectedTankIds.toSet()
        val selectedCount = tankIdsToDelete.size
        val title = if (selectedCount == 1) {
            getString(R.string.aquarium_delete_aquarium_title_single)
        } else {
            getString(R.string.aquarium_delete_aquarium_title_multi)
        }
        val message = if (selectedCount == 1) {
            getString(R.string.aquarium_delete_aquarium_message_single)
        } else {
            resources.getQuantityString(
                R.plurals.aquarium_selected_aquariums_delete_message,
                selectedCount,
                selectedCount
            )
        }

        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = title,
            message = message,
            primaryText = getString(R.string.confirm),
            cancelText = getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.WARNING,
            requestKey = DELETE_TANKS_REQUEST_KEY,
            actionId = tankIdsToDelete.sorted().joinToString(",")
        )
    }

    private fun deleteSelectedTanks(tankIdsToDelete: Set<Long>) {
        if (isDeletingTanks) {
            return
        }

        if (tankIdsToDelete.isEmpty()) {
            exitDeleteMode()
            return
        }

        isDeletingTanks = true
        setFragmentGlobalLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (
                    val result = aquariumTankViewModel.deleteTanks(
                        tankIds = tankIdsToDelete.toList()
                    )
                ) {
                    DeleteAquariumTanksResult.NoOp -> Unit
                    DeleteAquariumTanksResult.DeleteFailed -> {
                        DialogManager.showInfoDialog(
                            context = requireContext(),
                            type = DialogType.ERROR,
                            title = getString(R.string.aquarium_delete_failed_title),
                            message = getString(R.string.aquarium_error_selected_tanks_delete_failed)
                        )
                        return@launch
                    }
                    is DeleteAquariumTanksResult.Deleted -> {
                        if (result.hasCleanupIssues) {
                            DialogManager.showInfoDialog(
                                context = requireContext(),
                                type = DialogType.WARNING,
                                title = getString(R.string.aquarium_delete_cleanup_warning_title),
                                message = getString(R.string.aquarium_delete_cleanup_warning_message)
                            )
                        }
                    }
                }
                exitDeleteMode()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                exception.printStackTrace()
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = getString(R.string.aquarium_delete_failed_title),
                    message = getString(R.string.aquarium_error_selected_tanks_delete_failed)
                )
            } finally {
                isDeletingTanks = false
                setFragmentGlobalLoading(false)
            }
        }
    }
    override fun onDestroyView() {
        binding.rvTanks.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DELETE_TANKS_REQUEST_KEY = "aquarium_delete_tanks_result"
    }
}
