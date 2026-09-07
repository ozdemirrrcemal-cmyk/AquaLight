package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.aquarium.catalog.material.MaterialCategory
import com.aqua.aqualight.ui.tabs.aquarium.catalog.material.MaterialCategoryCatalog
import com.aqua.aqualight.databinding.FragmentTankMaterialBinding
import com.aqua.aqualight.ui.common.material.AquaMaterialCategoryRowFactory
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.materials.MaterialSummaryFormatter
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class TankMaterialFragment :
    Fragment(R.layout.fragment_tank_material),
    TankStepFragment {

    private var _binding: FragmentTankMaterialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by navGraphViewModels(R.id.nav_create_tank)

    private var isOpeningMaterialPicker: Boolean = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentTankMaterialBinding.bind(view)

        setupMaterialPickerResultListener()
        renderMaterialCategories()
    }

    override fun onResume() {
        super.onResume()
        isOpeningMaterialPicker = false
    }

    private fun setupMaterialPickerResultListener() {
        val savedStateHandle = findNavController()
            .currentBackStackEntry
            ?.savedStateHandle
            ?: return

        savedStateHandle.getLiveData<String?>(
            MaterialPickerFragment.RESULT_CATEGORY_KEY
        ).observe(viewLifecycleOwner) { categoryKey ->
            if (categoryKey == null) {
                return@observe
            }

            savedStateHandle.set<String?>(
                MaterialPickerFragment.RESULT_CATEGORY_KEY,
                null
            )

            renderMaterialCategories()
        }
    }

    private fun renderMaterialCategories() {
        binding.bioContainer.removeAllViews()
        binding.hardwareContainer.removeAllViews()

        MaterialCategoryCatalog.bioCategories.forEach { item ->
            binding.bioContainer.addView(
                createMaterialRow(item)
            )
        }

        MaterialCategoryCatalog.hardwareCategories.forEach { item ->
            binding.hardwareContainer.addView(
                createMaterialRow(item)
            )
        }
    }

    private fun createMaterialRow(
        item: MaterialCategory
    ): View {
        return AquaMaterialCategoryRowFactory.create(
            context = requireContext(),
            title = item.title(requireContext()),
            summary = getSelectedMaterialsText(
                item.key
            ),
            onClick = {
                openMaterialPicker(item)
            }
        )
    }

    private fun getSelectedMaterialsText(
        categoryKey: String
    ): String {
        val materials = viewModel.getMaterialsByCategory(categoryKey)

        return MaterialSummaryFormatter.summaryForSelections(
            context = requireContext(),
            selections = materials
        )
    }

    private fun openMaterialPicker(
        item: MaterialCategory
    ) {
        if (isOpeningMaterialPicker) {
            return
        }

        val didNavigate = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.tankMaterialStepFragment,
            directions = TankMaterialFragmentDirections
                .actionTankMaterialStepFragmentToCreateMaterialPickerFragment(
                    argMode = MaterialPickerFragment.MODE_CREATE,
                    argTankId = 0L,
                    argCategoryKey = item.key,
                    argCategoryTitle = item.title(requireContext())
                )
        )

        isOpeningMaterialPicker = didNavigate
    }

    override fun validateAndSave(): Boolean {
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }
}
