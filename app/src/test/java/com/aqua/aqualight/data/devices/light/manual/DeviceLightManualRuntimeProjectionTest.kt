package com.aqua.aqualight.data.devices.light.manual

import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannel
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMode
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualProtectionKind
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightManualRuntimeProjectionTest {

    @Test
    fun `manual snapshot projects firmware channel metadata in firmware order`() {
        val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())

        val snapshot = status.toManualSnapshot(
            uid = DeviceUid(DEVICE_UID),
            firmwareWriteAuthoritative = false
        )

        assertEquals(
            listOf(
                DeviceLightManualChannel.RED,
                DeviceLightManualChannel.GREEN,
                DeviceLightManualChannel.BLUE,
                DeviceLightManualChannel.WHITE
            ),
            snapshot.channelDescriptors.map { descriptor -> descriptor.channel }
        )
        assertEquals(
            status.channels.map { descriptor -> descriptor.displayName },
            snapshot.channelDescriptors.map { descriptor -> descriptor.displayName }
        )
        assertEquals(
            status.channels.map { descriptor -> descriptor.displayColorRgb },
            snapshot.channelDescriptors.map { descriptor -> descriptor.displayColorRgb }
        )
        assertEquals(
            status.manual.scene.percents.values.toList(),
            snapshot.scene.channels.values.toList()
        )
        val displayRgb = checkNotNull(status.color.displayRgb)
        assertEquals(
            (displayRgb.red shl RED_SHIFT) or
                (displayRgb.green shl GREEN_SHIFT) or
                displayRgb.blue,
            snapshot.effectiveOutputDisplayColorRgb
        )
        assertEquals(DeviceLightManualMode.MANUAL, snapshot.activeMode)
        assertFalse(snapshot.firmwareWriteAuthoritative)
    }

    @Test
    fun `fixture base draw is excluded when effective LED power is zero`() {
        val data = DeviceLightRuntimeFixtures.status()
        val zeroScene = JSONObject()
            .put("redPercent", 0)
            .put("greenPercent", 0)
            .put("bluePercent", 0)
            .put("whitePercent", 0)
        data.put("requested", JSONObject(zeroScene.toString()))
            .put("effective", JSONObject(zeroScene.toString()))
            .put("outputActive", false)
            .put("outputReason", "ALL_CHANNELS_ZERO")
        data.getJSONObject("manual").put("scene", JSONObject(zeroScene.toString()))
        data.getJSONObject("power")
            .put("estimatedFixturePowerW", FIXTURE_BASE_POWER_WATTS)
            .put("estimatedLedPowerW", 0.0)
            .put("ratio", FIXTURE_BASE_POWER_WATTS / FIXTURE_POWER_LIMIT_WATTS)
        data.getJSONObject("color").put(
            "displayRgb",
            JSONObject().put("red", 0).put("green", 0).put("blue", 0)
        )

        val snapshot = DeviceLightStatusParser.parse(data).toManualSnapshot(DeviceUid(DEVICE_UID))

        assertTrue(snapshot.scene.channels.values.all { percent -> percent == 0 })
        assertEquals(0, snapshot.estimatedLedPowerWatts)
        assertEquals(0f, snapshot.estimatedLedPowerRatio ?: Float.NaN, FLOAT_TOLERANCE)
    }

    @Test
    fun `gauge uses effective LED watts over hard LED limit`() {
        val data = DeviceLightRuntimeFixtures.status()
        data.getJSONObject("power")
            .put("estimatedLedPowerW", EFFECTIVE_LED_POWER_WATTS)
            .put("estimatedFixturePowerW", FIXTURE_POWER_WATTS)
            .put("hardLedPowerLimitW", LED_POWER_LIMIT_WATTS)
            .put("ratio", FIRMWARE_FIXTURE_RATIO)

        val snapshot = DeviceLightStatusParser.parse(data).toManualSnapshot(DeviceUid(DEVICE_UID))

        assertEquals(EFFECTIVE_LED_POWER_WATTS.toInt(), snapshot.estimatedLedPowerWatts)
        assertEquals(
            (EFFECTIVE_LED_POWER_WATTS / LED_POWER_LIMIT_WATTS).toFloat(),
            snapshot.estimatedLedPowerRatio ?: Float.NaN,
            FLOAT_TOLERANCE
        )
    }

    @Test
    fun `protection-reduced effective LED power drives gauge`() {
        val data = DeviceLightRuntimeFixtures.status()
        data.getJSONObject("power")
            .put("estimatedLedPowerW", PROTECTED_LED_POWER_WATTS)
        data.getJSONObject("scales").put("thermal", PROTECTED_SCALE_PERMILLE)

        val snapshot = DeviceLightStatusParser.parse(data).toManualSnapshot(DeviceUid(DEVICE_UID))

        assertEquals(PROTECTED_LED_POWER_WATTS.toInt(), snapshot.estimatedLedPowerWatts)
        assertEquals(
            DeviceLightManualProtectionKind.THERMAL_LIMITED,
            snapshot.protection?.kind
        )
        assertEquals(
            PROTECTED_SCALE_PERMILLE / PERMILLE_PER_PERCENT,
            snapshot.protection?.effectivePercent
        )
    }

    @Test
    fun `active automatic and custom modes remain explicit in manual snapshot`() {
        val parsed = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())

        assertEquals(
            DeviceLightManualMode.AUTOMATIC,
            parsed.copy(mode = DeviceLightMode.AUTO).toManualSnapshot(DeviceUid(DEVICE_UID)).activeMode
        )
        assertEquals(
            DeviceLightManualMode.CUSTOM,
            parsed.copy(mode = DeviceLightMode.CUSTOM).toManualSnapshot(DeviceUid(DEVICE_UID)).activeMode
        )
    }

    private companion object {
        const val DEVICE_UID = "manual-projection-light"
        const val FIXTURE_BASE_POWER_WATTS = 9.39
        const val FIXTURE_POWER_WATTS = 76.0
        const val FIXTURE_POWER_LIMIT_WATTS = 150.0
        const val EFFECTIVE_LED_POWER_WATTS = 62.0
        const val PROTECTED_LED_POWER_WATTS = 31.0
        const val LED_POWER_LIMIT_WATTS = 130.0
        const val FIRMWARE_FIXTURE_RATIO = 0.5
        const val PROTECTED_SCALE_PERMILLE = 500
        const val PERMILLE_PER_PERCENT = 10
        const val FLOAT_TOLERANCE = 0.0001f
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
    }
}
