package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingUiLayerBoundaryTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `dosing root initializes first frame header from bound central state`() {
        val rootFragment = source(ROOT_SOURCE_ROOT + "DeviceDosingRootFragment.kt")
        val bind = rootFragment.indexOf("viewModel.bind(")
        val firstFrameHeader = rootFragment.indexOf(
            "setupHeader(title = resolveHeaderTitle(viewModel.uiState.value.title))"
        )

        assertTrue(bind >= 0)
        assertTrue(firstFrameHeader > bind)
        assertTrue(
            rootFragment.contains(
                "authoritativeTitle\n            .ifBlank { args.deviceTitle }"
            )
        )
        assertFalse(rootFragment.contains("setupHeader(title = args.deviceTitle.ifBlank"))
    }

    @Test
    fun `catalog structural validity stays outside dosing ui`() {
        val screen = source(ROOT_SOURCE_ROOT + "DosingCatalogScreen.kt")
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
        val models = source(CARD_SOURCE_ROOT + "DosingChannelCardModels.kt")
        val mapper = source(CARD_SOURCE_ROOT + "DosingChannelCardMapper.kt")

        assertFalse(models.contains("require(slotId"))
        assertFalse(models.contains("require(channelNumber"))
        assertFalse(models.contains("require(displayName"))
        assertFalse(models.contains("selectedDays.distinct()"))
        assertFalse(models.contains("require(dailyDoseMl"))
        assertFalse(models.contains("require(scheduledDeliveredTodayMl"))
        assertFalse(models.contains("require(manualDeliveredTodayMl"))
        assertFalse(models.contains("val wireKey: String"))
        assertFalse(models.contains("wireKey = wireKey.value"))

        assertTrue(mapper.contains("DeviceDosingChannelSlot.toInitialDosingChannelCardUiState"))
    }

    @Test
    fun `channel navigation stays behind application and route boundaries`() {
        val rootFragment = source(ROOT_SOURCE_ROOT + "DeviceDosingRootFragment.kt")
        val rootViewModel = source(ROOT_SOURCE_ROOT + "DeviceDosingRootViewModel.kt")
        val cardMapper = source(CARD_SOURCE_ROOT + "DosingChannelCardMapper.kt")
        val navigationContract = source(
            "app/src/main/java/com/aqua/aqualight/application/devices/dosing/" +
                "DeviceDosingChannelNavigationOperations.kt"
        )
        val calibrationSnapshotReducer = source(
            DOSING_SOURCE_ROOT +
                "channel/calibration/DosingCalibrationSnapshotReducer.kt"
        )
        val navigator = source(
            "app/src/main/java/com/aqua/aqualight/ui/navigation/AppRouteNavigator.kt"
        )
        val appGraph = source("app/src/main/res/navigation/nav_app.xml")

        assertTrue(rootFragment.contains("AppRouteNavigator.openDosingChannel"))
        assertFalse(rootFragment.contains("DeviceDosingChannelDetailFragmentArgs"))
        assertFalse(rootFragment.contains("DeviceDosingChannelCalibrationFragmentArgs"))
        assertFalse(navigationContract.contains("com.aqua.aqualight.data.devices"))
        assertFalse(navigationContract.contains("runtime.modules"))
        assertFalse(navigationContract.contains("DevicesRepository"))
        assertTrue(navigator.contains("fun openDosingChannel("))
        assertTrue(appGraph.contains("deviceDosingChannelCalibrationFragment"))
        assertTrue(appGraph.contains("deviceDosingChannelDetailFragment"))
        assertFalse(rootViewModel.contains("withNavigationTarget"))
        assertFalse(cardMapper.contains("DeviceDosingChannelNavigationTarget"))
        assertFalse(cardMapper.contains("channel.channelTitle.ifBlank"))
        assertFalse(navigationContract.contains("channelTitle"))
        assertFalse(navigator.contains("channelTitle"))
        assertFalse(appGraph.contains("channelTitle"))
        assertFalse(calibrationSnapshotReducer.contains("displayName = snapshot.channelTitle"))
    }

    @Test
    fun `legacy dosing runtime package is absent`() {
        val legacyRuntime = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/dosing"
        )

        assertFalse(legacyRuntime.exists())
    }

    @Test
    fun `dosing v1 production composition remains owner scoped and central`() {
        val v1Root = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/data/devices/dosing/v1"
        )
        val provider = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/" +
                "DeviceRuntimeModuleProvider.kt"
        )
        val runtimeRepository = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/repository/" +
                "DeviceRuntimeRepository.kt"
        )
        val ownerGraph = source(
            "app/src/main/java/com/aqua/aqualight/composition/OwnerDependencyGraph.kt"
        )
        val productionRuntime = source(
            "app/src/main/java/com/aqua/aqualight/data/devices/dosing/v1/" +
                "DeviceDosingV1ProductionRuntime.kt"
        )
        val mainSources = File(repositoryRoot, "app/src/main/java")
            .walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    !file.toPath().startsWith(v1Root.toPath())
            }
            .map(File::readText)
            .toList()

        assertTrue(v1Root.isDirectory)
        assertFalse(provider.contains("DeviceDosingV1"))
        assertFalse(runtimeRepository.contains("DeviceDosingV1"))
        assertTrue(ownerGraph.contains("dosingOperations = createDosingOperations(dependencies)"))
        assertTrue(ownerGraph.contains("DeviceDosingV1ProductionRuntime("))
        assertTrue(productionRuntime.contains("DeviceDosingV1StateOwner(lowLevelAlertLedger)"))
        assertTrue(
            productionRuntime.contains(
                "DeviceDosingV1Repository(runtimeModules.commandGateway)"
            )
        )
        assertTrue(productionRuntime.contains("devicesRepository.typedRuntimeEvents()"))
        assertTrue(
            mainSources.count { source ->
                source.contains("DeviceDosingV1ProductionRuntime(")
            } == 1
        )
        assertTrue(mainSources.none { source -> source.contains("DeviceDosingV1Repository") })
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
            "app/src/main/java/com/aqua/aqualight/application/devices/dosing/" +
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
    fun `reservoir capacity policy stays centralized and unsaved draft is not persisted`() {
        val fragment = source(
            DOSING_SOURCE_ROOT + "channel/reservoir/DeviceDosingReservoirFragment.kt"
        )
        val viewModel = source(
            DOSING_SOURCE_ROOT + "channel/reservoir/DeviceDosingReservoirViewModel.kt"
        )
        val policy = source(
            "app/src/main/java/com/aqua/aqualight/application/devices/dosing/" +
                "DeviceDosingReservoirCapacityPolicy.kt"
        )

        listOf(fragment, viewModel).forEach { presentation ->
            assertFalse(presentation.contains("toDoubleOrNull"))
            assertFalse(presentation.contains("BigDecimal"))
            assertFalse(presentation.contains("4_294_967_295"))
            assertFalse(presentation.contains("0.001"))
        }
        assertFalse(fragment.contains("onSaveInstanceState"))
        assertFalse(fragment.contains("STATE_RESERVOIR_CAPACITY_MICROLITERS"))
        assertFalse(viewModel.contains("restoredDraft"))
        assertTrue(viewModel.contains("DeviceDosingReservoirCapacityPolicy.validate"))
        assertTrue(policy.contains("BigDecimal"))
        assertTrue(policy.contains("MAX_CAPACITY_MICROLITERS"))
    }

    @Test
    fun `reservoir alarm and supply projection remain independent application concepts`() {
        val mapper = source(CARD_SOURCE_ROOT + "DosingChannelCardReservoirMapper.kt")
        val policy = source(
            "app/src/main/java/com/aqua/aqualight/application/devices/dosing/" +
                "DeviceDosingSupplyProjectionPolicy.kt"
        )

        assertTrue(mapper.contains("projection.supplySeverity.toUiTone()"))
        assertFalse(mapper.contains("CRITICAL_REMAINING_DAYS"))
        assertFalse(mapper.contains("WARNING_REMAINING_DAYS"))
        assertFalse(mapper.contains("selectedWeekdays"))
        assertTrue(policy.contains("CRITICAL_REMAINING_DAYS = 10"))
        assertTrue(policy.contains("WARNING_REMAINING_DAYS = 20"))
        assertTrue(policy.contains("supplySeverity"))
        assertFalse(policy.contains("lowLevelActive"))
        assertTrue(dosingSources().none { source -> source.contains("lowLevelActive") })
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
        const val ROOT_SOURCE_ROOT = DOSING_SOURCE_ROOT + "root/"
        const val CARD_SOURCE_ROOT = DOSING_SOURCE_ROOT + "presentation/card/"
        const val APPLICATION_DEVICE_SLOTS =
            "app/src/main/java/com/aqua/aqualight/application/devices/DeviceChannelSlots.kt"
    }
}
