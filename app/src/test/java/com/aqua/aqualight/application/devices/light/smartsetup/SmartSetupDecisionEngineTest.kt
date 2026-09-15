package com.aqua.aqualight.application.devices.light.smartsetup

import com.aqua.aqualight.application.aquarium.lighting.AquariumObservationSeverity
import com.aqua.aqualight.application.aquarium.lighting.Co2Status
import com.aqua.aqualight.application.aquarium.lighting.PlantDensity
import com.aqua.aqualight.application.aquarium.lighting.PlantLightDemand
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSetupDecisionEngineTest {

    @Test
    fun `lifecycle boundaries use one based aquarium days`() {
        assertLifecycle(day = 1, SmartSetupLifecycleStage.STARTUP)
        assertLifecycle(day = 21, SmartSetupLifecycleStage.STARTUP)
        assertLifecycle(day = 22, SmartSetupLifecycleStage.ESTABLISHING)
        assertLifecycle(day = 90, SmartSetupLifecycleStage.ESTABLISHING)
        assertLifecycle(day = 91, SmartSetupLifecycleStage.MATURE)
        assertEquals(null, SmartSetupLifecycleClassifier.classify(EVALUATION_DAY + 1, EVALUATION_DAY))
    }

    @Test
    fun `complete freshwater profile produces a bounded stable offline lifecycle plan`() {
        val dayOneInput = inputForDay(1)
        val first = readyDecision(dayOneInput).recommendation
        val nextDay = readyDecision(
            dayOneInput.copy(
                evaluationEpochDay = EVALUATION_DAY + 1,
                setupDay = 2
            )
        ).recommendation

        assertEquals(4, first.plan.phases.size)
        assertEquals(first.plan, nextDay.plan)
        assertEquals(inputForDay(1).setupDateEpochDay, first.plan.phases.first().validFromEpochDay)
        assertEquals(null, first.plan.phases.last().validUntilEpochDayExclusive)
        first.plan.phases.zipWithNext().forEach { (current, next) ->
            assertEquals(current.validUntilEpochDayExclusive, next.validFromEpochDay)
        }
        first.plan.phases.forEach { phase ->
            assertTrue(phase.transitionDays in 0..90)
            assertEquals(127, phase.weekdaysMask)
            assertTrue(phase.startTimeMs in 0 until DAY_MS)
            assertTrue(phase.endTimeMs in 1 until DAY_MS)
            assertTrue(phase.endTimeMs > phase.startTimeMs)
            assertTrue(phase.rampDurationMs * 2 <= phase.endTimeMs - phase.startTimeMs)
            assertEquals(SmartSetupCalibrationCatalog.wrgbProElite.channels, phase.scene.channels.keys.toList())
            assertTrue(phase.scene.channels.values.all { value -> value in 0..100 })
        }
    }

    @Test
    fun `missing semantic facts are returned together and are never defaulted`() {
        val decision = SmartSetupDecisionEngine.decide(
            completeInput().copy(
                setupDateEpochDay = null,
                setupDay = null,
                lifecycleStage = null,
                plantDensity = null,
                highestPlantLightDemand = null,
                co2Status = null,
                isActiveSoil = null,
                waterDepthCm = null,
                fixtureMountHeightCm = null,
                preferredViewingStartMinuteOfDay = null,
                preferredViewingEndMinuteOfDay = null,
                algaeObservation = null,
                plantStressObservation = null,
                observationDateEpochDay = null,
                calibrationProfile = null
            )
        ) as SmartSetupDecision.MissingData

        assertEquals(
            setOf(
                SmartSetupMissingField.SETUP_DATE,
                SmartSetupMissingField.PLANT_DENSITY,
                SmartSetupMissingField.HIGHEST_PLANT_LIGHT_DEMAND,
                SmartSetupMissingField.CO2_STATUS,
                SmartSetupMissingField.ACTIVE_SOIL,
                SmartSetupMissingField.WATER_DEPTH,
                SmartSetupMissingField.FIXTURE_MOUNT_HEIGHT,
                SmartSetupMissingField.VIEWING_WINDOW,
                SmartSetupMissingField.ALGAE_OBSERVATION,
                SmartSetupMissingField.PLANT_STRESS_OBSERVATION,
                SmartSetupMissingField.OBSERVATION_DATE,
                SmartSetupMissingField.CALIBRATION_PROFILE
            ),
            decision.fields
        )
    }

    @Test
    fun `unsupported evidence and product states fail closed before missing data`() {
        assertUnsupported(
            completeInput().copy(
                aquariumEnvironment = SmartSetupAquariumEnvironment.MARINE,
                waterDepthCm = null
            ),
            SmartSetupUnsupportedReason.MARINE_EVIDENCE_NOT_AVAILABLE
        )
        assertUnsupported(
            completeInput().copy(deviceProductKey = "LIGHT_UNKNOWN"),
            SmartSetupUnsupportedReason.UNKNOWN_DEVICE_PRODUCT
        )
        assertUnsupported(
            completeInput().copy(reportedCalibrationRevision = null),
            SmartSetupUnsupportedReason.DEVICE_CALIBRATION_METADATA_MISSING
        )
        assertUnsupported(
            completeInput().copy(reportedCalibrationRevision = 3),
            SmartSetupUnsupportedReason.CALIBRATION_PROFILE_MISMATCH
        )
        assertUnsupported(
            completeInput().copy(reportedChannelSceneKeys = listOf("redPercent")),
            SmartSetupUnsupportedReason.UNSUPPORTED_CHANNEL_SET
        )
    }

    @Test
    @Suppress("NestedBlockDepth")
    fun `commercial planted cross product always remains inside firmware plan invariants`() {
        var evaluated = 0
        val maintenanceCases = listOf(
            SmartSetupMaintenanceObservations(),
            SmartSetupMaintenanceObservations(
                latestAlgaeCleaningEpochDay = EVALUATION_DAY - 14
            ),
            SmartSetupMaintenanceObservations(
                latestAlgaeCleaningEpochDay = EVALUATION_DAY - 15,
                overdueRelevantTaskCount = 2
            )
        )
        val viewingWindows = listOf(0 to 600, 600 to 1_200)
        listOf(1, 22, 91).forEach { aquariumDay ->
            PlantDensity.entries.forEach { density ->
                PlantLightDemand.entries.forEach { demand ->
                    Co2Status.entries.forEach { co2 ->
                        listOf(false, true).forEach { activeSoil ->
                            listOf(15, 40, 60).forEach { depth ->
                                listOf(0, 15, 30).forEach { mounting ->
                                    AquariumObservationSeverity.entries.forEach { algae ->
                                        AquariumObservationSeverity.entries.forEach { stress ->
                                            maintenanceCases.forEach { maintenance ->
                                                viewingWindows.forEach { viewing ->
                                                    val decision = SmartSetupDecisionEngine.decide(
                                                        inputForDay(aquariumDay).copy(
                                                            plantDensity = density,
                                                            highestPlantLightDemand = demand,
                                                            co2Status = co2,
                                                            isActiveSoil = activeSoil,
                                                            waterDepthCm = depth,
                                                            fixtureMountHeightCm = mounting,
                                                            algaeObservation = algae,
                                                            plantStressObservation = stress,
                                                            maintenance = maintenance,
                                                            preferredViewingStartMinuteOfDay =
                                                                viewing.first,
                                                            preferredViewingEndMinuteOfDay =
                                                                viewing.second
                                                        )
                                                    )
                                                    val ready = decision as SmartSetupDecision.Ready
                                                    assertPlanBounds(ready.recommendation.plan)
                                                    evaluated += 1
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(78_732, evaluated)
    }

    @Test
    fun `unplanted freshwater is explicit and does not require plant classifications`() {
        val recommendation = readyDecision(
            completeInput().copy(
                isPlanted = false,
                plantDensity = null,
                highestPlantLightDemand = null
            )
        ).recommendation

        assertTrue(recommendation.factors.any { factor ->
            factor.id == SmartSetupFactorId.UNPLANTED_AQUARIUM
        })
        assertTrue(recommendation.plan.phases.all { phase ->
            phase.scene.channels.values.max() <= 45
        })
    }

    @Test
    fun `unplanted output still responds to optical distance and maintenance risk`() {
        val base = completeInput().copy(
            isPlanted = false,
            plantDensity = null,
            highestPlantLightDemand = null,
            isActiveSoil = false,
            waterDepthCm = 15,
            fixtureMountHeightCm = 0
        )
        val shallow = readyDecision(base).recommendation.plan.phases.last().scene
        val deep = readyDecision(
            base.copy(waterDepthCm = 60, fixtureMountHeightCm = 30)
        ).recommendation.plan.phases.last().scene
        val overdue = readyDecision(
            base.copy(maintenance = SmartSetupMaintenanceObservations(overdueRelevantTaskCount = 3))
        ).recommendation.plan.phases.last().scene

        assertTrue(deep.channels.getValue(SmartSetupChannel.WHITE) >
            shallow.channels.getValue(SmartSetupChannel.WHITE)
        )
        assertTrue(overdue.channels.getValue(SmartSetupChannel.WHITE) <
            shallow.channels.getValue(SmartSetupChannel.WHITE)
        )
    }

    @Test
    fun `device and calibration matrix fails closed with stable reasons`() {
        val cases = listOf(
            completeInput().copy(deviceProductKey = "LIGHT_UNKNOWN") to
                SmartSetupUnsupportedReason.UNKNOWN_DEVICE_PRODUCT,
            completeInput().copy(
                deviceProductKey = SmartSetupCalibrationCatalog.RGB_PRO_SLIM_PRODUCT
            ) to SmartSetupUnsupportedReason.CALIBRATION_NOT_AVAILABLE_FOR_PRODUCT,
            completeInput().copy(reportedCalibrationRevision = null) to
                SmartSetupUnsupportedReason.DEVICE_CALIBRATION_METADATA_MISSING,
            completeInput().copy(reportedCalibrationRevision = 99) to
                SmartSetupUnsupportedReason.CALIBRATION_PROFILE_MISMATCH,
            completeInput().copy(reportedChannelSceneKeys = listOf("redPercent")) to
                SmartSetupUnsupportedReason.UNSUPPORTED_CHANNEL_SET,
            completeInput().copy(
                calibrationProfile = SmartSetupCalibrationCatalog.wrgbProElite.copy(
                    productKey = SmartSetupCalibrationCatalog.RGB_PRO_SLIM_PRODUCT
                )
            ) to SmartSetupUnsupportedReason.CALIBRATION_PRODUCT_MISMATCH,
            completeInput().copy(
                calibrationProfile = SmartSetupCalibrationCatalog.wrgbProElite.copy(revision = 99)
            ) to SmartSetupUnsupportedReason.CALIBRATION_PROFILE_MISMATCH
        )

        cases.forEach { (input, expected) -> assertUnsupported(input, expected) }
    }

    @Test
    fun `observation risk creates a stable hold phase starting on the observation date`() {
        val observationDay = EVALUATION_DAY - 2
        val risky = completeInput().copy(
            algaeObservation = AquariumObservationSeverity.SIGNIFICANT,
            observationDateEpochDay = observationDay
        )
        val first = readyDecision(risky).recommendation
        val next = readyDecision(
            risky.copy(
                evaluationEpochDay = EVALUATION_DAY + 1,
                setupDay = checkNotNull(risky.setupDay) + 1
            )
        ).recommendation

        assertEquals(1, first.plan.phases.size)
        assertEquals(observationDay, first.plan.phases.single().validFromEpochDay)
        assertEquals(first.plan, next.plan)
        assertEquals(EVALUATION_DAY + 7, first.reevaluationEpochDay)
    }

    @Test
    fun `fingerprint is deterministic locale independent and changes with semantic input`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val first = readyDecision(completeInput()).recommendation.profileFingerprint
            Locale.setDefault(Locale("tr", "TR"))
            val second = readyDecision(completeInput()).recommendation.profileFingerprint
            val changed = readyDecision(
                completeInput().copy(plantDensity = PlantDensity.HIGH)
            ).recommendation.profileFingerprint

            assertEquals(first, second)
            assertTrue(Regex("^[0-9a-f]{64}$").matches(first))
            assertNotEquals(first, changed)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `calendar horizon rejects plans whose future lifecycle phases exceed firmware range`() {
        val latestSafeSetup = MAX_EPOCH_DAY - 90
        readyDecision(input(setupDate = latestSafeSetup, evaluation = latestSafeSetup))
        assertUnsupported(
            input(setupDate = latestSafeSetup + 1, evaluation = latestSafeSetup + 1),
            SmartSetupUnsupportedReason.CALENDAR_OUTSIDE_FIRMWARE_RANGE
        )
    }

    @Test
    fun `stale observations block generation until the user records current facts`() {
        val decision = SmartSetupDecisionEngine.decide(
            completeInput().copy(observationDateEpochDay = EVALUATION_DAY - 31)
        ) as SmartSetupDecision.MissingData

        assertEquals(setOf(SmartSetupMissingField.OBSERVATIONS_STALE), decision.fields)
    }

    @Test
    fun `algae maintenance freshness boundary is inclusive at fourteen days`() {
        val recent = readyDecision(
            completeInput().copy(
                maintenance = SmartSetupMaintenanceObservations(
                    latestAlgaeCleaningEpochDay = EVALUATION_DAY - 14
                )
            )
        ).recommendation
        val stale = readyDecision(
            completeInput().copy(
                maintenance = SmartSetupMaintenanceObservations(
                    latestAlgaeCleaningEpochDay = EVALUATION_DAY - 15
                )
            )
        ).recommendation

        assertTrue(recent.factors.any { factor ->
            factor.id == SmartSetupFactorId.RECENT_ALGAE_MAINTENANCE
        })
        assertTrue(stale.factors.none { factor ->
            factor.id == SmartSetupFactorId.RECENT_ALGAE_MAINTENANCE
        })
        assertTrue(
            recent.plan.phases.last().scene.channels.getValue(SmartSetupChannel.WHITE) <
                stale.plan.phases.last().scene.channels.getValue(SmartSetupChannel.WHITE)
        )
    }

    @Test
    fun `observation before setup or outside firmware calendar fails closed`() {
        assertUnsupported(
            inputForDay(7).copy(observationDateEpochDay = EVALUATION_DAY - 7),
            SmartSetupUnsupportedReason.OBSERVATION_BEFORE_SETUP
        )
        assertUnsupported(
            input(setupDate = MIN_EPOCH_DAY, evaluation = MIN_EPOCH_DAY).copy(
                observationDateEpochDay = MIN_EPOCH_DAY - 1
            ),
            SmartSetupUnsupportedReason.CALENDAR_OUTSIDE_FIRMWARE_RANGE
        )
    }

    private fun assertLifecycle(day: Int, stage: SmartSetupLifecycleStage) {
        val lifecycle = SmartSetupLifecycleClassifier.classify(
            setupDateEpochDay = EVALUATION_DAY - (day - 1),
            evaluationEpochDay = EVALUATION_DAY
        )
        assertEquals(day, lifecycle?.setupDay)
        assertEquals(stage, lifecycle?.stage)
    }

    private fun assertUnsupported(input: SmartSetupInput, reason: SmartSetupUnsupportedReason) {
        val unsupported = SmartSetupDecisionEngine.decide(input) as SmartSetupDecision.Unsupported
        assertEquals(reason, unsupported.reason)
    }

    private fun assertPlanBounds(plan: SmartLightPlanDraft) {
        assertTrue(plan.initialStartPercent in 20..100)
        assertTrue(plan.initialStartPercent % 5 == 0)
        assertTrue(plan.phases.size in 1..8)
        assertEquals(null, plan.phases.last().validUntilEpochDayExclusive)
        plan.phases.forEachIndexed { index, phase ->
            assertTrue(phase.validFromEpochDay in MIN_EPOCH_DAY..MAX_EPOCH_DAY)
            assertTrue(phase.transitionDays in 0..90)
            assertEquals(127, phase.weekdaysMask)
            assertTrue(phase.endTimeMs > phase.startTimeMs)
            assertTrue(phase.startTimeMs % 60_000L == 0L)
            assertTrue(phase.endTimeMs % 60_000L == 0L)
            assertTrue(phase.rampDurationMs in ALLOWED_RAMPS)
            if (index > 0) {
                assertEquals(
                    plan.phases[index - 1].validUntilEpochDayExclusive,
                    phase.validFromEpochDay
                )
            }
        }
    }

    private fun readyDecision(input: SmartSetupInput): SmartSetupDecision.Ready =
        SmartSetupDecisionEngine.decide(input) as SmartSetupDecision.Ready

    private fun inputForDay(day: Int): SmartSetupInput = input(
        setupDate = EVALUATION_DAY - (day - 1),
        evaluation = EVALUATION_DAY
    )

    private fun completeInput(): SmartSetupInput = inputForDay(101)

    private fun input(setupDate: Long, evaluation: Long): SmartSetupInput {
        val lifecycle = checkNotNull(SmartSetupLifecycleClassifier.classify(setupDate, evaluation))
        return SmartSetupInput(
            tankId = 42,
            tankName = "Display tank",
            aquariumEnvironment = SmartSetupAquariumEnvironment.FRESHWATER,
            evaluationEpochDay = evaluation,
            setupDateEpochDay = setupDate,
            setupDay = lifecycle.setupDay,
            lifecycleStage = lifecycle.stage,
            isPlanted = true,
            plantDensity = PlantDensity.MEDIUM,
            highestPlantLightDemand = PlantLightDemand.MEDIUM,
            co2Status = Co2Status.ACTIVE,
            isActiveSoil = true,
            waterDepthCm = 40,
            fixtureMountHeightCm = 10,
            deviceProductKey = SmartSetupCalibrationCatalog.WRGB_PRO_ELITE_PRODUCT,
            reportedCalibrationRevision = SmartSetupCalibrationCatalog.wrgbProElite.revision,
            reportedChannelSceneKeys = SmartSetupCalibrationCatalog.wrgbProElite.channels
                .map(SmartSetupChannel::sceneKey),
            calibrationProfile = SmartSetupCalibrationCatalog.wrgbProElite,
            preferredViewingStartMinuteOfDay = 600,
            preferredViewingEndMinuteOfDay = 1_200,
            algaeObservation = AquariumObservationSeverity.NONE,
            plantStressObservation = AquariumObservationSeverity.NONE,
            observationDateEpochDay = evaluation,
            maintenance = SmartSetupMaintenanceObservations()
        )
    }

    private companion object {
        val EVALUATION_DAY = LocalDate.of(2026, 9, 15).toEpochDay()
        val MIN_EPOCH_DAY = LocalDate.of(2000, 1, 1).toEpochDay()
        val MAX_EPOCH_DAY = LocalDate.of(2099, 12, 31).toEpochDay()
        const val DAY_MS = 86_400_000L
        val ALLOWED_RAMPS = setOf(
            0L,
            1_800_000L,
            3_600_000L,
            5_400_000L,
            7_200_000L,
            9_000_000L
        )
    }
}
