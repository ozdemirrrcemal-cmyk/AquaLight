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

        setupHeader()
        setupTabs()
        renderSelectedSection()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health),
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun setupTabs() {
        binding.tabWaterQuality.setOnClickListener {
            selectSection(HealthSection.WATER_QUALITY)
        }
        binding.tabAlgaeControl.setOnClickListener {
            selectSection(HealthSection.ALGAE_CONTROL)
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

    private fun renderTabState(
        selected: Boolean,
        card: com.google.android.material.card.MaterialCardView,
        text: android.widget.TextView
    ) {
        val context = requireContext()

        card.setCardBackgroundColor(
            ContextCompat.getColor(
                context,
                if (selected) {
                    R.color.aqua_surface_action
                } else {
                    R.color.aqua_card_metric_surface
                }
            )
        )
        card.strokeColor = ContextCompat.getColor(
            context,
            if (selected) {
                R.color.aqua_accent_primary
            } else {
                R.color.aqua_card_metric_outline
            }
        )
        text.setTextColor(
            ContextCompat.getColor(
                context,
                if (selected) {
                    R.color.aqua_content_on_dark
                } else {
                    R.color.aqua_card_text_secondary
                }
            )
        )
        text.setTypeface(
            text.typeface,
            if (selected) Typeface.BOLD else Typeface.NORMAL
        )
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
        private const val KEY_SELECTED_SECTION = "selectedSection"
        private const val ALGAE_FRAGMENT_TAG = "tankAlgaeControl"
    }
}
