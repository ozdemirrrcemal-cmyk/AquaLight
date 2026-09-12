package com.aqua.aqualight.ui.tabs.devices.detail.timer

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerUiLayerBoundaryTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `Timer ui never reaches device data or runtime modules directly`() {
        timerUiSources().forEach { source ->
            assertFalse(source.contains("com.aqua.aqualight.data.devices"))
            assertFalse(source.contains("runtime.modules.timer"))
            assertFalse(source.contains("DevicesRepository"))
            assertFalse(source.contains("DeviceTimerRuntimeRepository"))
            assertFalse(source.contains("DeviceTimerRuntimeStateStore"))
        }
    }

    @Test
    fun `Timer application contract remains firmware and data independent`() {
        applicationTimerSources().forEach { source ->
            assertFalse(source.contains("com.aqua.aqualight.data"))
            assertFalse(source.contains("runtime.modules.timer"))
            assertFalse(source.contains("DeviceTimerStatus"))
        }
    }

    @Test
    fun `owner graph shares one stateless Timer adapter over the central runtime owner`() {
        val ownerGraph = source(
            "app/src/main/java/com/aqua/aqualight/composition/OwnerDependencyGraph.kt"
        )
        val ownerFactory = source(
            "app/src/main/java/com/aqua/aqualight/composition/OwnerViewModelFactory.kt"
        )
        val adapter = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/timer/" +
                "DefaultDeviceTimerControlOperations.kt"
        )
        val provider = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/" +
                "DeviceRuntimeModuleProvider.kt"
        )

        assertTrue(
            ownerGraph.contains(
                "val timerControlOperations = DefaultDeviceTimerControlOperations("
            )
        )
        assertTrue(ownerGraph.contains("timerControlOperations = timerControlOperations"))
        assertTrue(ownerFactory.contains("timerControlOperations = graph.timerControlOperations"))
        assertTrue(ownerFactory.contains("DeviceTimerProgramViewModel(graph.timerControlOperations)"))
        assertTrue(ownerFactory.contains("DeviceTimerChannelViewModel(graph.timerControlOperations)"))
        assertFalse(adapter.contains("MutableStateFlow"))
        assertFalse(adapter.contains("DeviceTimerRuntimeStateStore("))
        assertTrue(provider.contains("private val timerStateStore = DeviceTimerRuntimeStateStore()"))
        assertTrue(
            provider.contains(
                "val timer = DeviceTimerRuntimeRepository(commandGateway, timerStateStore"
            )
        )
    }

    private fun timerUiSources(): List<String> = File(repositoryRoot, TIMER_UI_SOURCE_ROOT)
        .walkTopDown()
        .filter { file -> file.isFile && file.extension == "kt" }
        .map(File::readText)
        .toList()

    private fun applicationTimerSources(): List<String> =
        File(repositoryRoot, APPLICATION_TIMER_SOURCE_ROOT)
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .map(File::readText)
            .toList()

    private fun source(relativePath: String): String = File(repositoryRoot, relativePath).readText()

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }

    private companion object {
        const val TIMER_UI_SOURCE_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/timer/"
        const val APPLICATION_TIMER_SOURCE_ROOT =
            "app/src/main/java/com/aqua/aqualight/application/devices/timer/"
    }
}
