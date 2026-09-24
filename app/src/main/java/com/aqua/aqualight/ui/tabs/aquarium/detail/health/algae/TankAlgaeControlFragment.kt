package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTypeId
import com.aqua.aqualight.databinding.FragmentTankAlgaeControlBinding
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthFragmentDirections
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class TankAlgaeControlFragment : Fragment(R.layout.fragment_tank_algae_control) {

    private var _binding: FragmentTankAlgaeControlBinding? = null
    private val binding get() = _binding!!

    private var tankId: Long = 0L
    private var formVisible: Boolean = false
    private var restoredState = AlgaeObservationSavedState()

    private var catalogController: AlgaeCatalogController? = null
    private var choicesController: AlgaeObservationChoicesController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tankId = requireArguments().getLong(ARG_TANK_ID)
        require(tankId > 0L) {
            "TankAlgaeControlFragment requires a positive tankId."
        }

        restoredState = readAlgaeObservationSavedState(savedInstanceState)
        formVisible = restoredState.formVisible
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankAlgaeControlBinding.bind(view)

        catalogController = AlgaeCatalogController(
            context = requireContext(),
            binding = binding,
            onCommonSelected = { type ->
                resetObservationDraft()
                showObservationForm(type)
            },
            onTypeSelected = {
                updateAnalyzeEnabled()
            }
        ).also { controller ->
            controller.bind()
            controller.select(restoredState.selectedType)
        }

        choicesController = AlgaeObservationChoicesController(
            context = requireContext(),
            binding = binding,
            onChanged = ::updateAnalyzeEnabled
        ).also { controller ->
            controller.bind()
            controller.restore(
                locations = restoredState.locations,
                density = restoredState.density,
                trend = restoredState.trend
            )
        }

        binding.etObservationNote.setText(restoredState.note)
        binding.btnNewObservation.setOnClickListener {
            openNewObservation()
        }
        binding.btnBackToOverview.setOnClickListener {
            openOverview()
        }
        binding.btnAnalyze.setOnClickListener {
            openAnalysis()
        }

        binding.algaeOverviewScroll.isVisible = !formVisible
        binding.algaeObservationScroll.isVisible = formVisible
        updateAnalyzeEnabled()
    }

    fun openNewObservation() {
        resetObservationDraft()
        showObservationForm(preselectedType = null)
    }

    fun openOverview() {
        formVisible = false
        if (_binding != null) {
            binding.algaeOverviewScroll.isVisible = true
            binding.algaeObservationScroll.isVisible = false
            binding.algaeOverviewScroll.scrollTo(0, 0)
        }
    }

    private fun showObservationForm(preselectedType: AlgaeTypeId?) {
        formVisible = true
        if (preselectedType != null) {
            catalogController?.select(preselectedType)
        }

        binding.algaeOverviewScroll.isVisible = false
        binding.algaeObservationScroll.isVisible = true
        binding.algaeObservationScroll.scrollTo(0, 0)
        updateAnalyzeEnabled()
    }

    private fun resetObservationDraft() {
        catalogController?.reset()
        choicesController?.reset()

        if (_binding != null) {
            binding.etObservationNote.setText("")
        }
        updateAnalyzeEnabled()
    }

    private fun updateAnalyzeEnabled() {
        if (_binding == null) {
            return
        }

        binding.btnAnalyze.isEnabled =
            catalogController?.selectedType != null &&
                choicesController?.complete == true
    }

    private fun openAnalysis() {
        val algaeType = catalogController?.selectedType
        val choices = choicesController
        val density = choices?.density
        val trend = choices?.trend
        val locations = choices?.locations.orEmpty()

        val hasCoreSelection = algaeType != null && density != null
        val hasCompleteSelection = hasCoreSelection && trend != null

        if (hasCompleteSelection && locations.isNotEmpty()) {
            findNavController().navigateSafelyFrom(
                sourceDestinationId = R.id.tankHealthFragment,
                directions = TankHealthFragmentDirections
                    .actionTankHealthFragmentToTankAlgaeAnalysisFragment(
                        tankId = tankId,
                        algaeType = algaeType.name,
                        locations = locations.joinToString(separator = ",") { location ->
                            location.name
                        },
                        density = density.name,
                        trend = trend.name,
                        note = binding.etObservationNote.text?.toString().orEmpty().trim()
                    )
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val catalog = catalogController
        val choices = choicesController

        val state = if (catalog != null && choices != null && _binding != null) {
            AlgaeObservationSavedState(
                formVisible = formVisible,
                selectedType = catalog.selectedType,
                locations = choices.locations,
                density = choices.density,
                trend = choices.trend,
                note = binding.etObservationNote.text?.toString().orEmpty()
            )
        } else {
            restoredState.copy(formVisible = formVisible)
        }

        writeAlgaeObservationSavedState(outState, state)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        catalogController = null
        choicesController = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"

        fun newInstance(tankId: Long): TankAlgaeControlFragment =
            TankAlgaeControlFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
    }
}
