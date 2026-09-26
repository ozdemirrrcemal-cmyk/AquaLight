package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.databinding.FragmentTankHealthBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class TankHealthFragment : Fragment(R.layout.fragment_tank_health) {

    private val args: TankHealthFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentTankHealthBinding? = null
    private val binding get() = _binding!!
    private var contentAdapter: TankHealthContentAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthBinding.bind(view)

        setupHeader()
        setupContent()
        observeTankProfile()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun setupContent() {
        val adapter = TankHealthContentAdapter(
            onAddAnalysisClick = ::openAddAnalysis
        )
        contentAdapter = adapter

        binding.healthContent.layoutManager = GridLayoutManager(
            requireContext(),
            TankHealthContentAdapter.GRID_SPAN_COUNT
        ).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    adapter.spanSizeForPosition(position)
            }
        }
        binding.healthContent.adapter = adapter
        binding.healthContent.itemAnimator = null
    }

    private fun observeTankProfile() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tankProfile = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.tankType
                ?.takeIf(AquariumTankTaxonomy::isSupportedTankType)

            val metrics = tankProfile
                ?.let { profile ->
                    TankHealthWaterMetricUiCatalog.models(
                        tankProfile = profile,
                        measuredParameterIds = emptyList()
                    )
                }
                .orEmpty()

            contentAdapter?.submitWaterMetrics(metrics)
        }
    }

    private fun openAddAnalysis() {
        findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.tankHealthFragment,
            directions = TankHealthFragmentDirections
                .actionTankHealthFragmentToTankHealthAnalysisAddFragment(args.tankId)
        )
    }

    override fun onDestroyView() {
        binding.healthContent.adapter = null
        contentAdapter = null
        _binding = null
        super.onDestroyView()
    }
}
