package com.aqua.aqualight.debug

import android.content.Context
import android.util.Log
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import kotlinx.coroutines.flow.first

object DebugDeviceSeeder {

    private const val TAG = "DebugDeviceSeeder"

    private const val DEBUG_DOSING_DEVICE_ID = 900_002L
    private const val DEBUG_DOSING_SERIAL = "DEBUG-DOSING-001"

    suspend fun seedIfNeeded(
        context: Context
    ) {
        val appContext = context.applicationContext
        val devicesManager = DevicesDataStoreManager.create(
            appContext
        )

        val currentDevices = devicesManager.devicesFlow.first()

        val dosingDebugDeviceAlreadyExists = currentDevices.any { device ->
            device.id == DEBUG_DOSING_DEVICE_ID ||
                device.serial == DEBUG_DOSING_SERIAL
        }

        if (dosingDebugDeviceAlreadyExists) {
            Log.d(
                TAG,
                "Debug dosing device already exists. Seed skipped."
            )

            return
        }

        devicesManager.addDevice(
            id = DEBUG_DOSING_DEVICE_ID,

            aquaName = "AquaDose",
            name = "DosePro 4",
            ip = "0.0.0.0",
            serial = DEBUG_DOSING_SERIAL,
            firmwareBuild = "debug",

            deviceType = AquaDeviceType.AQUA_DOSE_001,

            udpVersion = 20240813,

            tabLight = false,
            tabTimer = false,
            tabTemperature = false,

            productId = "aquadose.001",
            productFamily = "AquaDose",
            productModel = "DosePro 4",
            hardwareRevision = "debug",
            firmwareVersion = "debug",
            apiVersion = 1,

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
            "Debug dosing device seeded."
        )
    }
}