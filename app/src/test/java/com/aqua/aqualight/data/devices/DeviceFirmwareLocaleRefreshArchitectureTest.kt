package com.aqua.aqualight.data.devices

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareLocaleRefreshArchitectureTest {

    @Test
    fun `locale refresh remains inside the owner scoped ota adapter`() {
        val source = productionSource(
            "app/src/main/java/com/aqua/aqualight/data/devices/" +
                "DefaultDeviceFirmwareUpdateOperations.kt"
        )

        assertTrue(source.contains("AppLanguageController.languageChanges"))
        assertTrue(source.contains(".drop(1)"))
        assertTrue(source.contains("publisherJobs.keys.toList().forEach"))
        assertTrue(source.contains("states.value.requiresReleaseContentRelocalization("))
        assertTrue(source.contains("refreshAvailabilityIfStale("))
        assertTrue(source.contains("manifestUrl = DEVICE_FIRMWARE_MANIFEST_URL"))
        assertFalse(source.contains("WorkManager"))
        assertFalse(source.contains("DeviceFirmwareAvailabilityWorker"))
    }

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
