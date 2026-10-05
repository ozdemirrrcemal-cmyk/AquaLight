package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.databinding.FragmentLivestockHealthBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel

class LivestockHealthFragment : Fragment(R.layout.fragment_livestock_health) {

    private val args: LivestockHealthFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val healthViewModel: LivestockHealthViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthBinding? = null
    private val binding get() = _binding!!

    private var currentTank: AquariumTankSnapshot? = null
    private var activeFollowups: List<ActiveLivestockFollowupUi> = emptyList()
    private var closedFollowups: List<ClosedLivestockFollowupUi> = emptyList()
    private var renderer: LivestockHealthHomeRenderer? = null
    private var navigator: LivestockHealthNavigator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "LivestockHealthFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthBinding.bind(view)
        navigator = LivestockHealthNavigator(this, args.tankId)
        renderer = LivestockHealthHomeRenderer(
            fragment = this,
            binding = binding,
            onActiveClick = { entry -> navigator?.openActiveFollowup(entry) },
            onPastClick = { entry -> navigator?.openPastFollowup(entry) }
        )

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_livestock_health),
                onBackClick = { findNavController().navigateUp() }
            )
        )

        binding.btnNewObservation.setOnClickListener {
            navigator?.openNewObservation(
                hasLivestock = !currentTank?.livestock.isNullOrEmpty()
            )
        }
        binding.btnViewAllPast.setOnClickListener {
            navigator?.openAllPastFollowups(closedFollowups.isNotEmpty())
        }

        observeHealthRecords()
        observeTank()
    }

    override fun onResume() {
        super.onResume()
        navigator?.reset()
        render()
    }

    private fun observeHealthRecords() {
        healthViewModel.observationsForTank(args.tankId).observe(viewLifecycleOwner) { records ->
            activeFollowups = records.filter { it.closedAtMillis == null }.map { it.toActiveUi() }
            closedFollowups = records.filter { it.closedAtMillis != null }.map { it.toClosedUi() }
            render()
        }
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            currentTank = tanks.firstOrNull { tank -> tank.id == args.tankId }
            render()
        }
    }

    private fun render() {
        renderer?.render(
            tank = currentTank,
            activeFollowups = activeFollowups,
            closedFollowups = closedFollowups
        )
    }

    override fun onDestroyView() {
        renderer = null
        navigator = null
        _binding = null
        super.onDestroyView()
    }

}
