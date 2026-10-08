package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentPlantHealthHistoryBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import kotlinx.coroutines.launch

class PlantHealthHistoryFragment : Fragment(R.layout.fragment_plant_health_history) {
    private val args: PlantHealthHistoryFragmentArgs by navArgs()
    private val tanks: AquariumTankViewModel by activityViewModels()
    private val health: PlantHealthViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentPlantHealthHistoryBinding? = null
    private val binding get() = _binding!!
    private val records = PlantObservationAdapter(::openRecord)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L && args.plantId > 0L)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentPlantHealthHistoryBinding.bind(view)
        binding.appHeader.setupAquaHeader(this, AquaHeaderConfig(
            titleOverride = getString(R.string.plant_health_history_title),
            onBackClick = { findNavController().navigateUp() }
        ))
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.adapter = records
        binding.btnRetry.isVisible = false
        binding.btnRetry.setOnClickListener { health.retry() }
        observePlant()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                health.observations(args.tankId, args.plantId).collect(::render)
            }
        }
    }

    private fun observePlant() {
        tanks.tanks.observe(viewLifecycleOwner) { list ->
            val plant = list.firstOrNull { it.id == args.tankId }?.plants?.firstOrNull { it.id == args.plantId }
            if (plant == null) {
                findNavController().navigateUp()
            } else {
                binding.plantIdentity.tvPlantName.text = plant.plantName
                binding.plantIdentity.tvPlantCategory.text = plant.category
                binding.plantIdentity.imgPlant.bindRecordPhoto(plant.photoUri, R.drawable.ic_health_plant_24)
            }
        }
    }

    private fun render(state: PlantObservationsState) {
        val ready = state as? PlantObservationsState.Ready
        binding.tvLoadError.isVisible = state == PlantObservationsState.Failed
        binding.btnRetry.isVisible = state == PlantObservationsState.Failed
        binding.tvEmpty.isVisible = ready?.records?.isEmpty() == true
        binding.historyList.isVisible = ready != null
        if (ready != null) records.submitList(ready.records)
    }

    private fun openRecord(id: Long) {
        findNavController().navigateSafelyFrom(
            R.id.plantHealthHistoryFragment,
            PlantHealthHistoryFragmentDirections.actionPlantHealthHistoryFragmentToPlantObservationRecordFragment(
                args.tankId, args.plantId, id
            )
        )
    }

    override fun onDestroyView() {
        binding.historyList.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
