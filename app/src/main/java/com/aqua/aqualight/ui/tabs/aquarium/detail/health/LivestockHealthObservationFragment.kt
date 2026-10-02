package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.FragmentLivestockHealthObservationBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthSelectorBinding
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.TankRecordPhotoFragment
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

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
    private val selectedSymptoms = linkedSetOf(LivestockHealthUiText.SYMPTOM_SURFACE)
    private var affectedCount: Int = 1
    private var isNavigating: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "LivestockHealthObservationFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthObservationBinding.bind(view)

        mediaFlow.initializeSelection(null)
        setupPhotoSourceResultListener()
        val currentPhoto = mediaFlow.selection.value.selectedUri
        binding.ivObservationPhotoPreview.isVisible = !currentPhoto.isNullOrBlank()
        binding.tvObservationPhotoSlotPlus.isVisible = currentPhoto.isNullOrBlank()
        currentPhoto?.let(binding.ivObservationPhotoPreview::bindRecordPhoto)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.livestock_health_new_observation_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )

        bindSymptomSelection()
        bindAffectedCounter()

        binding.photoAddArea.setOnClickListener {
            val recordId = selectedLivestockId.takeIf { id -> id > 0L }
                ?: return@setOnClickListener
            val ownerUid = requireContext()
                .requireAppContainer()
                .authenticatedOwnerIdentity
                .requireOwnerUid()
            showRecordPhotoSource(recordId = recordId, ownerUid = ownerUid)
        }
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

            if (
                selectedLivestockId <= 0L ||
                currentLivestock.none { item -> item.id == selectedLivestockId }
            ) {
                selectedLivestockId = currentLivestock.firstOrNull()?.id ?: 0L
            }

            renderLivestockSelectors()
            updateAffectedCount()
        }
    }

    private fun renderLivestockSelectors() {
        binding.livestockSelectorContainer.removeAllViews()
        selectorBindings.clear()

        binding.tvNoLivestock.isVisible = currentLivestock.isEmpty()
        binding.livestockSelectorScroll.isVisible = currentLivestock.isNotEmpty()
        binding.btnEvaluate.isEnabled = currentLivestock.isNotEmpty()

        currentLivestock.forEach {
            livestock ->
            val itemBinding = ItemLivestockHealthSelectorBinding.inflate(
                LayoutInflater.from(requireContext()),
                binding.livestockSelectorContainer,
                false
            )
            itemBinding.ivLivestockPhoto.bindRecordPhoto(livestock.photoUri)
            val displayName = livestock.name.ifBlank {
                getString(R.string.aquarium_unnamed_livestock)
            }
            val nameParts = displayName.split(" / ", limit = 2)
            itemBinding.tvLivestockName.text = nameParts.first()
            itemBinding.tvLivestockSubtitle.isVisible = nameParts.size > 1
            itemBinding.tvLivestockSubtitle.text = nameParts.getOrNull(1).orEmpty()
            itemBinding.tvLivestockQuantity.text = getString(
                R.string.livestock_health_selector_quantity_format,
                livestock.quantity.coerceAtLeast(1)
            )
            itemBinding.root.setOnClickListener {
                selectedLivestockId = livestock.id
                affectedCount = affectedCount.coerceAtMost(livestock.quantity.coerceAtLeast(1))
                applyLivestockSelectorSelection(selectorBindings, selectedLivestockId)
                updateAffectedCount()
            }
            selectorBindings[livestock.id] = itemBinding
            binding.livestockSelectorContainer.addView(itemBinding.root)
        }

        applyLivestockSelectorSelection(selectorBindings, selectedLivestockId)
    }

    private fun bindSymptomSelection() {
        val cards = linkedMapOf(
            LivestockHealthUiText.SYMPTOM_SURFACE to binding.cardSymptomSurface,
            LivestockHealthUiText.SYMPTOM_APPETITE to binding.cardSymptomAppetite,
            LivestockHealthUiText.SYMPTOM_SWIMMING to binding.cardSymptomSwimming,
            LivestockHealthUiText.SYMPTOM_SPOT to binding.cardSymptomSpot,
            LivestockHealthUiText.SYMPTOM_FINS to binding.cardSymptomFins,
            LivestockHealthUiText.SYMPTOM_OTHER to binding.cardSymptomOther
        )

        cards.forEach { (key, card) ->
            card.setOnClickListener {
                if (!selectedSymptoms.add(key)) {
                    selectedSymptoms.remove(key)
                }
                applyLivestockSymptomSelection(cards, selectedSymptoms)
                binding.btnEvaluate.isEnabled =
                    currentLivestock.isNotEmpty() && selectedSymptoms.isNotEmpty()
            }
        }
        applyLivestockSymptomSelection(cards, selectedSymptoms)
    }

    private fun bindAffectedCounter() {
        binding.btnAffectedMinus.setOnClickListener {
            affectedCount = (affectedCount - 1).coerceAtLeast(1)
            updateAffectedCount()
        }
        binding.btnAffectedPlus.setOnClickListener {
            val maximum = currentLivestock.selectedLivestock(selectedLivestockId)?.quantity?.coerceAtLeast(1) ?: 1
            affectedCount = (affectedCount + 1).coerceAtMost(maximum)
            updateAffectedCount()
        }
    }

    private fun updateAffectedCount() {
        val maximum = currentLivestock.selectedLivestock(selectedLivestockId)?.quantity?.coerceAtLeast(1) ?: 1
        affectedCount = affectedCount.coerceIn(1, maximum)
        binding.tvAffectedRegisteredCount.text = getString(
            R.string.livestock_health_registered_count_format,
            maximum
        )
        binding.tvAffectedCount.text = getString(
            R.string.livestock_health_affected_counter_format,
            affectedCount,
            maximum
        )
        binding.btnAffectedMinus.isEnabled = affectedCount > 1
        binding.btnAffectedPlus.isEnabled = affectedCount < maximum
    }

    private fun continueToEvaluation() {
        if (isNavigating) {
            return
        }

        val livestock = currentLivestock.selectedLivestock(selectedLivestockId) ?: return
        val navController = findNavController()

        runCatching {
            navController.getBackStackEntry(R.id.livestockHealthFragment)
                .savedStateHandle
                .apply {
                    set(LivestockHealthFragment.KEY_HAS_OBSERVATION, true)
                    set(LivestockHealthFragment.KEY_LIVESTOCK_ID, livestock.id)
                }
        }

        val didNavigate = navController.navigateSafelyFrom(
            sourceDestinationId = R.id.livestockHealthObservationFragment,
            directions = LivestockHealthObservationFragmentDirections
                .actionLivestockHealthObservationFragmentToLivestockHealthEvaluationFragment(
                    tankId = args.tankId,
                    livestockId = livestock.id,
                    symptomKey = selectedSymptoms.firstOrNull()
                        ?: LivestockHealthUiText.SYMPTOM_SURFACE,
                    affectedCount = affectedCount
                )
        )
        isNavigating = didNavigate
    }

    override suspend fun onPhotoSelected(photoUri: String?) {
        if (!hasPhotoView) return
        binding.ivObservationPhotoPreview.isVisible = !photoUri.isNullOrBlank()
        binding.tvObservationPhotoSlotPlus.isVisible = photoUri.isNullOrBlank()
        photoUri?.let(binding.ivObservationPhotoPreview::bindRecordPhoto)
    }

    override fun onDestroyView() {
        selectorBindings.clear()
        _binding = null
        super.onDestroyView()
    }
}
