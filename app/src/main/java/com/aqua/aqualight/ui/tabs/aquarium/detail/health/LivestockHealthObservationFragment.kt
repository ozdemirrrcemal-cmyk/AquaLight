package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.FragmentLivestockHealthObservationBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthSelectorBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.google.android.material.card.MaterialCardView

class LivestockHealthObservationFragment :
    Fragment(R.layout.fragment_livestock_health_observation) {

    private val args: LivestockHealthObservationFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthObservationBinding? = null
    private val binding get() = _binding!!

    private val selectorBindings = linkedMapOf<Long, ItemLivestockHealthSelectorBinding>()
    private var currentLivestock: List<AquariumLivestock> = emptyList()
    private var selectedLivestockId: Long = 0L
    private var selectedSymptom: String = LivestockHealthUiText.SYMPTOM_SURFACE
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

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.livestock_health_new_observation_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )

        bindSymptomSelection()
        bindAffectedCounter()

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
            itemBinding.tvLivestockName.text = livestock.name.ifBlank {
                getString(R.string.aquarium_unnamed_livestock)
            }
            itemBinding.tvLivestockQuantity.text = getString(
                R.string.livestock_health_selector_quantity_format,
                livestock.quantity.coerceAtLeast(1)
            )
            itemBinding.root.setOnClickListener {
                selectedLivestockId = livestock.id
                affectedCount = affectedCount.coerceAtMost(livestock.quantity.coerceAtLeast(1))
                updateSelectorStyles()
                updateAffectedCount()
            }
            selectorBindings[livestock.id] = itemBinding
            binding.livestockSelectorContainer.addView(itemBinding.root)
        }

        updateSelectorStyles()
    }

    private fun updateSelectorStyles() {
        val selectedStroke = ContextCompat.getColor(requireContext(), R.color.aqua_button_blue)
        val normalStroke = ContextCompat.getColor(requireContext(), R.color.aqua_card_outline)
        val selectedSurface = ContextCompat.getColor(requireContext(), R.color.aqua_surface_action)
        val normalSurface = ContextCompat.getColor(requireContext(), R.color.aqua_card_surface)

        selectorBindings.forEach {
            (livestockId, itemBinding) ->
            val isSelected = livestockId == selectedLivestockId
            itemBinding.root.strokeWidth = resources.getDimensionPixelSize(
                if (isSelected) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
            )
            itemBinding.root.setStrokeColor(if (isSelected) selectedStroke else normalStroke)
            itemBinding.root.setCardBackgroundColor(
                if (isSelected) selectedSurface else normalSurface
            )
            itemBinding.ivSelected.isVisible = isSelected
        }
    }

    private fun bindSymptomSelection() {
        val cards = linkedMapOf(
            LivestockHealthUiText.SYMPTOM_NORMAL to binding.cardSymptomNormal,
            LivestockHealthUiText.SYMPTOM_SURFACE to binding.cardSymptomSurface,
            LivestockHealthUiText.SYMPTOM_APPETITE to binding.cardSymptomAppetite,
            LivestockHealthUiText.SYMPTOM_HIDING to binding.cardSymptomHiding,
            LivestockHealthUiText.SYMPTOM_FINS to binding.cardSymptomFins,
            LivestockHealthUiText.SYMPTOM_OTHER to binding.cardSymptomOther
        )

        cards.forEach {
            (key, card) ->
            card.setOnClickListener {
                selectedSymptom = key
                updateSymptomStyles(cards)
            }
        }
        updateSymptomStyles(cards)
    }

    private fun updateSymptomStyles(cards: Map<String, MaterialCardView>) {
        val selectedStroke = ContextCompat.getColor(requireContext(), R.color.aqua_button_blue)
        val normalStroke = ContextCompat.getColor(requireContext(), R.color.aqua_card_outline)
        val selectedSurface = ContextCompat.getColor(requireContext(), R.color.aqua_surface_action)
        val normalSurface = ContextCompat.getColor(requireContext(), R.color.aqua_card_surface)

        cards.forEach {
            (key, card) ->
            val selected = key == selectedSymptom
            card.strokeWidth = resources.getDimensionPixelSize(
                if (selected) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
            )
            card.setStrokeColor(if (selected) selectedStroke else normalStroke)
            card.setCardBackgroundColor(if (selected) selectedSurface else normalSurface)
        }
    }

    private fun bindAffectedCounter() {
        binding.btnAffectedMinus.setOnClickListener {
            affectedCount = (affectedCount - 1).coerceAtLeast(1)
            updateAffectedCount()
        }
        binding.btnAffectedPlus.setOnClickListener {
            val maximum = selectedLivestock()?.quantity?.coerceAtLeast(1) ?: 1
            affectedCount = (affectedCount + 1).coerceAtMost(maximum)
            updateAffectedCount()
        }
    }

    private fun updateAffectedCount() {
        val maximum = selectedLivestock()?.quantity?.coerceAtLeast(1) ?: 1
        affectedCount = affectedCount.coerceIn(1, maximum)
        binding.tvAffectedCount.text = affectedCount.toString()
        binding.tvAffectedTotal.text = getString(
            R.string.livestock_health_affected_total_format,
            maximum
        )
        binding.btnAffectedMinus.isEnabled = affectedCount > 1
        binding.btnAffectedPlus.isEnabled = affectedCount < maximum
    }

    private fun selectedLivestock(): AquariumLivestock? =
        currentLivestock.firstOrNull { item -> item.id == selectedLivestockId }

    private fun continueToEvaluation() {
        if (isNavigating) {
            return
        }

        val livestock = selectedLivestock() ?: return
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
                    symptomKey = selectedSymptom,
                    affectedCount = affectedCount
                )
        )
        isNavigating = didNavigate
    }

    override fun onDestroyView() {
        selectorBindings.clear()
        _binding = null
        super.onDestroyView()
    }
}
