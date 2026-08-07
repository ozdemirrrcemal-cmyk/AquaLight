package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareReleaseNoteItem
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareReleaseNotes
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareRuntimeContract
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareReleaseLocaleIntegrationTest {

    @Test
    fun `ota release notes resolve the explicit application language`() {
        val releaseNotes = DeviceFirmwareReleaseNotes(
            schema = DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA,
            defaultLocale = DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE,
            items = listOf(
                DeviceFirmwareReleaseNoteItem(
                    tr = "Bağlantı hatası giderildi.",
                    en = "Connection error fixed."
                )
            )
        )

        val english = releaseNotes.resolve(listOf("en-US"))
        val turkish = releaseNotes.resolve(listOf("tr-TR"))

        assertEquals("en", english.localeTag)
        assertEquals(listOf("Connection error fixed."), english.changes)
        assertEquals("tr", turkish.localeTag)
        assertEquals(listOf("Bağlantı hatası giderildi."), turkish.changes)
    }

    @Test
    fun `production ota planner uses the authoritative AquaLight application language`() {
        val moduleProvider = productionSource(
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/" +
                "DeviceRuntimeModuleProvider.kt"
        )
        val operations = productionSource(
            "app/src/main/java/com/aqua/aqualight/data/devices/" +
                "DefaultDeviceFirmwareUpdateOperations.kt"
        )

        assertTrue(moduleProvider.contains("DeviceFirmwareUpdatePlanner {"))
        assertTrue(moduleProvider.contains("listOf(AppLanguageController.current())"))
        assertTrue(operations.contains("preferredLocaleTag = AppLanguageController.current()"))
    }

    @Test
    fun `locale change refreshes prepared release content without waiting for freshness`() {
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(
            nowMillis = { 10_000L },
            freshnessMillis = 60_000L
        )
        val deviceUid = DeviceUid("device-locale")
        val state = DeviceOtaState.UpdateAvailable(
            preparedPlan(
                deviceUid = deviceUid.value,
                releaseContent = releaseContent("tr", "Bağlantı hatası giderildi.")
            )
        )

        policy.recordResult(deviceUid, Result.success(state))

        assertFalse(
            policy.shouldRefresh(
                deviceUid = deviceUid,
                state = state,
                preferredLocaleTag = "tr"
            )
        )
        assertTrue(
            policy.shouldRefresh(
                deviceUid = deviceUid,
                state = state,
                preferredLocaleTag = "en"
            )
        )
    }

    @Test
    fun `locale change never interrupts an active ota operation`() {
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(nowMillis = { 20_000L })
        val deviceUid = DeviceUid("device-active-locale")
        val active = DeviceOtaState.InProgress(
            deviceUid = deviceUid.value,
            targetVersion = "1.0.5",
            phase = DeviceOtaProgressPhase.DOWNLOADING,
            progressPermille = 500,
            bytesWritten = 512L,
            contentLength = 1_024L,
            releaseContent = releaseContent("tr", "Bağlantı hatası giderildi.")
        )

        assertFalse(
            policy.shouldRefresh(
                deviceUid = deviceUid,
                state = active,
                preferredLocaleTag = "en"
            )
        )
    }

    private fun preparedPlan(
        deviceUid: String,
        releaseContent: DeviceFirmwareReleaseContent
    ) = PreparedDeviceFirmwareUpdate(
        deviceUid = deviceUid,
        currentVersion = "1.0.4",
        targetVersion = "1.0.5",
        channel = "stable",
        environment = "light_wrgb_pro_elite",
        productKey = "LIGHT_WRGB_PRO_ELITE",
        productId = "com.aqualight.light.wrgb_pro_elite",
        model = "wrgb_pro_elite_120",
        hardwareRevision = "2.0",
        displayName = "WRGB Pro Elite 120",
        filename = "AquaLight-light_wrgb_pro_elite-v1.0.5-ota.bin",
        downloadUrl = "https://example.invalid/firmware.bin",
        sha256 = "a".repeat(64),
        sizeBytes = 1_024,
        applyNow = true,
        releaseContent = releaseContent
    )

    private fun releaseContent(localeTag: String, text: String) = DeviceFirmwareReleaseContent(
        localeTag = localeTag,
        title = "",
        summary = "",
        changes = listOf(text),
        warnings = emptyList(),
        mandatory = false
    )

    private fun productionSource(relativePath: String): String {
        return File(repositoryRoot(), relativePath).readText()
    }

    private fun repositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
