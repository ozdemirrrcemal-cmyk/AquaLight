package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankHealthBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae.TankAlgaeControlFragment

class TankHealthFragment : Fragment(R.layout.fragment_tank_health) {

    private val args: TankHealthFragmentArgs by navArgs()

    private var _binding: FragmentTankHealthBinding? = null
    private val binding get() = _binding!!

    private var selectedSection: HealthSection = HealthSection.WATER_QUALITY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthFragment requires a positive tankId."
        }

        selectedSection = savedInstanceState
            ?.getString(KEY_SELECTED_SECTION)
            ?.let { value ->
                runCatching {
                    HealthSection.valueOf(value)
                }.getOrNull()
            }
            ?: HealthSection.WATER_QUALITY
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthBinding.bind(view)

        setupTankHealthHeader(this, binding)
        setupTabs()
        setupReturnActions()
        renderSelectedSection()
    }

    private fun setupTabs() {
        binding.tabWaterQuality.setOnClickListener {
            selectSection(HealthSection.WATER_QUALITY)
        }
        binding.tabAlgaeControl.setOnClickListener {
            selectSection(HealthSection.ALGAE_CONTROL)
        }
    }

    private fun setupReturnActions() {
        val savedStateHandle = findNavController()
            .currentBackStackEntry
            ?.savedStateHandle
            ?: return

        savedStateHandle.getLiveData<Boolean>(
            KEY_RETURN_ALGAE_OVERVIEW
        ).observe(viewLifecycleOwner) { shouldOpen ->
            if (shouldOpen != true) {
                return@observe
            }

            savedStateHandle.set(KEY_RETURN_ALGAE_OVERVIEW, false)
            openAlgaeSection {
                it.openOverview()
            }
        }

        savedStateHandle.getLiveData<Boolean>(
            KEY_OPEN_NEW_ALGAE_OBSERVATION
        ).observe(viewLifecycleOwner) { shouldOpen ->
            if (shouldOpen != true) {
                return@observe
            }

            savedStateHandle.set(KEY_OPEN_NEW_ALGAE_OBSERVATION, false)
            openAlgaeSection {
                it.openNewObservation()
            }
        }
    }

    private fun openAlgaeSection(
        action: (TankAlgaeControlFragment) -> Unit
    ) {
        selectedSection = HealthSection.ALGAE_CONTROL
        renderSelectedSection()

        childFragmentManager.executePendingTransactions()
        val algaeFragment = childFragmentManager
            .findFragmentByTag(ALGAE_FRAGMENT_TAG)
            as? TankAlgaeControlFragment

        if (algaeFragment != null) {
            action(algaeFragment)
        }
    }


    private fun selectSection(section: HealthSection) {
        if (selectedSection == section) {
            return
        }

        selectedSection = section
        renderSelectedSection()
    }

    private fun renderSelectedSection() {
        val showWaterQuality = selectedSection == HealthSection.WATER_QUALITY

        binding.waterQualityScroll.isVisible = showWaterQuality
        binding.algaeControlContainer.isVisible = !showWaterQuality

        renderTabState(
            selected = showWaterQuality,
            card = binding.tabWaterQuality,
            text = binding.tvWaterQualityTab
        )
        renderTabState(
            selected = !showWaterQuality,
            card = binding.tabAlgaeControl,
            text = binding.tvAlgaeControlTab
        )

        if (!showWaterQuality) {
            ensureAlgaeControlFragment()
        }
    }

    private fun ensureAlgaeControlFragment() {
        if (childFragmentManager.findFragmentByTag(ALGAE_FRAGMENT_TAG) != null) {
            return
        }

        childFragmentManager.beginTransaction()
            .replace(
                R.id.algaeControlContainer,
                TankAlgaeControlFragment.newInstance(args.tankId),
                ALGAE_FRAGMENT_TAG
            )
            .commitNow()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_SELECTED_SECTION, selectedSection.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private enum class HealthSection {
        WATER_QUALITY,
        ALGAE_CONTROL
    }

    companion object {
        const val KEY_RETURN_ALGAE_OVERVIEW = "returnAlgaeOverview"
        const val KEY_OPEN_NEW_ALGAE_OBSERVATION = "openNewAlgaeObservation"

        private const val KEY_SELECTED_SECTION = "selectedSection"
        private const val ALGAE_FRAGMENT_TAG = "tankAlgaeControl"
    }
}

private fun Fragment.renderTabState(
    selected: Boolean,
    card: com.google.android.material.card.MaterialCardView,
    text: android.widget.TextView
) {
    val context = requireContext()
    val backgroundColor = if (selected) {
        R.color.aqua_surface_action
    } else {
        R.color.aqua_card_metric_surface
    }
    val strokeColor = if (selected) {
        R.color.aqua_accent_primary
    } else {
        R.color.aqua_card_metric_outline
    }
    val textColor = if (selected) {
        R.color.aqua_content_on_dark
    } else {
        R.color.aqua_card_text_secondary
    }

    card.setCardBackgroundColor(ContextCompat.getColor(context, backgroundColor))
    card.strokeColor = ContextCompat.getColor(context, strokeColor)
    text.setTextColor(ContextCompat.getColor(context, textColor))
    text.setTypeface(
        text.typeface,
        if (selected) Typeface.BOLD else Typeface.NORMAL
    )
}

private fun setupTankHealthHeader(
    fragment: TankHealthFragment,
    binding: FragmentTankHealthBinding
) {
    binding.appHeader.setupAquaHeader(
        fragment = fragment,
        config = AquaHeaderConfig(
            titleOverride = fragment.getString(R.string.screen_title_tank_health),
            onBackClick = {
                fragment.findNavController().navigateUp()
            }
        )
    )
}
