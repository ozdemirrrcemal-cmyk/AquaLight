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

    @Test
    fun `dosing ui never reaches data or runtime modules directly`() {
        dosingSources().forEach { source ->
            assertFalse(source.contains("com.aqua.aqualight.data.devices"))
            assertFalse(source.contains("runtime.modules"))
            assertFalse(source.contains("DevicesRepository"))
            assertFalse(source.contains("DeviceDosingRuntimeRepository"))
        }
    }

    @Test
    fun `calibration lifecycle safety is owned by application boundary`() {
        val workflow = source(
            DOSING_SOURCE_ROOT + "channel/calibration/DosingCalibrationWorkflow.kt"
        )
        val applicationBoundary = source(
            "app/src/main/java/com/aqua/aqualight/application/devices/" +
                "DeviceDosingCalibrationOperations.kt"
        )

        assertTrue(workflow.contains("operations.exitSafely("))
        assertTrue(workflow.contains("operations.primeSafetyStop("))
        assertTrue(workflow.contains("operations.awaitPrimeSafetyStop("))
        assertFalse(workflow.contains("stopVerificationDose("))
        assertFalse(workflow.contains("DeviceDosingCalibrationSessionPhase"))
        assertFalse(workflow.contains("delay("))
        assertFalse(workflow.contains("PRIME_SAFETY_TIMEOUT_MS"))

        assertTrue(applicationBoundary.contains("suspend fun exitSafely("))
        assertTrue(applicationBoundary.contains("suspend fun awaitPrimeSafetyStop("))
        assertTrue(applicationBoundary.contains("delay(constraints.primeSafetyTimeoutMs)"))
        assertTrue(applicationBoundary.contains("verificationDoseStarted"))
        assertTrue(applicationBoundary.contains("DeviceDosingCalibrationSessionPhase.IDLE"))
        assertTrue(applicationBoundary.contains("primeSafetyTimeoutMs"))
    }

    @Test
    fun `schedule business validity is delegated to application policy`() {
        val amount = source(
            DOSING_SOURCE_ROOT + "channel/schedule/DeviceDosingScheduleAmountContract.kt"
        )
        val custom = source(
            DOSING_SOURCE_ROOT +
                "channel/schedule/custom/DeviceDosingCustomScheduleContract.kt"
        )
        val timer = source(
            DOSING_SOURCE_ROOT +
                "channel/schedule/timer/DeviceDosingTimerScheduleContract.kt"
        )
        val policy = source(
            "app/src/main/java/com/aqua/aqualight/application/devices/" +
                "DeviceDosingScheduleDraftPolicy.kt"
        )

        assertTrue(amount.contains("DeviceDosingAmountDraftPolicy"))
        assertTrue(custom.contains("DeviceDosingCustomScheduleDraftPolicy"))
        assertTrue(timer.contains("DeviceDosingTimerScheduleDraftPolicy"))
        assertFalse(custom.contains("zipWithNext()"))
        assertFalse(timer.contains("Math.addExact"))
        assertFalse(custom.contains("const val MAX_DOSES_PER_DAY = 24"))
        assertFalse(timer.contains("const val MAX_DOSES_PER_DAY = 24"))

        assertTrue(policy.contains("const val MAX_DOSES_PER_DAY = 24"))
        assertTrue(policy.contains("periodsOverlap"))
        assertTrue(policy.contains("hasDuplicateTime"))
        assertTrue(policy.contains("Math.addExact"))
    }

    @Test
    fun `dosing child fragments delegate feature state to viewmodels`() {
        val plan = source(DOSING_SOURCE_ROOT + "channel/plan/DeviceDosingPlanFragment.kt")
        val reservoir = source(
            DOSING_SOURCE_ROOT + "channel/reservoir/DeviceDosingReservoirFragment.kt"
        )
        val detail = source(
            DOSING_SOURCE_ROOT + "channel/detail/DeviceDosingChannelDetailFragment.kt"
        )

        assertFragmentUsesStateOwner(plan, "DeviceDosingPlanViewModel")
        assertFragmentUsesStateOwner(reservoir, "DeviceDosingReservoirViewModel")
        assertFragmentUsesStateOwner(detail, "DeviceDosingChannelDetailViewModel")
    }

    private fun assertFragmentUsesStateOwner(source: String, viewModelName: String) {
        assertTrue(source.contains(viewModelName))
        assertTrue(source.contains("collectAsStateWithLifecycle"))
        assertTrue(source.contains("defaultViewModelFactory"))
        assertFalse(source.contains("mutableStateOf"))
        assertFalse(source.contains("mutableDoubleStateOf"))
    }

    private fun dosingSources(): List<String> = File(repositoryRoot, DOSING_SOURCE_ROOT)
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
        const val DOSING_SOURCE_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/"
        const val APPLICATION_DEVICE_SLOTS =
            "app/src/main/java/com/aqua/aqualight/application/devices/DeviceChannelSlots.kt"
    }
}
