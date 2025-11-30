package com.aqua.aqualight.ui.tabs.devices

import android.os.Bundle
import android.view.View
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceDashboardBinding

class DeviceDashboardFragment : Fragment(R.layout.fragment_device_dashboard) {

    private var _binding: FragmentDeviceDashboardBinding? = null
    private val binding get() = _binding!!

    // Cihaz bilgileri
    private val deviceId: Long by lazy { requireArguments().getLong("deviceId") }
    private val deviceName: String by lazy { requireArguments().getString("deviceName").orEmpty() }
    private val deviceIp: String by lazy { requireArguments().getString("deviceIp").orEmpty() }
    private val aquaName: String by lazy { requireArguments().getString("aquaName").orEmpty() }
    private val serial: String by lazy { requireArguments().getString("serial").orEmpty() }

    // JS: CheckTab("TabLight") / TabTimer / TabTemperature
    private val hasLight       by lazy { requireArguments().getBoolean("hasLight",       false) }
    private val hasTimer       by lazy { requireArguments().getBoolean("hasTimer",       false) }
    private val hasTemperature by lazy { requireArguments().getBoolean("hasTemperature", false) }

    private var initialPageLoaded = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceDashboardBinding.bind(view)

        // Başlık
        val title = deviceName.ifBlank { aquaName.ifBlank { "Device" } }
        binding.tvDeviceName.text = title

        // Geri
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Menü butonu → sağ drawer aç/kapat
        binding.btnMenu.setOnClickListener {
            toggleMenu()
        }

        // Menü item clickleri
        binding.menuLight.setOnClickListener {
            openLightPage()
            closeMenu()
        }

        binding.menuTimer.setOnClickListener {
            openTimerPage()
            closeMenu()
        }

        binding.menuTemperature.setOnClickListener {
            openTemperaturePage()
            closeMenu()
        }

        binding.menuWifi.setOnClickListener {
            // WiFi sayfası
            // openWifiPage()
            closeMenu()
        }

        binding.menuTime.setOnClickListener {
            // Time sayfası
            // openTimePage()
            closeMenu()
        }

        binding.menuGeneral.setOnClickListener {
            // General sayfası
            // openGeneralPage()
            closeMenu()
        }

        binding.menuPwm.setOnClickListener {
            // PWM sayfası
            // openPwmPage()
            closeMenu()
        }

        binding.menuNet.setOnClickListener {
            // Net sayfası
            // openNetPage()
            closeMenu()
        }

        binding.menuFs.setOnClickListener {
            // Filesystem sayfası
            // openFsPage()
            closeMenu()
        }

        binding.menuReboot.setOnClickListener {
            // Reboot sayfası
            // openRebootPage()
            closeMenu()
        }

        binding.menuInfo.setOnClickListener {
            // Info sayfası
            // openInfoPage()
            closeMenu()
        }

        binding.menuAbout.setOnClickListener {
            // About sayfası
            // openAboutPage()
            closeMenu()
        }

        // Cihaza girince index.htm mantığı ile ilk sayfayı yükle
        loadInitialPageIfNeeded()
    }

    private fun toggleMenu() {
        val drawer = binding.drawerLayout
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END)
        } else {
            drawer.openDrawer(GravityCompat.END)
        }
    }

    private fun closeMenu() {
        binding.drawerLayout.closeDrawer(GravityCompat.END)
    }

    private fun loadInitialPageIfNeeded() {
        if (initialPageLoaded) return
        initialPageLoaded = true

        val fragment: Fragment? = when {
            hasLight       -> createLightFragment()
            hasTimer       -> createTimerFragment()
            hasTemperature -> createTemperatureFragment()
            else -> null
        }

        if (fragment != null) {
            childFragmentManager.commit {
                replace(R.id.contentContainer, fragment)
            }
        }
    }

    private fun baseArgsBundle() = Bundle().apply {
        putLong("deviceId", deviceId)
        putString("deviceName", deviceName)
        putString("deviceIp", deviceIp)
        putString("aquaName", aquaName)
        putString("serial", serial)
    }

    private fun createLightFragment(): Fragment =
        DeviceLightFragment().apply { arguments = baseArgsBundle() }

    private fun createTimerFragment(): Fragment =
        DeviceTimerFragment().apply { arguments = baseArgsBundle() }

    private fun createTemperatureFragment(): Fragment =
        DeviceTemperatureFragment().apply { arguments = baseArgsBundle() }

    private fun openLightPage() {
        childFragmentManager.commit {
            replace(R.id.contentContainer, createLightFragment())
        }
    }

    private fun openTimerPage() {
        childFragmentManager.commit {
            replace(R.id.contentContainer, createTimerFragment())
        }
    }

    private fun openTemperaturePage() {
        childFragmentManager.commit {
            replace(R.id.contentContainer, createTemperatureFragment())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}