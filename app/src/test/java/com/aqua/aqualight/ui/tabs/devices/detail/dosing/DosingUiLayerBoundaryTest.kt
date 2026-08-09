package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingUiLayerBoundaryTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `catalog structural validity stays outside dosing ui`() {
        val screen = source(DOSING_SOURCE_ROOT + "DosingCatalogScreen.kt")
        val catalogSlots = source(APPLICATION_DEVICE_SLOTS)

        assertFalse(screen.contains("exactDosingChannelsOrEmpty"))
        assertFalse(screen.contains("uniqueSlotIds"))
        assertFalse(screen.contains("uniqueWireKeys"))
        assertFalse(screen.contains("expectedPositions"))

        assertTrue(catalogSlots.contains("requireContiguous(dosingChannels"))
        assertTrue(catalogSlots.contains("Device channel slot ids must be unique inside one product."))
        assertTrue(
            catalogSlots.contains("Addressable channel wire keys must be unique inside one product.")
        )
    }

    @Test
    fun `dosing presentation models do not own domain validity or firmware identity`() {
        val models = source(DOSING_SOURCE_ROOT + "DosingChannelCardModels.kt")

        assertFalse(models.contains("require(slotId"))
        assertFalse(models.contains("require(channelNumber"))
        assertFalse(models.contains("require(displayName"))
        assertFalse(models.contains("selectedDays.distinct()"))
        assertFalse(models.contains("require(dailyDoseMl"))
        assertFalse(models.contains("require(deliveredTodayMl"))
        assertFalse(models.contains("require(doseMilestonesMl"))
        assertFalse(models.contains("val wireKey: String"))
        assertFalse(models.contains("wireKey = wireKey.value"))

        assertTrue(models.contains("DeviceDosingChannelSlot.toInitialDosingChannelCardUiState"))
    }

    @Test
    fun `channel navigation stays behind central catalog runtime and route boundaries`() {
        val rootFragment = source(DOSING_SOURCE_ROOT + "DeviceDosingRootFragment.kt")
        val operations = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/" +
                "DefaultDeviceDosingChannelNavigationOperations.kt"
        )
        val navigator = source(
            "app/src/main/java/com/aqua/aqualight/ui/navigation/AppRouteNavigator.kt"
        )
        val appGraph = source("app/src/main/res/navigation/nav_app.xml")

        assertTrue(rootFragment.contains("AppRouteNavigator.openDosingChannel"))
        assertFalse(rootFragment.contains("DeviceDosingChannelDetailFragmentArgs"))
        assertFalse(rootFragment.contains("DeviceDosingChannelCalibrationFragmentArgs"))
        assertTrue(operations.contains("toDeviceRootSnapshot()"))
        assertTrue(operations.contains("DefaultDeviceMenuAccessOperations.create"))
        assertTrue(operations.contains("runtimePort.requestStatus(context.uid)"))
        assertTrue(
            operations.contains("devicesRepository.runtimeModules()?.dosing?.requestStatus(deviceUid)")
        )
        assertTrue(operations.contains("DeviceDosingChannelDestinationPolicy.resolve"))
        assertTrue(navigator.contains("fun openDosingChannel("))
        assertTrue(appGraph.contains("deviceDosingChannelCalibrationFragment"))
        assertTrue(appGraph.contains("deviceDosingChannelDetailFragment"))
    }

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
        const val DOSING_SOURCE_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/"
        const val APPLICATION_DEVICE_SLOTS =
            "app/src/main/java/com/aqua/aqualight/application/devices/DeviceChannelSlots.kt"
    }
}
