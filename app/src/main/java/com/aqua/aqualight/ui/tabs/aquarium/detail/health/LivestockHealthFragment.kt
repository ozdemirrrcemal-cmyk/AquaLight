package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.databinding.FragmentLivestockHealthBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class LivestockHealthFragment : Fragment(R.layout.fragment_livestock_health) {

    private val args: LivestockHealthFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthBinding? = null
    private val binding get() = _binding!!

    private var currentTank: AquariumTankSnapshot? = null
    private var hasSessionObservation: Boolean = false
    private var sessionLivestockId: Long = 0L
    private var isNavigating: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "LivestockHealthFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthBinding.bind(view)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_livestock_health),
                onBackClick = { findNavController().navigateUp() },
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_info,
                        contentDescription = getString(R.string.livestock_health_info_description),
                        onClick = {}
                    )
                )
            )
        )

        binding.btnNewObservation.setOnClickListener { openNewObservation() }
        binding.cardActiveFollowup.setOnClickListener { openCurrentFollowup() }
        binding.cardRecentObservation.setOnClickListener { openCurrentFollowup() }

        observeSessionUiState()
        observeTank()
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
    }

    private fun observeSessionUiState() {
        val handle = findNavController().currentBackStackEntry?.savedStateHandle ?: return

        handle.getLiveData(KEY_HAS_OBSERVATION, false).observe(viewLifecycleOwner) {
            hasObservation ->
            hasSessionObservation = hasObservation
            render()
        }
        handle.getLiveData(KEY_LIVESTOCK_ID, 0L).observe(viewLifecycleOwner) {
            livestockId ->
            sessionLivestockId = livestockId
            render()
        }
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) {
            tanks ->
            currentTank = tanks.firstOrNull { tank -> tank.id == args.tankId }
            render()
        }
    }

    private fun render() {
        val tank = currentTank
        val livestock = tank?.livestock.orEmpty()
        val selected = livestock.firstOrNull { item -> item.id == sessionLivestockId }
            ?: livestock.firstOrNull()
        val canShowFilledState = hasSessionObservation && selected != null

        binding.tvHeroTitle.setText(
            if (canShowFilledState) {
                R.string.livestock_health_hero_active_title
            } else {
                R.string.livestock_health_hero_empty_title
            }
        )
        binding.tvHeroSubtitle.setText(
            if (canShowFilledState) {
                R.string.livestock_health_hero_active_subtitle
            } else {
                R.string.livestock_health_hero_empty_subtitle
            }
        )

        binding.cardEmptyFollowups.isVisible = !canShowFilledState
        binding.filledHealthContent.isVisible = canShowFilledState

        if (livestock.isEmpty()) {
            binding.tvEmptyFollowupsTitle.setText(R.string.livestock_health_no_livestock_title)
            binding.tvEmptyFollowupsBody.setText(R.string.livestock_health_no_livestock_body)
            binding.btnNewObservation.isEnabled = false
        } else {
            binding.tvEmptyFollowupsTitle.setText(R.string.livestock_health_empty_followups_title)
            binding.tvEmptyFollowupsBody.setText(R.string.livestock_health_empty_followups_body)
            binding.btnNewObservation.isEnabled = true
        }

        if (selected != null) {
            renderActiveLivestock(selected)
        }
    }

    private fun renderActiveLivestock(livestock: AquariumLivestock) {
        val displayName = livestock.name.ifBlank {
            getString(R.string.aquarium_unnamed_livestock)
        }
        val quantity = livestock.quantity.coerceAtLeast(1)

        binding.ivActiveLivestock.bindRecordPhoto(livestock.photoUri)
        binding.tvActiveLivestockName.text = displayName
        binding.tvActiveLivestockAffected.text = getString(
            R.string.livestock_health_affected_format,
            1,
            quantity
        )

        binding.ivRecentLivestock.bindRecordPhoto(livestock.photoUri)
        binding.tvRecentLivestockName.text = displayName
        binding.tvRecentLivestockAffected.text = getString(
            R.string.livestock_health_affected_format,
            1,
            quantity
        )
    }

    private fun openNewObservation() {
        if (isNavigating || currentTank?.livestock.isNullOrEmpty()) {
            return
        }

        val didNavigate = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.livestockHealthFragment,
            directions = LivestockHealthFragmentDirections
                .actionLivestockHealthFragmentToLivestockHealthObservationFragment(args.tankId)
        )
        isNavigating = didNavigate
    }

    private fun openCurrentFollowup() {
        if (isNavigating) {
            return
        }

        val livestockId = currentTank
            ?.livestock
            ?.firstOrNull { item -> item.id == sessionLivestockId }
            ?.id
            ?: currentTank?.livestock?.firstOrNull()?.id
            ?: return

        val didNavigate = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.livestockHealthFragment,
            directions = LivestockHealthFragmentDirections
                .actionLivestockHealthFragmentToLivestockHealthFollowUpFragment(
                    tankId = args.tankId,
                    livestockId = livestockId
                )
        )
        isNavigating = didNavigate
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val KEY_HAS_OBSERVATION = "livestock_health_ui_has_observation"
        const val KEY_LIVESTOCK_ID = "livestock_health_ui_livestock_id"
    }
}
