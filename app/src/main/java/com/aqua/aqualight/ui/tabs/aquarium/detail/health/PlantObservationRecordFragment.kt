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
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.application.aquarium.health.PlantObservationRules
import com.aqua.aqualight.application.aquarium.health.PlantObservationSnapshot
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentPlantObservationRecordBinding
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.ui.common.header.AquaHeaderCardIconAction
import com.aqua.aqualight.ui.common.header.AquaHeaderCardIconTone
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import kotlinx.coroutines.launch

class PlantObservationRecordFragment : Fragment(R.layout.fragment_plant_observation_record) {
    private val args: PlantObservationRecordFragmentArgs by navArgs()
    private val tanks: AquariumTankViewModel by activityViewModels()
    private val health: PlantHealthViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentPlantObservationRecordBinding? = null
    private val binding get() = _binding!!
    private var current: PlantObservationSnapshot? = null
    private var photos: PlantObservationPhotos? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L && args.plantId > 0L && args.observationId > 0L)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentPlantObservationRecordBinding.bind(view)
        photos = PlantObservationPhotos(binding.photoContainer, onOpen = { index ->
            current?.photoUris?.let { uris ->
                LivestockHealthPhotoViewerDialogFragment.show(childFragmentManager, uris, index)
            }
        })
        renderHeader()
        binding.btnRetry.isVisible = false
        binding.btnRetry.setOnClickListener { health.retry() }
        binding.btnGoAlgaeControl.setOnClickListener { openAlgaeControl() }
        listenForDeletion()
        observePlant()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { health.observations(args.tankId, args.plantId).collect(::renderRecords) }
                launch { health.deletion.collect(::renderDeletion) }
            }
        }
    }

    private fun observePlant() {
        tanks.tanks.observe(viewLifecycleOwner) { list ->
            val plant = list.firstOrNull { it.id == args.tankId }?.plants?.firstOrNull { it.id == args.plantId }
            if (plant == null) {
                leaveRecord()
            } else {
                binding.plantIdentity.tvPlantName.text = plant.plantName
                binding.plantIdentity.tvPlantCategory.text = plant.category
                binding.plantIdentity.imgPlant.bindRecordPhoto(plant.photoUri, R.drawable.ic_health_plant_24)
            }
        }
    }

    private fun renderRecords(state: PlantObservationsState) {
        binding.tvLoadError.isVisible = state == PlantObservationsState.Failed
        binding.btnRetry.isVisible = state == PlantObservationsState.Failed
        if (state is PlantObservationsState.Ready) {
            current = state.records.firstOrNull { it.id == args.observationId }
            val record = current
            if (record == null) leaveRecord() else binding.renderSavedPlantObservation(record, photos)
        } else {
            current = null
            binding.recordContainer.isVisible = false
        }
        renderHeader()
    }

    private fun renderHeader() {
        binding.appHeader.setupAquaHeader(this, AquaHeaderConfig(
            titleOverride = getString(R.string.plant_health_record_title),
            onBackClick = { leaveRecord() },
            cardIconAction = AquaHeaderCardIconAction(
                iconRes = R.drawable.ic_delete,
                contentDescription = getString(R.string.common_delete),
                tone = AquaHeaderCardIconTone.DANGER,
                enabled = current != null && health.deletion.value != PlantDeletionState.Working,
                onClick = { confirmPlantObservationDeletion() }
            )
        ))
    }

    private fun listenForDeletion() {
        childFragmentManager.setFragmentResultListener(
            PLANT_OBSERVATION_DELETE_REQUEST, viewLifecycleOwner
        ) { _, result ->
            if (result.getString(ConfirmDialogFragment.RESULT_KEY) == ConfirmDialogFragment.RESULT_CONFIRM) {
                health.delete(args.tankId, args.plantId, args.observationId)
            }
        }
    }

    private fun renderDeletion(state: PlantDeletionState) {
        renderHeader()
        binding.btnGoAlgaeControl.isEnabled = state != PlantDeletionState.Working
        if (state == PlantDeletionState.Deleted) leaveRecord()
        if (state == PlantDeletionState.Failed) {
            (activity as? BaseActivity)?.showSnackBar(
                getString(R.string.plant_health_delete_failed), BaseActivity.SnackType.ERROR
            )
        }
    }

    private fun openAlgaeControl() {
        if (current?.symptomKeys?.contains(PlantObservationRules.ALGAE) != true) return
        findNavController().navigateSafelyFrom(
            R.id.plantObservationRecordFragment,
            PlantObservationRecordFragmentDirections
                .actionPlantObservationRecordFragmentToPlantHealthAlgaeControlFragment(args.tankId)
        )
    }

    private fun leaveRecord() {
        if (findNavController().currentDestination?.id == R.id.plantObservationRecordFragment) {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        photos = null
        _binding = null
        super.onDestroyView()
    }

}
