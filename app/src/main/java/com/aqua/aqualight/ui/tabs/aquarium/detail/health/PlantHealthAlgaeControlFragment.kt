package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentPlantHealthAlgaeControlBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel

/** Tank-scoped destination reserved for the future algae-control design. */
class PlantHealthAlgaeControlFragment : Fragment(R.layout.fragment_plant_health_algae_control) {
    private val args: PlantHealthAlgaeControlFragmentArgs by navArgs()
    private val tanks: AquariumTankViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentPlantHealthAlgaeControlBinding.bind(view).appHeader.setupAquaHeader(this, AquaHeaderConfig(
            titleOverride = getString(R.string.plant_health_algae_control_title),
            onBackClick = { findNavController().navigateUp() }
        ))
        tanks.tanks.observe(viewLifecycleOwner) { list ->
            if (list.none { it.id == args.tankId }) findNavController().navigateUp()
        }
    }
}
