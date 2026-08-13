package com.aqua.aqualight.data.devices.runtime.modules.dosing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingTypedEventReducerTest {

    @Test
    fun `snapshot event is treated as slim change notification not full status`() {
        val source = reducerSource()

        assertTrue(source.contains("refreshStatusChange(event.deviceUid, payload.data)"))
        assertFalse(source.contains("DeviceDosingStatusParser.parseGlobal(payload.data)"))
        assertFalse(source.contains("DeviceDosingStatusParser.parseChannel(payload.data)"))
    }

    @Test
    fun `command result events still reduce exact canonical mutations`() {
        val source = reducerSource()

        listOf(
            "DeviceDosingMutationParser.parseChannelConfigApply",
            "DeviceDosingMutationParser.parseProgramApply",
            "DeviceDosingMutationParser.parseChannelReset",
            "DeviceDosingMutationParser.parsePrimeStart",
            "DeviceDosingMutationParser.parseCalibrationConfirm",
            "DeviceDosingMutationParser.parseDoseNow",
            "DeviceDosingMutationParser.parseReservoirRefill"
        ).forEach { token -> assertTrue(source.contains(token)) }
    }

    @Test
    fun `unknown command actions are ignored and wrong module is validated`() {
        val source = reducerSource()

        assertTrue(source.contains("require(payload.commandModule == DeviceDosingRuntimeContract.MODULE)"))
        assertTrue(source.contains("else -> null"))
    }

    private fun reducerSource(): String = File(
        repositoryRoot(),
        "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/dosing/events/" +
            "DeviceDosingTypedEventReducer.kt"
    ).readText()

    private fun repositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
