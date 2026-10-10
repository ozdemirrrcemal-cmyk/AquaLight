package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentTankControlGroupCreateBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

/** Group selection UI; persistent creation is deliberately a separate stage. */
class TankControlGroupCreateFragment : Fragment(R.layout.fragment_tank_control_group_create) {

    private val args: TankControlGroupCreateFragmentArgs by navArgs()
    private val viewModel: TankControlGroupCreateViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentTankControlGroupCreateBinding? = null
    private val binding get() = _binding!!
    private var drag: TankControlGroupDragController? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (args.tankId <= 0L) {
            findNavController().navigateUp()
            return
        }

        _binding = FragmentTankControlGroupCreateBinding.bind(view)
        binding.appHeader.root.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.aqua_card_surface))
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.tank_control_group_create_screen_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )
        binding.appHeader.tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.aqua_text_size_title_medium))
        ViewCompat.setAccessibilityHeading(binding.tvGroupTitle, true)
        ViewCompat.setAccessibilityHeading(binding.tvDevicesTitle, true)
        val controller = TankControlGroupDragController(binding, viewModel)
        drag = controller
        val group = TankControlGroupDeviceAdapter(true, { viewModel.move(it, false) },
            { card, uid -> controller.start(card, uid, true) })
        val devices = TankControlGroupDeviceAdapter(false, { viewModel.move(it, true) },
            { card, uid -> controller.start(card, uid, false) })
        binding.rvGroup.layoutManager = GridLayoutManager(requireContext(), COLUMNS)
        binding.rvDevices.layoutManager = GridLayoutManager(requireContext(), COLUMNS)
        binding.rvGroup.adapter = group
        binding.rvDevices.adapter = devices
        val renderer = TankControlGroupRenderer(binding, group, devices)
        viewModel.bind(args.tankId)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(renderer::render)
            }
        }
    }

    override fun onDestroyView() {
        drag?.close()
        drag = null
        _binding?.rvGroup?.adapter = null
        _binding?.rvDevices?.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val COLUMNS = 2
    }
}
