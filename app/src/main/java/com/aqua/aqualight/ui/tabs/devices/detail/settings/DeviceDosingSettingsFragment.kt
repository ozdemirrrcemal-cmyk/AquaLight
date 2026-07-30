package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceDosingSettingsFragment : DeviceFamilySettingsFragment(DOSING_SETTINGS_COPY) {

    private val args: DeviceDosingSettingsFragmentArgs by navArgs()

    override val deviceUid: String
        get() = args.deviceUid
}

private val DOSING_SETTINGS_COPY = DeviceFamilySettingsCopy(
    screenTitleRes = R.string.device_dosing_settings_title,
    information = DeviceInformationSettingsCopy(
        sectionTitleRes = R.string.device_dosing_settings_device_information_section,
        deviceNameLabelRes = R.string.device_dosing_settings_device_name_label,
        editDeviceNameDescriptionRes = R.string.device_dosing_settings_edit_device_name_description,
        serialNumberLabelRes = R.string.device_dosing_settings_serial_number_label,
        hardwareRevisionLabelRes = R.string.device_dosing_settings_hardware_revision_label
    ),
    software = DeviceSoftwareSettingsCopy(
        sectionTitleRes = R.string.device_dosing_settings_software_section,
        firmwareVersionLabelRes = R.string.device_dosing_settings_firmware_version_label,
        checkForUpdatesActionRes = R.string.device_dosing_settings_check_updates_action
    ),
    unavailableValueRes = R.string.device_dosing_settings_unavailable_value
)
