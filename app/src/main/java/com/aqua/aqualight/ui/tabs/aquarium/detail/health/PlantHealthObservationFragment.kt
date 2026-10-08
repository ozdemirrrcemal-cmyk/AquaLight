package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.media.MediaScope
import com.aqua.aqualight.application.aquarium.health.PlantObservationRules
import com.aqua.aqualight.databinding.FragmentPlantHealthObservationBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.TankRecordPhotoFragment
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import kotlinx.coroutines.launch

class PlantHealthObservationFragment : TankRecordPhotoFragment(
    R.layout.fragment_plant_health_observation,
    MediaScope.PLANT,
    R.string.aquarium_plant_photo_title,
    R.string.aquarium_plant_photo_crop_title
) {
    private val args: PlantHealthObservationFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val draft: PlantObservationDraftViewModel by viewModels()
    private var _binding: FragmentPlantHealthObservationBinding? = null
    private val binding get() = _binding!!
    private var symptomGrid: PlantSymptomGrid? = null
    private var photoGrid: PlantObservationPhotos? = null
    private var plantPresent = false
    override val photoTankId: Long get() = args.tankId
    override val hasPhotoView: Boolean get() = _binding != null
    override val photoActionsBlocked: Boolean get() = draft.locked

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L && args.plantId > 0L)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlantHealthObservationBinding.bind(view)
        binding.appHeader.setupAquaHeader(this, AquaHeaderConfig(
            titleOverride = getString(R.string.plant_health_observation_title),
            onBackClick = { findNavController().navigateUp() }
        ))
        setupPhotoSourceResultListener()
        bindForm()
        observePlant()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { draft.revision.collect { renderForm() } }
                launch { draft.save.collect { renderSave(it) } }
                launch { photoTarget.inProgress.collect { renderForm() } }
            }
        }
    }

    private fun bindForm() {
        symptomGrid = PlantSymptomGrid(binding.symptomGridContainer, draft::toggle)
        photoGrid = PlantObservationPhotos(binding.photoContainer, onEdit = { index ->
            if (draft.locked || !plantPresent) return@PlantObservationPhotos
            showRecordPhotoSource(
                recordId = (index + 1).toLong(),
                persistedUri = draft.photos.getOrNull(index)?.takeIf(String::isNotBlank),
                resetSelection = true
            )
        })
        binding.etNote.setText(draft.note)
        binding.etNote.doAfterTextChanged { draft.updateNote(it?.toString().orEmpty()) }
        binding.btnSaveObservation.setOnClickListener {
            if (plantPresent && !photoTarget.isInProgress) draft.submit(args.tankId, args.plantId)
        }
    }

    private fun observePlant() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val plant = tanks.firstOrNull { it.id == args.tankId }
                ?.plants?.firstOrNull { it.id == args.plantId }
            plantPresent = plant != null
            if (plant == null) {
                findNavController().navigateUp()
            } else {
                binding.plantIdentity.tvPlantName.text = plant.plantName
                binding.plantIdentity.tvPlantCategory.text = plant.category
                binding.plantIdentity.imgPlant.bindRecordPhoto(plant.photoUri, R.drawable.ic_health_plant_24)
                renderForm()
            }
        }
    }

    private fun renderForm() {
        symptomGrid?.render(draft.symptoms, !draft.locked)
        photoGrid?.render(draft.photos, !draft.locked)
        binding.etNote.isEnabled = !draft.locked
        val other = PlantObservationRules.OTHER in draft.symptoms
        binding.tvNoteLabel.setText(
            if (other) R.string.plant_health_note_required else R.string.plant_health_note_optional
        )
        binding.tvValidation.isVisible = other && draft.note.isBlank()
        binding.btnSaveObservation.isEnabled = plantPresent && draft.canSave &&
            draft.save.value != PlantSaveState.Working && !photoTarget.isInProgress
    }

    private suspend fun renderSave(state: PlantSaveState) {
        renderForm()
        binding.tvSaveError.isVisible = state == PlantSaveState.Failed
        binding.tvSaveError.setText(
            if (draft.locked) R.string.plant_health_save_failed else R.string.plant_health_save_failed_editable
        )
        binding.btnSaveObservation.setText(when (state) {
            PlantSaveState.Working -> R.string.plant_health_saving
            PlantSaveState.Failed -> R.string.plant_health_retry_save
            else -> R.string.plant_health_save
        })
        if (state is PlantSaveState.Saved) {
            mediaFlow.commitSelection(deletePersistedMedia = false)
            findNavController().navigateSafelyFrom(
                R.id.plantHealthObservationFragment,
                PlantHealthObservationFragmentDirections
                    .actionPlantHealthObservationFragmentToPlantObservationRecordFragment(
                        args.tankId, args.plantId, state.id
                    )
            )
        }
    }

    override suspend fun onPhotoSelected(photoUri: String?) {
        val index = requireNotNull(photoTarget.recordId).toInt() - 1
        try {
            draft.replacePhoto(index, photoUri)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            showPhotoSnackBar(getString(R.string.aquarium_photo_crop_failed))
        } finally {
            photoTarget.finish()
            if (hasPhotoView) renderForm()
        }
    }

    override fun onDestroyView() {
        symptomGrid = null
        photoGrid = null
        _binding = null
        super.onDestroyView()
    }
}
