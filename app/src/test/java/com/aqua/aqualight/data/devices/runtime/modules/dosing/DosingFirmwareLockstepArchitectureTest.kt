package com.aqua.aqualight.data.devices.runtime.modules.dosing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingFirmwareLockstepArchitectureTest {

    @Test
    fun `production source sets contain no legacy dosing schedule contract`() {
        productionKotlinSources().forEach { source ->
            FORBIDDEN_LEGACY_TOKENS.forEach { token ->
                assertFalse("Legacy Dosing token '$token' remains in ${source.path}", source.text.contains(token))
            }
        }
    }

    @Test
    fun `dosing production sources never depend on standalone timer runtime`() {
        dosingProductionSources().forEach { source ->
            assertFalse(source.text.contains("runtime.modules.timer"))
            assertFalse(source.text.contains("DeviceTimerRuntime"))
        }
    }

    @Test
    fun `final dosing contract exposes program reset revision status and live metadata`() {
        val models = source(MODELS)
        val repository = source(REPOSITORY)
        val parser = source(STATUS_PARSER)
        val access = source(ACCESS)

        listOf(
            "DeviceDosingProgramApplyPayload",
            "DeviceDosingChannelResetPayload",
            "expectedRevision",
            "DeviceDosingSchedulingMetadata",
            "DeviceDosingUsageToday",
            "missedDoseRecoveryEnabled"
        ).forEach { token -> assertTrue(models.text.contains(token)) }

        listOf(
            "requestChannelStatus(",
            "applyProgram(",
            "resetChannel(",
            "acceptStatusChange("
        ).forEach { token -> assertTrue(repository.text.contains(token)) }

        assertTrue(parser.text.contains("parseGlobal("))
        assertTrue(parser.text.contains("parseChannel("))
        assertTrue(parser.text.contains("parseStatusChange("))
        assertTrue(access.text.contains("!capabilities.standaloneTimer"))
        assertTrue(access.text.contains("limits.timerChannelCount == 0"))
        assertTrue(access.text.contains("!modules.timerApi"))
        assertTrue(access.text.contains("!modules.timerEngine"))
    }

    @Test
    fun `dosing editor capacities are firmware supplied not application product constants`() {
        val policy = source(SCHEDULE_POLICY)
        val channelOperations = source(CHANNEL_OPERATIONS)
        val planViewModel = source(PLAN_VIEW_MODEL)

        assertFalse(policy.text.contains("MAX_DOSES_PER_DAY"))
        assertTrue(policy.text.contains("maxDoseCount: Int"))
        assertTrue(channelOperations.text.contains("maxEventsPerChannel"))
        assertTrue(channelOperations.text.contains("maxCustomPeriodsPerChannel"))
        assertTrue(planViewModel.text.contains("currentMaxEventsPerChannel"))
        assertTrue(planViewModel.text.contains("currentMaxCustomPeriodsPerChannel"))
    }

    private fun productionKotlinSources(): Sequence<Source> = sequenceOf(
        "app/src/main/java",
        "app/src/debug/java",
        "app/src/releaseSmoke/java"
    ).flatMap { relativeRoot -> kotlinSources(relativeRoot) }

    private fun dosingProductionSources(): Sequence<Source> = productionKotlinSources()
        .filter { source ->
            source.path.contains("/dosing/") || source.path.contains("Dosing")
        }

    private fun kotlinSources(relativeRoot: String): Sequence<Source> {
        val root = File(repositoryRoot(), relativeRoot)
        if (!root.isDirectory) return emptySequence()
        return root.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .map { file -> Source(file.relativeTo(repositoryRoot()).invariantSeparatorsPath, file.readText()) }
    }

    private fun source(relativePath: String): Source {
        val file = File(repositoryRoot(), relativePath)
        return Source(relativePath, file.readText())
    }

    private fun repositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }

    private data class Source(val path: String, val text: String)

    private companion object {
        val FORBIDDEN_LEGACY_TOKENS = listOf(
            "DeviceDosingScheduleConfig",
            "DeviceDosingScheduleStatus",
            "DeviceDosingConfigApplyPayload",
            "DeviceDosingChannelDosingConfig",
            "intervalOnMs",
            "intervalOffMs",
            "repeatCount",
            "MAX_DOSES_PER_DAY"
        )

        const val MODELS =
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/dosing/models/DeviceDosingModels.kt"
        const val REPOSITORY =
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/dosing/repository/DeviceDosingRuntimeRepository.kt"
        const val STATUS_PARSER =
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/dosing/parsers/DeviceDosingStatusParser.kt"
        const val ACCESS =
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/dosing/contract/DeviceDosingRuntimeAccess.kt"
        const val SCHEDULE_POLICY =
            "app/src/main/java/com/aqua/aqualight/application/devices/dosing/DeviceDosingScheduleDraftPolicy.kt"
        const val CHANNEL_OPERATIONS =
            "app/src/main/java/com/aqua/aqualight/application/devices/dosing/DeviceDosingChannelOperations.kt"
        const val PLAN_VIEW_MODEL =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/plan/DeviceDosingPlanViewModel.kt"
    }
}
