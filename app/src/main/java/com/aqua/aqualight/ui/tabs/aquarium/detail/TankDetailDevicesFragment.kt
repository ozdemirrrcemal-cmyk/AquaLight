package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailDevicesBinding
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankAssignedDeviceItem
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankAssignedDevicesAdapter
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesUiState
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesViewModel
import kotlinx.coroutines.launch

class TankDetailDevicesFragment : Fragment(R.layout.fragment_tank_detail_devices) {

    interface Host {
        fun onTankDetailAddDeviceClicked(
            tankId: Long
        )
    }

    private val viewModel: TankDetailDevicesViewModel by viewModels()

    private var _binding: FragmentTankDetailDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TankAssignedDevicesAdapter

    private var tankId: Long = 0L

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankDetailDevicesBinding.bind(view)

        setupRecycler()
        setupClickListeners()
        observeViewModel()

        viewModel.bind(tankId)
    }

    private fun setupRecycler() {
        adapter = TankAssignedDevicesAdapter(
            onDeviceClick = {
                // Cihaz detay açma daha sonra merkezi route ile bağlanacak.
            },
            onDeviceLongClick = { item ->
                confirmRemoveDevice(item)
            }
        )

        binding.rvAssignedDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAssignedDevices.adapter = adapter
        binding.rvAssignedDevices.setHasFixedSize(false)
    }

    private fun setupClickListeners() {
        binding.btnAddDevice.setOnClickListener {
            parentHost()?.onTankDetailAddDeviceClicked(
                tankId = tankId
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(
        state: TankDetailDevicesUiState
    ) {
        adapter.submitList(state.devices)
        binding.rvAssignedDevices.isVisible = state.isEmpty.not()
        binding.cardDevicesEmpty.isVisible = state.isEmpty
    }

    private fun confirmRemoveDevice(
        item: TankAssignedDeviceItem
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove device")
            .setMessage("Remove ${item.title} from this tank?")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Remove") { _, _ ->
                viewModel.removeDeviceFromTank(item.deviceUid)
            }
            .show()
    }

    private fun parentHost(): Host? {
        return parentFragment as? Host
    }

    override fun onDestroyView() {
        binding.rvAssignedDevices.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {

        private const val ARG_TANK_ID = "tankId"

        fun newInstance(
            tankId: Long
        ): TankDetailDevicesFragment {
            return TankDetailDevicesFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}
