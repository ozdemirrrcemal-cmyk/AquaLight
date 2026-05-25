package com.aqua.aqualight.debug

import android.content.Context
import android.util.Log
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import kotlinx.coroutines.flow.first

object DebugDeviceSeeder {

    private const val TAG = "DebugDeviceSeeder"

    private const val DEBUG_TIMER_DEVICE_ID = 900_001L
    private const val DEBUG_TIMER_SERIAL = "DEBUG-TIMER-001"

    suspend fun seedIfNeeded(
        context: Context
    ) {
        val appContext = context.applicationContext
        val devicesManager = DevicesDataStoreManager.create(appContext)

        val currentDevices = devicesManager.devicesFlow.first()

        val timerDebugDeviceAlreadyExists = currentDevices.any { device ->
            device.id == DEBUG_TIMER_DEVICE_ID ||
                device.serial == DEBUG_TIMER_SERIAL
        }

        if (timerDebugDeviceAlreadyExists) {
            Log.d(
                TAG,
                "Debug Timer device already exists. Seed skipped."
            )
            return
        }

        devicesManager.addDevice(
            id = DEBUG_TIMER_DEVICE_ID,

            aquaName = "AquaLight Timer",
            name = "Timer",
            ip = "0.0.0.0",
            serial = DEBUG_TIMER_SERIAL,
            firmwareBuild = "debug",

            deviceType = AquaDeviceType.AQUA_TIMER_001,

            udpVersion = 20240813,

            tabLight = false,
            tabTimer = true,
            tabTemperature = false,

            productId = "AQUA_TIMER_001",
            productFamily = "timer",
            productModel = "Aqua Timer 001",
            hardwareRevision = "debug",
            firmwareVersion = "debug",
            apiVersion = 1,

            channelCount = 4,
            sensorCount = 0,

            supportedFeatures = setOf(
                "timer",
                "schedule",
                "multi_channel_timer"
            ),

            supportedScreens = setOf(
                "timer"
            )
        )

        Log.d(
            TAG,
            "Debug Timer device seeded."
        )
    }
}