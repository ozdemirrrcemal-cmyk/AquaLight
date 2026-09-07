package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.aquarium.catalog.material.MaterialCategoryCatalog
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.databinding.FragmentTankSettingsDetailsBinding
import com.aqua.aqualight.ui.common.material.AquaMaterialCategoryRowFactory
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.materials.MaterialSummaryFormatter

class TankSettingsDetailsFragment : Fragment(R.layout.fragment_tank_settings_details) {

    private var _binding: FragmentTankSettingsDetailsBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankSettingsDetailsBinding.bind(view)

        observeTank()
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { savedTank ->
                savedTank.id == tankId
            } ?: return@observe

            renderMaterials(tank)
        }
    }

    private fun renderMaterials(
        tank: AquariumTankSnapshot
    ) {
        binding.bioMaterialsContainer.removeAllViews()
        binding.hardwareMaterialsContainer.removeAllViews()

        MaterialCategoryCatalog.bioCategories.forEach { category ->
            val categoryTitle = category.title(requireContext())
            val selectedMaterials = tank.materials.filter { material ->
                material.categoryKey == category.key
            }

            binding.bioMaterialsContainer.addView(
                AquaMaterialCategoryRowFactory.create(
                    context = requireContext(),
                    title = categoryTitle,
                    summary = getMaterialSummary(selectedMaterials),
                    onClick = {
                        (parentFragment as? TankSettingsFragment)?.openMaterialPickerFlow(
                            categoryKey = category.key,
                            categoryTitle = categoryTitle
                        )
                    }
                )
            )
        }

        MaterialCategoryCatalog.hardwareCategories.forEach { category ->
            val categoryTitle = category.title(requireContext())
            val selectedMaterials = tank.materials.filter { material ->
                material.categoryKey == category.key
            }

            binding.hardwareMaterialsContainer.addView(
                AquaMaterialCategoryRowFactory.create(
                    context = requireContext(),
                    title = categoryTitle,
                    summary = getMaterialSummary(selectedMaterials),
                    onClick = {
                        (parentFragment as? TankSettingsFragment)?.openMaterialPickerFlow(
                            categoryKey = category.key,
                            categoryTitle = categoryTitle
                        )
                    }
                )
            )
        }
    }

    private fun getMaterialSummary(
        materials: List<AquariumMaterialSelection>
    ): String {
        return MaterialSummaryFormatter.summaryForSavedMaterials(
            context = requireContext(),
            materials = materials
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"

        fun newInstance(
            tankId: Long
        ): TankSettingsDetailsFragment {
            return TankSettingsDetailsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}
