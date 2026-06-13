package com.aqua.aqualight.debug

import android.content.Context
import android.util.Log
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaProductKey
import kotlinx.coroutines.flow.first

object DebugDeviceSeeder {

    private const val TAG = "DebugDeviceSeeder"

    private const val DEBUG_DOSING_DEVICE_ID = 900_003L
    private const val DEBUG_DOSING_SERIAL = "DEBUG-DOSING-LOCAL-001"
    private const val DEBUG_DOSING_IP = "127.0.0.1:8081"

    private const val DEBUG_LIGHT_DEVICE_ID = 900_004L
    private const val DEBUG_LIGHT_SERIAL = "DEBUG-LIGHT-LOCAL-001"
    private const val DEBUG_LIGHT_IP = "127.0.0.1:8082"

    suspend fun seedIfNeeded(
        context: Context
    ) {
        val appContext = context.applicationContext

        val devicesManager =
            DevicesDataStoreManager.create(appContext)

        val currentDevices =
            devicesManager.devicesFlow.first()

        seedDosingDeviceIfNeeded(
            devicesManager = devicesManager,
            currentDevices = currentDevices
        )

        seedLightDeviceIfNeeded(
            devicesManager = devicesManager,
            currentDevices = currentDevices
        )
    }

    private suspend fun seedDosingDeviceIfNeeded(
        devicesManager: DevicesDataStoreManager,
        currentDevices: List<DevicesDataStoreManager.DeviceInfo>
    ) {
        val alreadyExists =
            currentDevices.any { device ->
                device.id == DEBUG_DOSING_DEVICE_ID ||
                    device.serial == DEBUG_DOSING_SERIAL
            }

        if (alreadyExists) {
            Log.d(
                TAG,
                "Local debug dosing device already exists. Seed skipped."
            )
            return
        }

        devicesManager.addDevice(
            id = DEBUG_DOSING_DEVICE_ID,

            aquaName = "AquaDose Local",
            name = "DosePro 4 Local",
            ip = DEBUG_DOSING_IP,
            serial = DEBUG_DOSING_SERIAL,
            firmwareBuild = "debug-local",

            productKey = AquaProductKey.DOSING_DOSE_PRO_4,
            category = AquaDeviceCategory.DOSING,
            setupCode = AquaProductKey.DOSING_DOSE_PRO_4.setupCode,

            udpVersion = 20240813,

            tabLight = false,
            tabTimer = false,
            tabTemperature = false,

            productId = AquaProductKey.DOSING_DOSE_PRO_4.productId,
            productFamily = "AquaDose",
            productLine = "Dosing",
            productModel = "DosePro 4",
            displayName = "DosePro 4 Local",
            hardwareRevision = "debug",
            firmwareVersion = "debug-local",
            protocolVersion = 1,

            channelCount = 4,
            sensorCount = 0,

            supportedFeatures = setOf(
                "dosing",
                "ml_dosing",
                "pump_calibration",
                "manual_run",
                "schedule",
                "reservoir_tracking"
            ),

            supportedScreens = setOf(
                "dosing",
                "dosing_control",
                "dosing_channels",
                "dosing_calibration",
                "dosing_schedule",
                "dosing_reservoir"
            )
        )

        Log.d(
            TAG,
            "Local debug dosing device seeded with IP: $DEBUG_DOSING_IP"
        )
    }

    private suspend fun seedLightDeviceIfNeeded(
        devicesManager: DevicesDataStoreManager,
        currentDevices: List<DevicesDataStoreManager.DeviceInfo>
    ) {
        val alreadyExists =
            currentDevices.any { device ->
                device.id == DEBUG_LIGHT_DEVICE_ID ||
                    device.serial == DEBUG_LIGHT_SERIAL
            }

        if (alreadyExists) {
            Log.d(
                TAG,
                "Local debug light device already exists. Seed skipped."
            )
            return
        }

        devicesManager.addDevice(
            id = DEBUG_LIGHT_DEVICE_ID,

            aquaName = "AquaLight Local",
            name = "WRGB Light Local",
            ip = DEBUG_LIGHT_IP,
            serial = DEBUG_LIGHT_SERIAL,
            firmwareBuild = "debug-local",

            productKey = AquaProductKey.LIGHT_WRGB_PRO_ELITE,
            category = AquaDeviceCategory.LIGHT,
            setupCode = AquaProductKey.LIGHT_WRGB_PRO_ELITE.setupCode,

            udpVersion = 20240813,

            tabLight = true,
            tabTimer = true,
            tabTemperature = true,

            productId = AquaProductKey.LIGHT_WRGB_PRO_ELITE.productId,
            productFamily = "AquaLight",
            productLine = "WRGB",
            productModel = "WRGB Pro Elite",
            displayName = "WRGB Pro Elite Local",
            hardwareRevision = "debug",
            firmwareVersion = "debug-local",
            protocolVersion = 1,

            channelCount = 4,
            sensorCount = 1,

            supportedFeatures = setOf(
                "lighting",
                "manual_light_control",
                "rgbw_channels",
                "light_programs",
                "temporary_modes",
                "presets",
                "acclimation",
                "fan_control",
                "temperature_protection"
            ),

            supportedScreens = setOf(
                "light",
                "lighting",
                "light_manual",
                "light_programs",
                "light_program_editor",
                "light_quick_setup",
                "light_presets",
                "light_device_settings"
            )
        )

        Log.d(
            TAG,
            "Local debug light device seeded with IP: $DEBUG_LIGHT_IP"
        )
    }
}