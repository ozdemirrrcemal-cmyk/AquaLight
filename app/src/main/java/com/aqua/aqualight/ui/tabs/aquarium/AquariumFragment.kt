package com.aqua.aqualight.ui.tabs.aquarium

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentAquariumBinding
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import kotlinx.coroutines.launch

class AquariumFragment : Fragment(R.layout.fragment_aquarium) {

    private var _binding: FragmentAquariumBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var isDeleteMode = false
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

        _binding = FragmentAquariumBinding.bind(view)

        setupRecyclerView()
        setupClickListeners()
        observeTanks()
    }

    private fun setupRecyclerView() {
        binding.rvTanks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTanks.adapter = tankAdapter
        binding.rvTanks.setHasFixedSize(false)
    }

    private fun setupClickListeners() {
        binding.btnAdd.setOnClickListener {
            if (isDeleteMode) {
                showDeleteConfirmDialog()
            } else {
                findNavController().navigate(
                    R.id.action_aquariumFragment_to_createTankFragment
                )
            }
        }
    }

    private fun observeTanks() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            tankAdapter.submitList(tanks)

            binding.rvTanks.isVisible = tanks.isNotEmpty()
            binding.tvEmptyState.isVisible = tanks.isEmpty()

            if (tanks.isEmpty()) {
                exitDeleteMode()
                return@observe
            }

            val existingTankIds = tanks.map { tank ->
                tank.id
            }.toSet()

            selectedTankIds.retainAll(existingTankIds)

            if (isDeleteMode && selectedTankIds.isEmpty()) {
                exitDeleteMode()
            } else {
                updateDeleteModeUi()
            }
        }
    }

    private fun handleTankClick(
        tank: SavedAquariumTank
    ) {
        if (isDeleteMode) {
            toggleTankSelection(tank.id)
        } else {
            findNavController().navigate(
                R.id.action_aquariumFragment_to_tankDetailFragment,
                Bundle().apply {
                    putLong("tankId", tank.id)
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

    private fun toggleTankSelection(
        tankId: Long
    ) {
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
        if (isDeleteMode) {
            binding.btnAdd.setImageResource(R.drawable.ic_delete)
            binding.btnAdd.contentDescription = "Delete selected aquariums"
        } else {
            binding.btnAdd.setImageResource(R.drawable.ic_add_24)
            binding.btnAdd.contentDescription = "Add Aquarium"
        }

        tankAdapter.setDeleteMode(
            enabled = isDeleteMode,
            selectedIds = selectedTankIds
        )
    }

    private fun showDeleteConfirmDialog() {
        if (selectedTankIds.isEmpty()) {
            exitDeleteMode()
            return
        }

        val selectedCount = selectedTankIds.size

        AlertDialog.Builder(requireContext())
            .setTitle("Delete aquarium?")
            .setMessage(
                if (selectedCount == 1) {
                    "Selected aquarium will be permanently deleted."
                } else {
                    "$selectedCount selected aquariums will be permanently deleted."
                }
            )
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Delete") { dialog, _ ->
                dialog.dismiss()
                deleteSelectedTanks()
            }
            .show()
    }

    private fun deleteSelectedTanks() {
        val tankIdsToDelete = selectedTankIds.toList()

        if (tankIdsToDelete.isEmpty()) {
            exitDeleteMode()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            aquariumTankViewModel.deleteTanks(
                tankIds = tankIdsToDelete
            )

            exitDeleteMode()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvTanks.adapter = null
        _binding = null
    }
}