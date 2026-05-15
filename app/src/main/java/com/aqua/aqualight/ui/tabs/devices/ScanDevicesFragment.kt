package com.aqua.aqualight.ui.tabs.devices

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentScanDevicesBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ScanDevicesFragment :
    Fragment(R.layout.fragment_scan_devices) {

    private var _binding:
            FragmentScanDevicesBinding? = null

    private val binding
        get() = _binding!!

    private lateinit var adapter:
            ScanDevicesAdapter

    private lateinit var userPrefs:
            UserPreferencesManager

    private var scanJob: Job? = null

    companion object {

        private const val SCAN_TIMEOUT_MS =
            3000L
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
            FragmentScanDevicesBinding.bind(view)

        userPrefs =
            UserPreferencesManager.create(
                requireContext()
            )

        setupRecyclerView()

        setupClickListeners()

        startScan()
    }

    // ---------------------------------------------------
    // CLICK LISTENERS
    // ---------------------------------------------------

    private fun setupClickListeners() {

        binding.btnBack.setOnClickListener {

            findNavController()
                .popBackStack()
        }

        binding.btnRescan.setOnClickListener {

            startScan()
        }
    }

    // ---------------------------------------------------
    // RECYCLER
    // ---------------------------------------------------

    private fun setupRecyclerView() {

        adapter =
            ScanDevicesAdapter { device ->

                saveSelectedDevice(device)
            }

        binding.rvDevices.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        binding.rvDevices.adapter =
            adapter
    }

    // ---------------------------------------------------
    // START SCAN
    // ---------------------------------------------------

    private fun startScan() {

        if (scanJob?.isActive == true) {
            return
        }

        scanJob =
            viewLifecycleOwner.lifecycleScope.launch {

                showScanningState()

                try {

                    val devices =
                        withTimeout(
                            SCAN_TIMEOUT_MS + 1000L
                        ) {

                            discoverDevices(
                                context = requireContext(),
                                timeoutMs = SCAN_TIMEOUT_MS
                            )
                        }

                    if (_binding == null) {
                        return@launch
                    }

                    val validDevices =
                        devices
                            .filter {
                                isValidDevice(it)
                            }
                            .distinctBy {
                                it.id
                            }

                    showResultState(
                        validDevices
                    )

                } catch (
                    e: TimeoutCancellationException
                ) {

                    e.printStackTrace()

                    if (_binding != null) {

                        showTimeoutState()
                    }

                } catch (e: Exception) {

                    e.printStackTrace()

                    if (_binding != null) {

                        showErrorState()
                    }
                }
            }
    }

    // ---------------------------------------------------
    // UI STATES
    // ---------------------------------------------------

    private fun showScanningState() {

        binding.tvTitle.text =
            getString(
                R.string.device_scan_header_scanning
            )

        binding.btnRescan.isEnabled =
            false

        binding.btnRescan.alpha =
            0.4f

        binding.scanAnimation.visibility =
            View.VISIBLE

        binding.scanAnimation.playAnimation()

        binding.rvDevices.visibility =
            View.GONE

        binding.tvNoDevices.visibility =
            View.GONE

        adapter.submitList(
            emptyList()
        )
    }

    private fun showResultState(
        devices: List<DiscoveredDevice>
    ) {

        binding.tvTitle.text =
            getString(
                R.string.device_scan_header_list
            )

        stopScanAnimation()

        binding.btnRescan.isEnabled =
            true

        binding.btnRescan.alpha =
            1f

        if (devices.isEmpty()) {

            binding.rvDevices.visibility =
                View.GONE

            binding.tvNoDevices.visibility =
                View.VISIBLE

            binding.tvNoDevices.text =
                getString(
                    R.string.device_scan_no_devices
                )

            adapter.submitList(
                emptyList()
            )

        } else {

            binding.rvDevices.visibility =
                View.VISIBLE

            binding.tvNoDevices.visibility =
                View.GONE

            adapter.submitList(
                devices
            )
        }
    }

    private fun showTimeoutState() {

        binding.tvTitle.text =
            getString(
                R.string.device_scan_header_list
            )

        stopScanAnimation()

        binding.btnRescan.isEnabled =
            true

        binding.btnRescan.alpha =
            1f

        binding.rvDevices.visibility =
            View.GONE

        binding.tvNoDevices.visibility =
            View.VISIBLE

        binding.tvNoDevices.text =
            getString(
                R.string.device_scan_timeout
            )

        adapter.submitList(
            emptyList()
        )
    }

    private fun showErrorState() {

        binding.tvTitle.text =
            getString(
                R.string.device_scan_header_list
            )

        stopScanAnimation()

        binding.btnRescan.isEnabled =
            true

        binding.btnRescan.alpha =
            1f

        binding.rvDevices.visibility =
            View.GONE

        binding.tvNoDevices.visibility =
            View.VISIBLE

        binding.tvNoDevices.text =
            getString(
                R.string.device_scan_error
            )

        adapter.submitList(
            emptyList()
        )
    }

    private fun stopScanAnimation() {

        binding.scanAnimation
            .cancelAnimation()

        binding.scanAnimation.visibility =
            View.GONE
    }

    // ---------------------------------------------------
    // SAVE DEVICE
    // ---------------------------------------------------

    private fun saveSelectedDevice(
        device: DiscoveredDevice
    ) {

        if (!isValidDevice(device)) {

            Toast.makeText(
                requireContext(),
                getString(
                    R.string.device_scan_invalid_device
                ),
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val aquaName =
            device.aquaName
                ?.ifBlank { "-" }
                ?: "-"

        val name =
            device.name.ifBlank {
                "Device"
            }

        val serial =
            buildSerial(
                aquaName,
                name,
                device.id
            )

        viewLifecycleOwner.lifecycleScope
            .launch {

                val alreadyExists =
                    userPrefs.deviceExists(
                        device.id
                    )

                if (alreadyExists) {

                    Toast.makeText(
                        requireContext(),
                        getString(
                            R.string.device_scan_already_added
                        ),
                        Toast.LENGTH_SHORT
                    ).show()

                    if (_binding != null) {

                        findNavController()
                            .popBackStack()
                    }

                    return@launch
                }

                userPrefs.addDevice(
                    id = device.id,
                    aquaName = aquaName,
                    name = name,
                    ip = device.ip,
                    serial = serial,
                    firmwareBuild =
                        device.firmwareBuild ?: ""
                )

                if (_binding != null) {

                    findNavController()
                        .popBackStack()
                }
            }
    }

    // ---------------------------------------------------
    // VALIDATION
    // ---------------------------------------------------

    private fun isValidDevice(
        device: DiscoveredDevice
    ): Boolean {

        if (device.id <= 0L) {
            return false
        }

        if (device.ip.isBlank()) {
            return false
        }

        if (
            device.name.isBlank() &&
            device.aquaName.isNullOrBlank()
        ) {
            return false
        }

        return true
    }

    // ---------------------------------------------------
    // SERIAL
    // ---------------------------------------------------

    private fun buildSerial(
        aquaName: String,
        name: String,
        id: Long
    ): String {

        val a =
            aquaName.firstOrNull()
                ?.uppercaseChar()
                ?: 'X'

        val n =
            name.firstOrNull()
                ?.uppercaseChar()
                ?: 'X'

        val core =
            if (id != 0L) {
                id.toString()
            } else {
                ""
            }

        return if (core.isNotEmpty()) {

            "$a$n-$core"

        } else {

            "$a$n"
        }
    }

    // ---------------------------------------------------
    // DESTROY
    // ---------------------------------------------------

    override fun onDestroyView() {

        scanJob?.cancel()

        scanJob = null

        _binding
            ?.scanAnimation
            ?.cancelAnimation()

        _binding
            ?.rvDevices
            ?.adapter = null

        _binding = null

        super.onDestroyView()
    }
}