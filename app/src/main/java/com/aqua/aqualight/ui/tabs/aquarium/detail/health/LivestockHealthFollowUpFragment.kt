package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.FragmentLivestockHealthFollowUpBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPillTextAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.google.android.material.card.MaterialCardView

class LivestockHealthFollowUpFragment :
    Fragment(R.layout.fragment_livestock_health_follow_up) {

    private val args: LivestockHealthFollowUpFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthFollowUpBinding? = null
    private val binding get() = _binding!!

    private var currentLivestock: AquariumLivestock? = null
    private var currentStatus: String = LivestockHealthCheckBottomSheet.STATUS_SAME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "LivestockHealthFollowUpFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthFollowUpBinding.bind(view)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.livestock_health_followup_title),
                onBackClick = { findNavController().navigateUp() },
                pillTextAction = AquaHeaderPillTextAction(
                    text = getString(R.string.livestock_health_end_followup),
                    backgroundRes = R.drawable.bg_aqua_toolbar_pill_action_primary,
                    onClick = { endSessionFollowup() }
                )
            )
        )

        parentFragmentManager.setFragmentResultListener(
            LivestockHealthCheckBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) {
            _, bundle ->
            currentStatus = bundle.getString(
                LivestockHealthCheckBottomSheet.RESULT_STATUS,
                LivestockHealthCheckBottomSheet.STATUS_SAME
            )
            renderStatus()
        }

        binding.btnNewCheck.setOnClickListener {
            val livestock = currentLivestock ?: return@setOnClickListener
            LivestockHealthCheckBottomSheet.show(
                fragmentManager = parentFragmentManager,
                livestockName = livestock.name.ifBlank {
                    getString(R.string.aquarium_unnamed_livestock)
                },
                totalCount = livestock.quantity.coerceAtLeast(1),
                photoUri = livestock.photoUri
            )
        }

        observeLivestock()
        renderStatus()
    }

    private fun observeLivestock() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) {
            tanks ->
            currentLivestock = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.livestock
                ?.firstOrNull { item -> item.id == args.livestockId }
                ?: tanks
                    .firstOrNull { tank -> tank.id == args.tankId }
                    ?.livestock
                    ?.firstOrNull()

            currentLivestock?.let(::renderLivestock)
        }
    }

    private fun renderLivestock(livestock: AquariumLivestock) {
        val name = livestock.name.ifBlank {
            getString(R.string.aquarium_unnamed_livestock)
        }
        val quantity = livestock.quantity.coerceAtLeast(1)

        binding.ivFollowupLivestock.bindRecordPhoto(livestock.photoUri)
        binding.tvFollowupLivestockName.text = name
        binding.tvFollowupAffected.text = getString(
            R.string.livestock_health_affected_format,
            1,
            quantity
        )

        binding.ivHistoryCurrent.bindRecordPhoto(livestock.photoUri)
        binding.ivHistoryPrevious.bindRecordPhoto(livestock.photoUri)
        binding.tvHistoryCurrentAffected.text = getString(
            R.string.livestock_health_affected_format,
            1,
            quantity
        )
        binding.tvHistoryPreviousAffected.text = getString(
            R.string.livestock_health_affected_format,
            1,
            quantity
        )
    }

    private fun renderStatus() {
        val cards = linkedMapOf(
            LivestockHealthCheckBottomSheet.STATUS_INCREASED to binding.cardStatusIncreased,
            LivestockHealthCheckBottomSheet.STATUS_SAME to binding.cardStatusSame,
            LivestockHealthCheckBottomSheet.STATUS_DECREASED to binding.cardStatusDecreased,
            LivestockHealthCheckBottomSheet.STATUS_RECOVERED to binding.cardStatusRecovered
        )
        val selectedStroke = ContextCompat.getColor(requireContext(), R.color.aqua_button_blue)
        val normalStroke = ContextCompat.getColor(requireContext(), R.color.aqua_card_outline)

        cards.forEach {
            (status, card) ->
            styleStatusCard(card, status == currentStatus, selectedStroke, normalStroke)
        }
    }

    private fun styleStatusCard(
        card: MaterialCardView,
        selected: Boolean,
        selectedStroke: Int,
        normalStroke: Int
    ) {
        card.strokeWidth = resources.getDimensionPixelSize(
            if (selected) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
        )
        card.setStrokeColor(if (selected) selectedStroke else normalStroke)
    }

    private fun endSessionFollowup() {
        val navController = findNavController()
        runCatching {
            navController.getBackStackEntry(R.id.livestockHealthFragment)
                .savedStateHandle
                .set(LivestockHealthFragment.KEY_HAS_OBSERVATION, false)
        }
        navController.popBackStack(R.id.livestockHealthFragment, false)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
