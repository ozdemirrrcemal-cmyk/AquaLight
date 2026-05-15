package com.aqua.aqualight.ui.tabs.settings.device

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentDeviceStatusBinding
import com.aqua.aqualight.ui.tabs.devices.DeviceCardUi
import kotlinx.coroutines.flow.collectLatest

class DeviceStatusFragment :
    Fragment(R.layout.fragment_device_status) {

    private var _binding: FragmentDeviceStatusBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DeviceStatusAdapter

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    companion object {
        private const val ONLINE_TIMEOUT_MS = 60_000L
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDeviceStatusBinding.bind(view)

        setupRecycler()

        observeDevices()

        // BACK
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    // ---------------------------------------------------
    // RECYCLER
    // ---------------------------------------------------

    private fun setupRecycler() {

        adapter =
            DeviceStatusAdapter()

        binding.rvDevices.layoutManager =
            LinearLayoutManager(requireContext())

        binding.rvDevices.adapter =
            adapter
    }

    // ---------------------------------------------------
    // OBSERVE DEVICES
    // ---------------------------------------------------

    private fun observeDevices() {

        viewLifecycleOwner.lifecycleScope
            .launchWhenStarted {

                userPrefs.devicesFlow
                    .collectLatest { list ->

                        val now =
                            System.currentTimeMillis()

                        val uiList =
                            list.map { dev ->

                                val online =
                                    dev.lastSeenMillis != 0L &&
                                    ((now - dev.lastSeenMillis) <= ONLINE_TIMEOUT_MS)

                                DeviceCardUi(

                                    id =
                                        dev.id,

                                    aquaName =
                                        dev.aquaName,

                                    name =
                                        dev.name.ifBlank {
                                            "Device"
                                        },

                                    ip =
                                        dev.ip,

                                    serial =
                                        dev.serial,

                                    firmwareBuild =
                                        dev.firmwareBuild,

                                    isOnline =
                                        online
                                )
                            }

                        adapter.submitList(
                            uiList
                        )
                    }
            }
    }

    // ---------------------------------------------------
    // DESTROY
    // ---------------------------------------------------

    override fun onDestroyView() {

        binding.rvDevices.adapter = null

        _binding = null

        super.onDestroyView()
    }
}