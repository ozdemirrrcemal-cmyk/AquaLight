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
    fun `dosing presentation models do not own firmware identity`() {
        val models = source(DOSING_SOURCE_ROOT + "DosingChannelCardModels.kt")

        assertFalse(models.contains("val wireKey: String"))
        assertFalse(models.contains("wireKey = wireKey.value"))
        assertTrue(models.contains("DeviceDosingChannelSlot.toInitialDosingChannelCardUiState"))
    }

    @Test
    fun `channel navigation stays behind central catalog runtime and route boundaries`() {
        val rootFragment = source(DOSING_SOURCE_ROOT + "DeviceDosingRootFragment.kt")
        val operations = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/dosing/" +
                "DefaultDeviceDosingChannelNavigationOperations.kt"
        )
        val navigator = source("app/src/main/java/com/aqua/aqualight/ui/navigation/AppRouteNavigator.kt")
        val appGraph = source("app/src/main/res/navigation/nav_app.xml")

        assertTrue(rootFragment.contains("AppRouteNavigator.openDosingChannel"))
        assertFalse(rootFragment.contains("DeviceDosingChannelDetailFragmentArgs"))
        assertFalse(rootFragment.contains("DeviceDosingChannelCalibrationFragmentArgs"))
        assertTrue(operations.contains("toDeviceRootSnapshot()"))
        assertTrue(operations.contains("DefaultDeviceMenuAccessOperations.create"))
        assertTrue(
            operations.contains(
                "runtimePort.requestStatus(context.uid, context.slot.wireKey.value)"
            )
        )
        assertTrue(
            operations.contains(
                "dosing?.requestChannelStatus(deviceUid, channelKey)"
            )
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
            "app/src/main/java/com/aqua/aqualight/application/devices/dosing/" +
                "DeviceDosingCalibrationOperations.kt"
        )

        assertTrue(workflow.contains("operations.exitSafely("))
        assertTrue(workflow.contains("operations.primeSafetyStop("))
        assertTrue(workflow.contains("operations.awaitPrimeSafetyStop("))
        assertFalse(workflow.contains("DeviceDosingRuntimeRepository"))
        assertFalse(workflow.contains("delay("))

        assertTrue(applicationBoundary.contains("suspend fun exitSafely("))
        assertTrue(applicationBoundary.contains("suspend fun awaitPrimeSafetyStop("))
        assertTrue(applicationBoundary.contains("delay(constraints.primeSafetyTimeoutMs)"))
        assertTrue(applicationBoundary.contains("verificationDoseStarted"))
        assertTrue(applicationBoundary.contains("DeviceDosingCalibrationSessionPhase.IDLE"))
    }

    @Test
    fun `dosing program capacities come from firmware metadata not product constants`() {
        val policy = source(
            "app/src/main/java/com/aqua/aqualight/application/devices/dosing/" +
                "DeviceDosingScheduleDraftPolicy.kt"
        )
        val operations = source(
            "app/src/main/java/com/aqua/aqualight/application/devices/dosing/" +
                "DeviceDosingChannelOperations.kt"
        )
        val planViewModel = source(
            DOSING_SOURCE_ROOT + "channel/plan/DeviceDosingPlanViewModel.kt"
        )
        val customContract = source(
            DOSING_SOURCE_ROOT +
                "channel/schedule/custom/DeviceDosingCustomScheduleContract.kt"
        )
        val timerContract = source(
            DOSING_SOURCE_ROOT +
                "channel/schedule/timer/DeviceDosingTimerScheduleContract.kt"
        )

        assertFalse(policy.contains("MAX_DOSES_PER_DAY"))
        assertTrue(policy.contains("maxPeriods: Int"))
        assertTrue(policy.contains("maxDoseCount: Int"))
        assertTrue(operations.contains("maxEventsPerChannel"))
        assertTrue(operations.contains("maxCustomPeriodsPerChannel"))
        assertTrue(planViewModel.contains("currentMaxEventsPerChannel"))
        assertTrue(planViewModel.contains("currentMaxCustomPeriodsPerChannel"))
        assertTrue(customContract.contains("maxPeriods"))
        assertTrue(customContract.contains("maxDoseCount"))
        assertTrue(timerContract.contains("maxDoseCount"))
    }

    @Test
    fun `plan detail and reservoir mutations stay behind one application channel boundary`() {
        val applicationBoundary = source(
            "app/src/main/java/com/aqua/aqualight/application/devices/dosing/" +
                "DeviceDosingChannelOperations.kt"
        )
        val adapter = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/dosing/" +
                "DefaultDeviceDosingChannelOperations.kt"
        )
        val ownerFactory = source(
            "app/src/main/java/com/aqua/aqualight/composition/OwnerViewModelFactory.kt"
        )
        val processFactory = source(
            "app/src/main/java/com/aqua/aqualight/composition/ProcessViewModelFactory.kt"
        )

        listOf(
            "suspend fun saveProgram(",
            "suspend fun setMissedDoseRecoveryEnabled(",
            "suspend fun dispenseManualDose(",
            "suspend fun resetChannel(",
            "suspend fun saveReservoir(",
            "suspend fun refillReservoir("
        ).forEach { contract -> assertTrue(applicationBoundary.contains(contract)) }

        assertTrue(adapter.contains("runtime.saveProgram("))
        assertTrue(adapter.contains("runtime.resetChannel("))
        assertTrue(adapter.contains("runtime.configureReservoir("))
        assertTrue(adapter.contains("runtime.reservoirRefill("))
        assertTrue(ownerFactory.contains("DefaultDeviceDosingChannelOperations(repository)"))
        assertTrue(ownerFactory.contains("DeviceDosingPlanViewModel::class.java"))
        assertTrue(ownerFactory.contains("DeviceDosingChannelDetailViewModel::class.java"))
        assertTrue(ownerFactory.contains("DeviceDosingReservoirViewModel::class.java"))
        assertFalse(processFactory.contains("DeviceDosingPlanViewModel"))
        assertFalse(processFactory.contains("DeviceDosingChannelDetailViewModel"))
        assertFalse(processFactory.contains("DeviceDosingReservoirViewModel"))
    }

    @Test
    fun `dosing runtime is isolated from standalone timer engine`() {
        val access = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/dosing/contract/" +
                "DeviceDosingRuntimeAccess.kt"
        )
        val commercialModules = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/catalog/" +
                "AqlCommercialRuntimeModuleContract.kt"
        )
        val runtimeRepository = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/dosing/repository/" +
                "DeviceDosingRuntimeRepository.kt"
        )

        assertTrue(access.contains("!capabilities.standaloneTimer"))
        assertTrue(access.contains("limits.timerChannelCount == 0"))
        assertTrue(access.contains("!modules.timerApi"))
        assertTrue(access.contains("!modules.timerEngine"))
        assertTrue(commercialModules.contains("timerEngine = standaloneTimer"))
        assertFalse(runtimeRepository.contains("DeviceTimerRuntime"))
        assertFalse(runtimeRepository.contains("timer.*"))
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
