package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import android.content.ClipData
import android.os.Bundle
import android.view.DragEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentTankControlGroupCreateBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class TankControlGroupCreateFragment : Fragment(R.layout.fragment_tank_control_group_create) {
    private val args: TankControlGroupCreateFragmentArgs by navArgs()
    private val viewModel: TankControlGroupCreateViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentTankControlGroupCreateBinding? = null
    private val binding get() = _binding!!
    private var dragSession: Any = Any()
    private lateinit var selectedAdapter: TankControlGroupDeviceAdapter
    private lateinit var devicesAdapter: TankControlGroupDeviceAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (args.tankId <= 0L) {
            findNavController().navigateUp()
            return
        }
        _binding = FragmentTankControlGroupCreateBinding.bind(view)
        dragSession = Any()
        binding.appHeader.setupAquaHeader(this, AquaHeaderConfig(
            titleOverride = getString(R.string.tank_control_group_create_screen_title),
            onBackClick = { findNavController().navigateUp() }
        ))
        selectedAdapter = TankControlGroupDeviceAdapter(true, { viewModel.remove(it) }) { source, uid ->
            startDrag(source, uid, fromGroup = true)
        }
        devicesAdapter = TankControlGroupDeviceAdapter(false, { viewModel.add(it) }) { source, uid ->
            startDrag(source, uid, fromGroup = false)
        }
        binding.rvSelected.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSelected.adapter = selectedAdapter
        binding.rvDevices.adapter = devicesAdapter
        installDropTarget(binding.groupDropZone, binding.groupDropZone, toGroup = true)
        installDropTarget(binding.rvSelected, binding.groupDropZone, toGroup = true)
        installDropTarget(binding.devicesDropZone, binding.devicesDropZone, toGroup = false)
        installDropTarget(binding.rvDevices, binding.devicesDropZone, toGroup = false)
        binding.btnRefresh.setOnClickListener { viewModel.retry() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        viewModel.bind(args.tankId)
    }

    private fun startDrag(source: View, uid: String, fromGroup: Boolean): Boolean {
        if (if (fromGroup) !viewModel.canRemove(uid) else !viewModel.canAdd(uid)) return false
        return source.startDragAndDrop(
            ClipData.newPlainText("AquaLight group device", uid), View.DragShadowBuilder(source),
            DeviceDrag(dragSession, uid, fromGroup), 0
        )
    }

    private fun installDropTarget(target: View, card: MaterialCardView, toGroup: Boolean) {
        target.setOnDragListener { view, event ->
            val payload = event.localState as? DeviceDrag
            if (payload == null || payload.session !== dragSession) return@setOnDragListener false
            val allowed = canDrop(payload, toGroup)
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> allowed
                DragEvent.ACTION_DRAG_ENTERED -> { highlight(card, allowed); true }
                DragEvent.ACTION_DRAG_LOCATION -> {
                    highlight(card, allowed)
                    if (allowed && view is RecyclerView) scrollAtEdge(view, event, toGroup)
                    true
                }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                    highlight(card, false)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    highlight(card, false)
                    allowed && (if (toGroup) viewModel.add(payload.uid) else viewModel.remove(payload.uid))
                }
                else -> true
            }
        }
    }

    private fun canDrop(payload: DeviceDrag, toGroup: Boolean): Boolean =
        payload.fromGroup != toGroup &&
            (if (toGroup) viewModel.canAdd(payload.uid) else viewModel.canRemove(payload.uid))

    private fun scrollAtEdge(view: RecyclerView, event: DragEvent, horizontal: Boolean) {
        val edge = resources.getDimension(R.dimen.aqua_size_48)
        val step = resources.getDimensionPixelSize(R.dimen.aqua_size_16)
        val coordinate = if (horizontal) event.x else event.y
        val extent = if (horizontal) view.width else view.height
        val delta = when { coordinate < edge -> -step; coordinate > extent - edge -> step; else -> 0 }
        view.scrollBy(if (horizontal) delta else 0, if (horizontal) 0 else delta)
    }

    private fun highlight(card: MaterialCardView, active: Boolean) {
        card.strokeColor = ContextCompat.getColor(requireContext(), if (active)
            R.color.aqua_accent_positive else R.color.aqua_card_device_section_outline)
    }

    private fun render(state: TankControlGroupCreateUiState) {
        val enabled = !state.isLoading && !state.loadFailed
        selectedAdapter.submitList(state.selected.map { ControlGroupDeviceRow(it, enabled) })
        devicesAdapter.submitList(state.available.map {
            ControlGroupDeviceRow(it, enabled && it.compatibility != null)
        })
        binding.tvGroupTitle.text = getString(R.string.tank_group_selected_count, state.selected.size)
        binding.tvGroupEmpty.isVisible = state.selected.isEmpty()
        binding.tvSelectionSummary.text = getString(summaryResource(state))
        binding.tvDevicesTitle.text = getString(if (state.selected.isEmpty())
            R.string.tank_group_devices_count else R.string.tank_group_compatible_count, state.available.size)
        val notices = mutableListOf<String>()
        if (state.selectionAdjusted) notices += getString(R.string.tank_group_selection_adjusted)
        if (state.hiddenCount > 0) notices += getString(R.string.tank_group_hidden_count, state.hiddenCount)
        binding.tvNotice.text = notices.joinToString("\n")
        binding.tvNotice.isVisible = notices.isNotEmpty()
        binding.rvDevices.isVisible = enabled && state.available.isNotEmpty()
        binding.listState.isVisible = !enabled || state.available.isEmpty()
        binding.progressDevices.isVisible = state.isLoading
        binding.tvListState.text = getString(listStateResource(state))
        binding.btnRefresh.isEnabled = !state.isLoading
        binding.btnRefresh.setText(if (state.loadFailed) R.string.tank_group_retry else R.string.tank_group_refresh)
    }

    private fun summaryResource(state: TankControlGroupCreateUiState): Int = when {
        state.isLoading -> R.string.tank_group_loading
        state.loadFailed -> R.string.tank_group_load_failed
        state.isSelectionReady -> R.string.tank_group_selection_ready
        state.selected.any { it.compatibility == null } -> R.string.tank_group_identity_unavailable
        else -> R.string.tank_group_minimum_two
    }

    private fun listStateResource(state: TankControlGroupCreateUiState): Int = when {
        state.isLoading -> R.string.tank_group_loading
        state.loadFailed -> R.string.tank_group_load_failed
        state.selected.isNotEmpty() -> R.string.tank_group_no_more_compatible
        else -> R.string.tank_group_no_lights
    }

    override fun onDestroyView() {
        _binding?.let {
            it.groupDropZone.setOnDragListener(null)
            it.devicesDropZone.setOnDragListener(null)
            it.rvSelected.setOnDragListener(null)
            it.rvDevices.setOnDragListener(null)
            it.rvSelected.adapter = null
            it.rvDevices.adapter = null
        }
        dragSession = Any()
        _binding = null
        super.onDestroyView()
    }

    private data class DeviceDrag(val session: Any, val uid: String, val fromGroup: Boolean)
}
