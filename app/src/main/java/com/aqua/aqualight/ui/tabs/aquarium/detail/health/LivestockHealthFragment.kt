package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.databinding.FragmentLivestockHealthBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthActiveFollowupBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthPastFollowupBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import java.util.concurrent.TimeUnit

class LivestockHealthFragment : Fragment(R.layout.fragment_livestock_health) {

    private val args: LivestockHealthFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthBinding? = null
    private val binding get() = _binding!!

    private var currentTank: AquariumTankSnapshot? = null
    private var activeFollowups: List<ActiveLivestockFollowupUi> = emptyList()
    private var closedFollowups: List<ClosedLivestockFollowupUi> = emptyList()
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
                onBackClick = { findNavController().navigateUp() }
            )
        )

        binding.btnNewObservation.setOnClickListener { openNewObservation() }
        binding.btnViewAllPast.setOnClickListener { openAllPastFollowups() }

        observeSessionUiState()
        observeTank()
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
        readSessionState()
        render()
    }

    private fun observeSessionUiState() {
        val handle = mainStateHandle() ?: return
        listOf(
            LivestockHealthUiSessionState.KEY_ACTIVE_REVISION,
            LivestockHealthUiSessionState.KEY_CLOSED_REVISION,
            LivestockHealthUiSessionState.KEY_HAS_OBSERVATION
        ).forEach { key ->
            handle.getLiveData<Any?>(key).observe(viewLifecycleOwner) {
                readSessionState()
                render()
            }
        }
    }

    private fun readSessionState() {
        val handle = mainStateHandle() ?: return
        activeFollowups = LivestockHealthUiSessionState.activeFollowups(handle)
        closedFollowups = LivestockHealthUiSessionState.closedFollowups(handle)
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            currentTank = tanks.firstOrNull { tank -> tank.id == args.tankId }
            render()
        }
    }

    private fun render() {
        val livestock = currentTank?.livestock.orEmpty()
        val hasActive = activeFollowups.any { entry ->
            livestock.any { item -> item.id == entry.livestockId }
        }
        val hasHistory = closedFollowups.isNotEmpty()
        val hasAnyTracking = hasActive || hasHistory

        binding.tvHeroTitle.setText(
            if (hasAnyTracking) {
                R.string.livestock_health_hero_active_title
            } else {
                R.string.livestock_health_hero_empty_title
            }
        )
        binding.tvHeroSubtitle.setText(
            if (hasAnyTracking) {
                R.string.livestock_health_hero_active_subtitle
            } else {
                R.string.livestock_health_hero_empty_subtitle
            }
        )

        binding.cardEmptyFollowups.isVisible = !hasAnyTracking
        binding.cardNoObservationInfo.isVisible = !hasAnyTracking
        binding.filledHealthContent.isVisible = hasAnyTracking

        binding.btnNewObservation.isEnabled = livestock.isNotEmpty()
        if (livestock.isEmpty()) {
            binding.tvEmptyFollowupsTitle.setText(R.string.livestock_health_no_livestock_title)
            binding.tvEmptyFollowupsBody.setText(R.string.livestock_health_no_livestock_body)
        } else {
            binding.tvEmptyFollowupsTitle.setText(R.string.livestock_health_empty_followups_title)
            binding.tvEmptyFollowupsBody.setText(R.string.livestock_health_empty_followups_body)
        }

        renderActiveFollowups(hasActive)
        renderPastFollowups()
    }

    private fun renderActiveFollowups(hasActive: Boolean) {
        binding.activeFollowupsContainer.removeAllViews()
        binding.tvActiveFollowupsTitle.isVisible = hasActive
        binding.activeFollowupsContainer.isVisible = hasActive
        if (!hasActive) return

        activeFollowups.forEach { entry ->
            val livestock = currentTank
                ?.livestock
                ?.firstOrNull { item -> item.id == entry.livestockId }
                ?: return@forEach
            val item = ItemLivestockHealthActiveFollowupBinding.inflate(
                LayoutInflater.from(requireContext()),
                binding.activeFollowupsContainer,
                false
            )
            item.ivLivestock.bindRecordPhoto(
                livestock.photoUri,
                LivestockCategories.iconRes(livestock.category)
            )
            item.tvName.text = livestock.displayName()
            item.tvIssue.setText(LivestockHealthUiText.symptomLabelRes(entry.symptomKey))
            item.tvAffected.text = getString(
                R.string.livestock_health_affected_format,
                entry.affectedCount.coerceIn(1, livestock.quantity.coerceAtLeast(1)),
                livestock.quantity.coerceAtLeast(1)
            )
            item.tvLastCheck.text = getString(
                R.string.livestock_health_active_last_check_format,
                formatLastCheck(entry.lastCheckAtMillis)
            )
            item.root.setOnClickListener { openActiveFollowup(entry) }
            binding.activeFollowupsContainer.addView(item.root)
        }
    }

    private fun renderPastFollowups() {
        binding.pastFollowupsContainer.removeAllViews()
        val latest = closedFollowups.take(MAX_HOME_HISTORY)
        binding.tvPastFollowupsEmpty.isVisible = latest.isEmpty()
        binding.btnViewAllPast.isVisible = closedFollowups.isNotEmpty()

        latest.forEach { entry ->
            val livestock = currentTank
                ?.livestock
                ?.firstOrNull { item -> item.id == entry.livestockId }
                ?: return@forEach
            val item = ItemLivestockHealthPastFollowupBinding.inflate(
                LayoutInflater.from(requireContext()),
                binding.pastFollowupsContainer,
                false
            )
            bindPastFollowup(item, livestock, entry)
            item.root.setOnClickListener { openPastFollowup(entry) }
            binding.pastFollowupsContainer.addView(item.root)
        }
    }

    private fun bindPastFollowup(
        item: ItemLivestockHealthPastFollowupBinding,
        livestock: AquariumLivestock,
        entry: ClosedLivestockFollowupUi
    ) {
        item.ivLivestock.bindRecordPhoto(
            livestock.photoUri,
            LivestockCategories.iconRes(livestock.category)
        )
        item.tvName.text = livestock.displayName()
        item.tvIssue.setText(LivestockHealthUiText.symptomLabelRes(entry.symptomKey))
        item.tvPeriod.text = getString(
            R.string.livestock_health_past_period_format,
            LocaleFormatter.formatDate(requireContext(), entry.startedAtMillis),
            LocaleFormatter.formatDate(requireContext(), entry.closedAtMillis),
            followupDurationDays(entry)
        )
        item.tvCloseReason.text = getString(
            if (entry.closeReason == LivestockHealthUiSessionState.CLOSE_REASON_RECOVERED) {
                R.string.livestock_health_past_recovered_format
            } else {
                R.string.livestock_health_past_manual_format
            },
            entry.checkCount
        )
        item.tvCloseReason.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (entry.closeReason == LivestockHealthUiSessionState.CLOSE_REASON_RECOVERED) {
                    R.color.aqua_status_success
                } else {
                    R.color.aqua_card_text_secondary
                }
            )
        )
    }

    private fun AquariumLivestock.displayName(): String = name.ifBlank {
        getString(R.string.aquarium_unnamed_livestock)
    }

    private fun followupDurationDays(entry: ClosedLivestockFollowupUi): Long =
        TimeUnit.MILLISECONDS.toDays(
            (entry.closedAtMillis - entry.startedAtMillis).coerceAtLeast(0L)
        ).coerceAtLeast(1L)

    private fun formatLastCheck(millis: Long): String =
        if (millis > 0L) {
            getString(
                R.string.livestock_health_today_time_format,
                LocaleFormatter.formatTime(requireContext(), millis)
            )
        } else {
            getString(R.string.livestock_health_no_check_yet)
        }

    private fun openNewObservation() {
        if (isNavigating || currentTank?.livestock.isNullOrEmpty()) return
        val didNavigate = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.livestockHealthFragment,
            directions = LivestockHealthFragmentDirections
                .actionLivestockHealthFragmentToLivestockHealthObservationFragment(args.tankId)
        )
        isNavigating = didNavigate
    }

    private fun openActiveFollowup(entry: ActiveLivestockFollowupUi) {
        if (isNavigating) return
        val didNavigate = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.livestockHealthFragment,
            directions = LivestockHealthFragmentDirections
                .actionLivestockHealthFragmentToLivestockHealthFollowUpFragment(
                    tankId = args.tankId,
                    livestockId = entry.livestockId,
                    symptomKey = entry.symptomKey,
                    affectedCount = entry.affectedCount,
                    readOnly = false
                )
        )
        isNavigating = didNavigate
    }

    private fun openPastFollowup(entry: ClosedLivestockFollowupUi) {
        if (isNavigating) return
        val didNavigate = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.livestockHealthFragment,
            directions = LivestockHealthFragmentDirections
                .actionLivestockHealthFragmentToLivestockHealthFollowUpFragment(
                    tankId = args.tankId,
                    livestockId = entry.livestockId,
                    symptomKey = entry.symptomKey,
                    affectedCount = entry.affectedCount,
                    readOnly = true
                )
        )
        isNavigating = didNavigate
    }

    private fun openAllPastFollowups() {
        if (isNavigating || closedFollowups.isEmpty()) return
        val didNavigate = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.livestockHealthFragment,
            directions = LivestockHealthFragmentDirections
                .actionLivestockHealthFragmentToLivestockHealthHistoryFragment(args.tankId)
        )
        isNavigating = didNavigate
    }

    private fun mainStateHandle(): SavedStateHandle? =
        findNavController().currentBackStackEntry?.savedStateHandle

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val KEY_HAS_OBSERVATION = LivestockHealthUiSessionState.KEY_HAS_OBSERVATION
        const val KEY_LIVESTOCK_ID = LivestockHealthUiSessionState.KEY_LIVESTOCK_ID
        const val KEY_SYMPTOM_KEY = LivestockHealthUiSessionState.KEY_SYMPTOM_KEY
        const val KEY_AFFECTED_COUNT = LivestockHealthUiSessionState.KEY_AFFECTED_COUNT
        const val KEY_STARTED_AT = LivestockHealthUiSessionState.KEY_STARTED_AT
        const val KEY_LAST_CHECK_AT = LivestockHealthUiSessionState.KEY_LAST_CHECK_AT

        private const val MAX_HOME_HISTORY = 2
    }
}
