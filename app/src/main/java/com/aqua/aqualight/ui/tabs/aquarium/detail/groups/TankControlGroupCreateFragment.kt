package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankControlGroupCreateBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

/** Navigation-only shell. Device group creation and control are not yet implemented. */
class TankControlGroupCreateFragment : Fragment(R.layout.fragment_tank_control_group_create) {

    private val args: TankControlGroupCreateFragmentArgs by navArgs()

    private var _binding: FragmentTankControlGroupCreateBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankControlGroupCreateBinding.bind(view)
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.tank_control_group_create_screen_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
