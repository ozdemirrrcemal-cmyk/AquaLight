package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.chrome.DeviceChromeHost
import com.aqua.aqualight.ui.tabs.devices.detail.chrome.DeviceHeaderAction

class DeviceLightControllerFragment :
    Fragment(R.layout.fragment_device_light_controller) {

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceIp: String
        get() = requireArguments().getString(ARG_DEVICE_IP).orEmpty()

    private val deviceTitle: String
        get() = requireArguments().getString(ARG_DEVICE_TITLE).orEmpty()
            .ifBlank {
                requireArguments().getString(ARG_DEFAULT_DEVICE_TITLE).orEmpty()
            }
            .ifBlank {
                DEFAULT_DEVICE_TITLE
            }

    private var activeScreen: LightScreen =
        LightScreen.Overview

    private val chromeHost: DeviceChromeHost?
        get() = parentFragment as? DeviceChromeHost

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        restoreState(
            savedInstanceState = savedInstanceState
        )

        setupSystemBackHandling()

        if (childFragmentManager.findFragmentById(R.id.lightControllerContainer) == null) {
            openOverview()
        } else {
            renderHeaderForActiveScreen()
        }
    }

    private fun restoreState(
        savedInstanceState: Bundle?
    ) {
        activeScreen = savedInstanceState
            ?.getString(KEY_ACTIVE_SCREEN)
            ?.let { screenName ->
                runCatching {
                    LightScreen.valueOf(screenName)
                }.getOrNull()
            }
            ?: LightScreen.Overview
    }

    private fun setupSystemBackHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBack()
                }
            }
        )
    }

    private fun handleBack() {
        when (activeScreen) {
            LightScreen.Overview -> {
                findNavController().popBackStack()
            }

            LightScreen.Manual,
            LightScreen.Programs -> {
                openOverview()
            }
        }
    }

    fun openOverview() {
        activeScreen =
            LightScreen.Overview

        renderHeaderForOverview()

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.lightControllerContainer,
                DeviceLightFragment.newInstance(
                    deviceId = deviceId
                )
            )
        }
    }

    fun openManual() {
        activeScreen =
            LightScreen.Manual

        renderHeaderForManual()

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.lightControllerContainer,
                DeviceLightManualFragment.newInstance(
                    deviceId = deviceId
                )
            )
        }
    }

    fun openPrograms() {
        activeScreen =
            LightScreen.Programs

        renderHeaderForPrograms()

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.lightControllerContainer,
                DeviceLightProgramListFragment.newInstance(
                    deviceId = deviceId
                )
            )
        }
    }

    private fun renderHeaderForActiveScreen() {
        when (activeScreen) {
            LightScreen.Overview -> {
                renderHeaderForOverview()
            }

            LightScreen.Manual -> {
                renderHeaderForManual()
            }

            LightScreen.Programs -> {
                renderHeaderForPrograms()
            }
        }
    }

    private fun renderHeaderForOverview() {
        chromeHost?.setDeviceHeader(
            title = deviceTitle,
            actions = listOf(
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_light_sync_24,
                    contentDescription = getString(R.string.light_cd_sync),
                    onClick = {
                        currentOverviewFragment()?.onHeaderSyncClick()
                    }
                ),
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_light_settings_24,
                    contentDescription = getString(R.string.light_cd_settings),
                    onClick = {
                        currentOverviewFragment()?.onHeaderSettingsClick()
                    }
                ),
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_light_more_vert_24,
                    contentDescription = getString(R.string.light_cd_more),
                    onClick = {
                        currentOverviewFragment()?.onHeaderMoreClick()
                    }
                )
            ),
            onBackClick = {
                findNavController().popBackStack()
            }
        )
    }

    private fun renderHeaderForManual() {
        chromeHost?.setDeviceHeader(
            title = getString(R.string.light_manual_title),
            actions = listOf(
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_light_sync_24,
                    contentDescription = getString(R.string.light_cd_sync),
                    onClick = {
                        currentManualFragment()?.onHeaderSyncClick()
                    }
                )
            ),
            onBackClick = {
                openOverview()
            }
        )
    }

    private fun renderHeaderForPrograms() {
        chromeHost?.setDeviceHeader(
            title = getString(R.string.light_programs_title),
            actions = listOf(
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_add_24,
                    contentDescription = getString(R.string.light_add_program),
                    onClick = {
                        currentProgramListFragment()?.onHeaderAddClick()
                    }
                )
            ),
            onBackClick = {
                openOverview()
            }
        )
    }

    private fun currentOverviewFragment(): DeviceLightFragment? {
        return childFragmentManager.findFragmentById(
            R.id.lightControllerContainer
        ) as? DeviceLightFragment
    }

    private fun currentManualFragment(): DeviceLightManualFragment? {
        return childFragmentManager.findFragmentById(
            R.id.lightControllerContainer
        ) as? DeviceLightManualFragment
    }

    private fun currentProgramListFragment(): DeviceLightProgramListFragment? {
        return childFragmentManager.findFragmentById(
            R.id.lightControllerContainer
        ) as? DeviceLightProgramListFragment
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        super.onSaveInstanceState(
            outState
        )

        outState.putString(
            KEY_ACTIVE_SCREEN,
            activeScreen.name
        )
    }

    private enum class LightScreen {
        Overview,
        Manual,
        Programs
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_DEFAULT_DEVICE_TITLE = "defaultDeviceTitle"

        private const val KEY_ACTIVE_SCREEN = "activeScreen"

        private const val DEFAULT_DEVICE_TITLE = "Device"

        fun newInstance(
            deviceId: Long,
            deviceIp: String,
            deviceTitle: String,
            defaultDeviceTitle: String
        ): DeviceLightControllerFragment {
            return DeviceLightControllerFragment().apply {
                arguments = Bundle().apply {
                    putLong(
                        ARG_DEVICE_ID,
                        deviceId
                    )

                    putString(
                        ARG_DEVICE_IP,
                        deviceIp
                    )

                    putString(
                        ARG_DEVICE_TITLE,
                        deviceTitle
                    )

                    putString(
                        ARG_DEFAULT_DEVICE_TITLE,
                        defaultDeviceTitle
                    )
                }
            }
        }
    }
}