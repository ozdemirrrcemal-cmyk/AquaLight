package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.FragmentLivestockHealthObservationBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthSelectorBinding
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.TankRecordPhotoFragment
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LivestockHealthObservationFragment : TankRecordPhotoFragment(
    R.layout.fragment_livestock_health_observation,
    AppMediaScope.LIVESTOCK,
    R.string.aquarium_livestock_photo_title,
    R.string.aquarium_livestock_photo_crop_title
) {

    private val args: LivestockHealthObservationFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthObservationBinding? = null
    private val binding get() = _binding!!

    override val photoTankId: Long get() = args.tankId
    override val hasPhotoView: Boolean get() = _binding != null

    private val selectorBindings = linkedMapOf<Long, ItemLivestockHealthSelectorBinding>()
    private var currentLivestock: List<AquariumLivestock> = emptyList()
    private var selectedLivestockId: Long = 0L
    private val selectedSymptoms = linkedSetOf<String>()
    private var selectedOnset: String = LivestockHealthObservationCatalog.ONSET_TODAY
    private var affectedCount: Int = 1
    private val observationPhotoUris = MutableList<String?>(MAX_OBSERVATION_PHOTOS) { null }
    private var activePhotoSlotIndex: Int = 0
    private var isNavigating: Boolean = false
    private var draftStateHandle: SavedStateHandle? = null
    private var requestId: String = UUID.randomUUID().toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "LivestockHealthObservationFragment requires a positive tankId."
        }

        savedInstanceState
            ?.getStringArrayList(STATE_OBSERVATION_PHOTOS)
            ?.take(MAX_OBSERVATION_PHOTOS)
            ?.forEachIndexed { index, uri ->
                observationPhotoUris[index] = uri.takeIf(String::isNotBlank)
            }
        activePhotoSlotIndex = savedInstanceState
            ?.getInt(STATE_ACTIVE_PHOTO_SLOT, 0)
            ?.coerceIn(0, MAX_OBSERVATION_PHOTOS - 1)
            ?: 0
        selectedLivestockId = savedInstanceState?.getLong(STATE_LIVESTOCK_ID) ?: 0L
        requestId = savedInstanceState?.getString(STATE_REQUEST_ID) ?: requestId
        selectedSymptoms.addAll(savedInstanceState?.getStringArrayList(STATE_SYMPTOMS).orEmpty())
        selectedOnset = savedInstanceState?.getString(STATE_ONSET)
            ?: LivestockHealthObservationCatalog.ONSET_TODAY
        affectedCount = savedInstanceState?.getInt(STATE_AFFECTED_COUNT) ?: 1
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthObservationBinding.bind(view)
        draftStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        binding.etOtherObservation.setText(savedInstanceState?.getString(STATE_OTHER))
        binding.etObservationNote.setText(savedInstanceState?.getString(STATE_NOTE))

        mediaFlow.initializeSelection(observationPhotoUris[activePhotoSlotIndex])
        setupPhotoSourceResultListener()
        binding.renderObservationPhotoSlots(observationPhotoUris)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.livestock_health_new_observation_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )

        bindSymptomSelection()
        binding.bindAffectedCounter(
            fragment = this,
            currentCount = { affectedCount },
            maximum = {
                currentLivestock
                    .selectedLivestock(selectedLivestockId)
                    ?.quantity
                    ?.coerceAtLeast(1)
                    ?: 1
            },
            onCountChanged = { affectedCount = it }
        )
        bindLivestockObservationMeta(
            fragment = this,
            binding = binding,
            selectedOnset = { selectedOnset },
            onOnsetSelected = { selectedOnset = it }
        )
        binding.etOtherObservation.doAfterTextChanged { text ->
            binding.btnEvaluate.isEnabled =
                currentLivestock.isNotEmpty() &&
                    selectedSymptoms.isNotEmpty() &&
                    (
                        LivestockHealthUiText.SYMPTOM_OTHER !in selectedSymptoms ||
                            !text.isNullOrBlank()
                    )
        }

        binding.photoAddArea.setOnClickListener {
            openObservationPhotoSlot(
                observationPhotoUris.indexOfFirst { uri -> uri.isNullOrBlank() }
                    .takeIf { index -> index >= 0 }
                    ?: 0
            )
        }
        binding.photoSlotOne.setOnClickListener { openObservationPhotoSlot(0) }
        binding.photoSlotTwo.setOnClickListener { openObservationPhotoSlot(1) }
        binding.photoSlotThree.setOnClickListener { openObservationPhotoSlot(2) }
        binding.btnEvaluate.setOnClickListener { continueToEvaluation() }
        observeTank()
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) {
            tanks ->
            currentLivestock = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.livestock
                .orEmpty()

            val previousLivestockId = selectedLivestockId
            if (
                selectedLivestockId <= 0L ||
                currentLivestock.none { item -> item.id == selectedLivestockId }
            ) {
                selectedLivestockId = currentLivestock.firstOrNull()?.id ?: 0L
            }
            if (selectedLivestockId != previousLivestockId) {
                selectedSymptoms.clear()
                if (previousLivestockId > 0L) discardObservationPhotos()
            }

            binding.renderObservationLivestockSelectors(
                fragment = this,
                livestock = currentLivestock,
                selectorBindings = selectorBindings,
                selectedLivestockId = selectedLivestockId
            ) { selected ->
                if (selectedLivestockId != selected.id) {
                    selectedSymptoms.clear()
                    discardObservationPhotos()
                }
                selectedLivestockId = selected.id
                affectedCount = affectedCount.coerceAtMost(
                    selected.quantity.coerceAtLeast(1)
                )
                bindSymptomSelection()
                affectedCount = binding.renderAffectedCounter(
                    fragment = this,
                    count = affectedCount,
                    maximum = selected.quantity.coerceAtLeast(1)
                )
            }
            bindSymptomSelection()
            affectedCount = binding.renderAffectedCounter(
                fragment = this,
                count = affectedCount,
                maximum = currentLivestock
                    .selectedLivestock(selectedLivestockId)
                    ?.quantity
                    ?.coerceAtLeast(1)
                    ?: 1
            )
        }
    }


    private fun bindSymptomSelection() {
        val selectedLivestock = currentLivestock.selectedLivestock(selectedLivestockId)
        val category = selectedLivestock?.category
        val options = LivestockHealthObservationCatalog.symptomsFor(category)
        val validKeys = options.mapTo(linkedSetOf()) { option -> option.key }
        if (selectedLivestock != null) selectedSymptoms.retainAll(validKeys)
        binding.bindLivestockSymptomOptions(
            options = options,
            selectedSymptoms = selectedSymptoms
        ) { key ->
            if (!selectedSymptoms.add(key)) {
                selectedSymptoms.remove(key)
            }
            bindSymptomSelection()
        }

        val otherSelected = LivestockHealthUiText.SYMPTOM_OTHER in selectedSymptoms
        binding.otherObservationContainer.isVisible = otherSelected
        binding.btnEvaluate.isEnabled =
            currentLivestock.isNotEmpty() &&
                selectedSymptoms.isNotEmpty() &&
                (
                    !otherSelected ||
                        !binding.etOtherObservation.text.isNullOrBlank()
                    )
    }


    private fun continueToEvaluation() {
        if (!isNavigating && !photoTarget.isInProgress &&
            binding.btnEvaluate.isEnabled && selectedSymptoms.isNotEmpty()
        ) {
            currentLivestock
                .selectedLivestock(selectedLivestockId)
                ?.let { livestock ->
                    val symptomKey = selectedSymptoms.first()
                    draftStateHandle?.apply {
                        set(DRAFT_TANK_ID, args.tankId)
                        set(DRAFT_REQUEST_ID, requestId)
                        set(DRAFT_LIVESTOCK_ID, livestock.id)
                        set(DRAFT_SYMPTOMS, ArrayList(selectedSymptoms))
                        set(DRAFT_ONSET, selectedOnset)
                        set(DRAFT_OTHER, binding.etOtherObservation.text?.toString().orEmpty())
                        set(DRAFT_NOTE, binding.etObservationNote.text?.toString().orEmpty())
                        set(DRAFT_PHOTOS, ArrayList(observationPhotoUris.filterNotNull()))
                        set(DRAFT_AFFECTED, affectedCount)
                    }
                    val didNavigate = findNavController().navigateSafelyFrom(
                        sourceDestinationId = R.id.livestockHealthObservationFragment,
                        directions = LivestockHealthObservationFragmentDirections
                            .actionLivestockHealthObservationFragmentToLivestockHealthEvaluationFragment(
                                tankId = args.tankId,
                                livestockId = livestock.id,
                                symptomKey = symptomKey,
                                affectedCount = affectedCount
                            )
                    )
                    isNavigating = didNavigate
                }
        }
    }

    private fun openObservationPhotoSlot(slotIndex: Int) {
        val recordId = selectedLivestockId.takeIf { id -> id > 0L } ?: return
        activePhotoSlotIndex = slotIndex.coerceIn(0, MAX_OBSERVATION_PHOTOS - 1)
        val ownerUid = requireContext()
            .requireAppContainer()
            .authenticatedOwnerIdentity
            .requireOwnerUid()
        showRecordPhotoSource(
            recordId = recordId,
            ownerUid = ownerUid,
            persistedUri = observationPhotoUris[activePhotoSlotIndex],
            resetSelection = true
        )
    }

    private fun discardObservationPhotos() {
        val pending = observationPhotoUris.filterNotNull()
        observationPhotoUris.indices.forEach { observationPhotoUris[it] = null }
        _binding?.renderObservationPhotoSlots(observationPhotoUris)
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            pending.forEach { AppMediaStorage.rollbackPendingMedia(context, it) }
        }
    }


    override suspend fun onPhotoSelected(photoUri: String?) {
        if (!hasPhotoView) return
        val previous = observationPhotoUris[activePhotoSlotIndex]
        if (previous != photoUri && !previous.isNullOrBlank()) {
            mediaFlow.deleteInternalMedia(previous)
        }
        observationPhotoUris[activePhotoSlotIndex] = photoUri
        binding.renderObservationPhotoSlots(observationPhotoUris)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_LIVESTOCK_ID, selectedLivestockId)
        outState.putString(STATE_REQUEST_ID, requestId)
        outState.putStringArrayList(STATE_SYMPTOMS, ArrayList(selectedSymptoms))
        outState.putString(STATE_ONSET, selectedOnset)
        outState.putInt(STATE_AFFECTED_COUNT, affectedCount)
        outState.putString(STATE_OTHER, _binding?.etOtherObservation?.text?.toString())
        outState.putString(STATE_NOTE, _binding?.etObservationNote?.text?.toString())
        outState.putStringArrayList(
            STATE_OBSERVATION_PHOTOS,
            ArrayList(observationPhotoUris.map { uri -> uri.orEmpty() })
        )
        outState.putInt(STATE_ACTIVE_PHOTO_SLOT, activePhotoSlotIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        selectorBindings.clear()
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        if (draftStateHandle?.get<Boolean>(DRAFT_COMMITTED) != true &&
            draftStateHandle?.get<Boolean>(DRAFT_SAVING) != true &&
            activity?.isChangingConfigurations != true
        ) {
            val pending = observationPhotoUris.filterNotNull()
            val context = context?.applicationContext
            if (context != null && pending.isNotEmpty()) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    pending.forEach { AppMediaStorage.rollbackPendingMedia(context, it) }
                }
            }
        }
        super.onDestroy()
    }

    companion object {
        const val DRAFT_TANK_ID = "health_draft_tank"
        const val DRAFT_REQUEST_ID = "health_draft_request_id"
        const val DRAFT_LIVESTOCK_ID = "health_draft_livestock"
        const val DRAFT_SYMPTOMS = "health_draft_symptoms"
        const val DRAFT_ONSET = "health_draft_onset"
        const val DRAFT_OTHER = "health_draft_other"
        const val DRAFT_NOTE = "health_draft_note"
        const val DRAFT_PHOTOS = "health_draft_photos"
        const val DRAFT_AFFECTED = "health_draft_affected"
        const val DRAFT_COMMITTED = "health_draft_committed"
        const val DRAFT_SAVING = "health_draft_saving"
        const val MAX_OBSERVATION_PHOTOS = 3
        const val STATE_OBSERVATION_PHOTOS = "livestock_health_observation_photos"
        const val STATE_REQUEST_ID = "livestock_health_request_id"
        const val STATE_ACTIVE_PHOTO_SLOT = "livestock_health_active_photo_slot"
    }
}
