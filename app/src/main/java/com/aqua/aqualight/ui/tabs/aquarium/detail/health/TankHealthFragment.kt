package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankHealthBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class TankHealthFragment : Fragment(R.layout.fragment_tank_health) {

    private val args: TankHealthFragmentArgs by navArgs()

    private var _binding: FragmentTankHealthBinding? = null
    private val binding get() = _binding!!

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

    private fun setupContent() {
        val contentAdapter = TankHealthContentAdapter(
            onAddAnalysisClick = ::openAddAnalysis
        )
        val contentLayoutManager = GridLayoutManager(
            requireContext(),
            TankHealthContentAdapter.GRID_SPAN_COUNT
        ).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return contentAdapter.spanSizeForPosition(position)
                }
            }
        }

        binding.healthContent.layoutManager = contentLayoutManager
        binding.healthContent.adapter = contentAdapter
        binding.healthContent.itemAnimator = null
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
        _binding = null
        super.onDestroyView()
    }

}
