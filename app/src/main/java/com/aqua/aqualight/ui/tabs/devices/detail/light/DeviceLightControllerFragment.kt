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
        get() = requireArguments()
            .getString(ARG_DEVICE_TITLE)
            .orEmpty()
            .ifBlank {
                requireArguments()
                    .getString(ARG_DEFAULT_DEVICE_TITLE)
                    .orEmpty()
            }
            .ifBlank {
                DEFAULT_DEVICE_TITLE
            }

    private var activeScreen: LightScreen =
        LightScreen.Overview

    private var activeProgramName: String =
        DEFAULT_PROGRAM_NAME

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

    override fun onResume() {
        super.onResume()

        renderHeaderForActiveScreen()
    }

    private fun restoreState(
        savedInstanceState: Bundle?
    ) {
        activeScreen =
            savedInstanceState
                ?.getString(KEY_ACTIVE_SCREEN)
                ?.let { screenName ->
                    runCatching {
                        LightScreen.valueOf(screenName)
                    }.getOrNull()
                }
                ?: LightScreen.Overview

        activeProgramName =
            savedInstanceState
                ?.getString(KEY_ACTIVE_PROGRAM_NAME)
                .orEmpty()
                .ifBlank {
                    DEFAULT_PROGRAM_NAME
                }
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
            LightScreen.Programs,
            LightScreen.DeviceSettings,
            LightScreen.QuickSetup,
            LightScreen.Presets -> {
                openOverview()
            }

            LightScreen.ProgramEditor -> {
                openPrograms()
            }
        }
    }

    fun openOverview() {
        activeScreen =
            LightScreen.Overview

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.lightControllerContainer,
                DeviceLightFragment.newInstance(
                    deviceId = deviceId
                )
            )
        }

        renderHeaderForOverview()
    }

    fun openManual() {
        activeScreen =
            LightScreen.Manual

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.lightControllerContainer,
                DeviceLightManualFragment.newInstance(
                    deviceId = deviceId
                )
            )
        }

        renderHeaderForManual()
    }

    fun openPrograms() {
        activeScreen =
            LightScreen.Programs

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.lightControllerContainer,
                DeviceLightProgramListFragment.newInstance(
                    deviceId = deviceId
                )
            )
        }

        renderHeaderForPrograms()
    }

    fun openProgramEditor(
        programName: String
    ) {
        activeScreen =
            LightScreen.ProgramEditor

        activeProgramName =
            programName.ifBlank {
                DEFAULT_PROGRAM_NAME
            }

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.lightControllerContainer,
                DeviceLightProgramEditorFragment.newInstance(
                    deviceId = deviceId,
                    programName = activeProgramName
                )
            )
        }

        renderHeaderForProgramEditor()
    }

    fun openDeviceSettings() {
        activeScreen =
            LightScreen.DeviceSettings

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.lightControllerContainer,
                DeviceLightDeviceSettingsFragment.newInstance(
                    deviceId = deviceId
                )
            )
        }

        renderHeaderForDeviceSettings()
    }

    fun openQuickSetup() {
        activeScreen =
            LightScreen.QuickSetup

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.lightControllerContainer,
                DeviceLightQuickSetupFragment.newInstance(
                    deviceId = deviceId
                )
            )
        }

        renderHeaderForQuickSetup()
    }

    fun openPresets() {
        activeScreen =
            LightScreen.Presets

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.lightControllerContainer,
                DeviceLightPresetsFragment.newInstance(
                    deviceId = deviceId
                )
            )
        }

        renderHeaderForPresets()
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

            LightScreen.ProgramEditor -> {
                renderHeaderForProgramEditor()
            }

            LightScreen.DeviceSettings -> {
                renderHeaderForDeviceSettings()
            }

            LightScreen.QuickSetup -> {
                renderHeaderForQuickSetup()
            }

            LightScreen.Presets -> {
                renderHeaderForPresets()
            }
        }
    }

    private fun renderHeaderForOverview() {
        chromeHost?.setDeviceHeader(
            title = deviceTitle,
            actions = listOf(
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_light_sync_24,
                    contentDescription = getString(
                        R.string.light_cd_sync
                    ),
                    onClick = {
                        currentOverviewFragment()
                            ?.onHeaderSyncClick()
                    }
                ),
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_light_settings_24,
                    contentDescription = getString(
                        R.string.light_cd_settings
                    ),
                    onClick = {
                        currentOverviewFragment()
                            ?.onHeaderSettingsClick()
                    }
                ),
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_light_more_vert_24,
                    contentDescription = getString(
                        R.string.light_cd_more
                    ),
                    onClick = {
                        currentOverviewFragment()
                            ?.onHeaderMoreClick()
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
            title = getString(
                R.string.light_manual_title
            ),
            actions = listOf(
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_light_sync_24,
                    contentDescription = getString(
                        R.string.light_cd_sync
                    ),
                    onClick = {
                        currentManualFragment()
                            ?.onHeaderSyncClick()
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
            title = getString(
                R.string.light_programs_title
            ),
            actions = listOf(
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_add_24,
                    contentDescription = getString(
                        R.string.light_add_program
                    ),
                    onClick = {
                        currentProgramListFragment()
                            ?.onHeaderAddClick()
                    }
                )
            ),
            onBackClick = {
                openOverview()
            }
        )
    }

    private fun renderHeaderForProgramEditor() {
        chromeHost?.setDeviceHeader(
            title = activeProgramName.ifBlank {
                DEFAULT_PROGRAM_NAME
            },
            actions = listOf(
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_light_program_24,
                    contentDescription = "Preview Program",
                    onClick = {
                        currentProgramEditorFragment()
                            ?.onHeaderPreviewClick()
                    }
                ),
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_check_24,
                    contentDescription = "Save Program",
                    onClick = {
                        currentProgramEditorFragment()
                            ?.onHeaderSaveClick()
                    }
                )
            ),
            onBackClick = {
                openPrograms()
            }
        )
    }

    private fun renderHeaderForDeviceSettings() {
        chromeHost?.setDeviceHeader(
            title = "Device Settings",
            actions = listOf(
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_light_sync_24,
                    contentDescription = getString(
                        R.string.light_cd_sync
                    ),
                    onClick = {
                        currentDeviceSettingsFragment()
                            ?.onHeaderSyncClick()
                    }
                )
            ),
            onBackClick = {
                openOverview()
            }
        )
    }

    private fun renderHeaderForQuickSetup() {
        chromeHost?.setDeviceHeader(
            title = "Quick Setup",
            actions = emptyList(),
            onBackClick = {
                openOverview()
            }
        )
    }

    private fun renderHeaderForPresets() {
        chromeHost?.setDeviceHeader(
            title = "Presets & Scenes",
            actions = listOf(
                DeviceHeaderAction(
                    iconRes = R.drawable.ic_add_24,
                    contentDescription = "Create Preset",
                    onClick = {
                        currentPresetsFragment()
                            ?.onHeaderCreatePresetClick()
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

    private fun currentProgramEditorFragment(): DeviceLightProgramEditorFragment? {
        return childFragmentManager.findFragmentById(
            R.id.lightControllerContainer
        ) as? DeviceLightProgramEditorFragment
    }

    private fun currentDeviceSettingsFragment(): DeviceLightDeviceSettingsFragment? {
        return childFragmentManager.findFragmentById(
            R.id.lightControllerContainer
        ) as? DeviceLightDeviceSettingsFragment
    }

    private fun currentQuickSetupFragment(): DeviceLightQuickSetupFragment? {
        return childFragmentManager.findFragmentById(
            R.id.lightControllerContainer
        ) as? DeviceLightQuickSetupFragment
    }

    private fun currentPresetsFragment(): DeviceLightPresetsFragment? {
        return childFragmentManager.findFragmentById(
            R.id.lightControllerContainer
        ) as? DeviceLightPresetsFragment
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

        outState.putString(
            KEY_ACTIVE_PROGRAM_NAME,
            activeProgramName
        )
    }

    private enum class LightScreen {
        Overview,
        Manual,
        Programs,
        ProgramEditor,
        DeviceSettings,
        QuickSetup,
        Presets
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_DEFAULT_DEVICE_TITLE = "defaultDeviceTitle"

        private const val KEY_ACTIVE_SCREEN = "activeScreen"
        private const val KEY_ACTIVE_PROGRAM_NAME = "activeProgramName"

        private const val DEFAULT_DEVICE_TITLE = "Device"
        private const val DEFAULT_PROGRAM_NAME = "New Program"

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