package com.aqua.aqualight.data.devices.dosing.v1

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1ReconciliationArchitectureTest {

    @Test
    fun `events mutations and refreshes share one per channel gate`() {
        val adapter = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/dosing/v1/DeviceDosingV1StateAdapter.kt"
        )
        val events = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/dosing/v1/DeviceDosingV1EventCoordinator.kt"
        )

        assertTrue(adapter.contains("private val operationGate = DeviceDosingV1ChannelOperationGate()"))
        assertTrue(adapter.countOccurrences("operationGate = operationGate") >= 3)
        assertTrue(events.contains("operationGate.withChannel(address)"))
        assertTrue(events.contains("refreshCoordinator.refreshWithinGate(address)"))
        assertFalse(events.contains("DeviceDosingV1InvalidationDisposition.APPLIED -> refreshCoordinator.refresh(address)"))
    }

    @Test
    fun `navigation waits for central authority under global loading`() {
        val navigation = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/dosing/" +
                "DefaultDeviceDosingChannelNavigationOperations.kt"
        )
        val root = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/root/" +
                "DeviceDosingRootViewModel.kt"
        )
        val fragment = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/root/" +
                "DeviceDosingRootFragment.kt"
        )

        assertTrue(navigation.contains("channelOperations.awaitAuthoritative("))
        assertTrue(root.contains("channelNavigationInProgress"))
        assertTrue(root.contains("channelNavigationJob?.isActive == true"))
        assertTrue(fragment.contains("setFragmentGlobalLoading(state.channelNavigationInProgress)"))
    }

    private fun String.countOccurrences(token: String): Int =
        windowed(size = token.length, step = 1, partialWindows = false).count { it == token }

    private fun source(relativePath: String): String = File(repositoryRoot(), relativePath).readText()

    private fun repositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
