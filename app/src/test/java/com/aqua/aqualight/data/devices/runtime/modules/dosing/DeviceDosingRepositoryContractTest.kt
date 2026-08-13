package com.aqua.aqualight.data.devices.runtime.modules.dosing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingRepositoryContractTest {

    @Test
    fun `repository exposes global channel program config and reset operations`() {
        val source = repositorySource()

        listOf(
            "suspend fun requestStatus(",
            "suspend fun requestChannelStatus(",
            "suspend fun applyChannelConfig(",
            "suspend fun applyProgram(",
            "suspend fun saveProgram(",
            "suspend fun resetChannel("
        ).forEach { token -> assertTrue(source.contains(token)) }
    }

    @Test
    fun `revision guarded writes load authoritative channel baseline`() {
        val source = repositorySource()

        assertTrue(source.contains("withChannelBaseline("))
        assertTrue(source.contains("requestChannelStatus(deviceUid, key)"))
        assertTrue(source.contains("requiresStatusRefresh.not()"))
        assertTrue(source.contains("expectedRevision"))
    }

    @Test
    fun `slim status change performs authoritative channel refresh`() {
        val source = repositorySource()

        assertTrue(source.contains("internal suspend fun acceptStatusChange("))
        assertTrue(source.contains("DeviceDosingStatusParser.parseStatusChange(data)"))
        assertTrue(source.contains("requestChannelStatus(deviceUid, change.channelKey)"))
    }

    @Test
    fun `legacy schedule list CRUD is absent from repository`() {
        val source = repositorySource()

        listOf(
            "createSchedule(",
            "updateSchedule(",
            "deleteSchedule(",
            "mutateSchedules(",
            "DeviceDosingScheduleConfig",
            "DeviceDosingConfigApplyPayload"
        ).forEach { legacy -> assertFalse(source.contains(legacy)) }
    }

    @Test
    fun `repository never delegates Dose Pro to standalone Timer runtime`() {
        val source = repositorySource()

        assertFalse(source.contains("DeviceTimerRuntime"))
        assertFalse(source.contains("runtime.modules.timer"))
        assertTrue(source.contains("DeviceDosingRuntimeContract.Action.PROGRAM_APPLY"))
        assertTrue(source.contains("DeviceDosingRuntimeContract.Action.CHANNEL_RESET"))
    }

    private fun repositorySource(): String = File(
        repositoryRoot(),
        "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/dosing/repository/" +
            "DeviceDosingRuntimeRepository.kt"
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
