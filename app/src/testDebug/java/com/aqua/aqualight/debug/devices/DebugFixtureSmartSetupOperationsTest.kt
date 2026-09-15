package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.application.aquarium.lighting.AquariumLightingProfile
import com.aqua.aqualight.application.aquarium.lighting.AquariumObservationSeverity
import com.aqua.aqualight.application.aquarium.lighting.Co2Status
import com.aqua.aqualight.application.aquarium.lighting.PlantDensity
import com.aqua.aqualight.application.aquarium.lighting.PlantLightDemand
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupApplyResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupCalibrationCatalog
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupDecision
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupOperations
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupProfileSaveResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupReadResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugFixtureSmartSetupOperationsTest {

    @Test
    fun assignedFixtureUsesRealTankFactsFromMissingDataThroughAtomicApply() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val fixtureUid = fixtures.snapshots.single { snapshot ->
            snapshot.product.productKey == SmartSetupCalibrationCatalog.WRGB_PRO_ELITE_PRODUCT
        }.deviceUid.value
        val assignments = DebugFixtureTankAssignments()
        var tank = tankSnapshot()
        val operations = DebugFixtureSmartSetupOperations(
            delegate = FailOnFixtureSmartSetupOperations,
            fixtures = fixtures,
            fixtureAssignments = assignments,
            tankSnapshot = { tankId -> tank.takeIf { snapshot -> snapshot.id == tankId } },
            careTasks = { emptyList() },
            persistProfile = { tankId, setupDate, profile ->
                require(tank.id == tankId)
                tank = tank.copy(
                    setupDateEpochDay = setupDate,
                    lightingProfile = profile
                )
            },
            zoneId = { UTC },
            nowMillis = { NOW_MILLIS }
        )

        assertSame(SmartSetupReadResult.DeviceNotAssigned, operations.read(fixtureUid))
        assertSame(
            com.aqua.aqualight.application.devices.AssignDeviceToTankResult.Assigned,
            assignments.assign(fixtureUid, TANK_ID, setOf(TANK_ID))
        )
        val missing = (operations.read(fixtureUid) as SmartSetupReadResult.Available).snapshot
        assertTrue(missing.decision is SmartSetupDecision.MissingData)

        val saved = operations.saveProfile(
            deviceUid = fixtureUid,
            setupDateEpochDay = EVALUATION_EPOCH_DAY - 10L,
            profile = completeProfile()
        ) as SmartSetupProfileSaveResult.Saved
        val ready = saved.snapshot.decision as SmartSetupDecision.Ready
        assertFalse(saved.snapshot.installedPlanFingerprintMatches)

        val applied = operations.apply(
            fixtureUid,
            ready.recommendation.profileFingerprint
        ) as SmartSetupApplyResult.Applied
        val confirmed = (operations.read(fixtureUid) as SmartSetupReadResult.Available).snapshot

        assertEquals(1L, applied.revision)
        assertEquals(1L, applied.storageGeneration)
        assertEquals(applied.planId, confirmed.installedPlanId)
        assertEquals(applied.revision, confirmed.installedPlanRevision)
        assertTrue(confirmed.installedPlanFingerprintMatches)
    }

    private fun tankSnapshot() = AquariumTankSnapshot(
        id = TANK_ID,
        name = "Fixture tank",
        description = "",
        photoUri = null,
        setupDateEpochDay = null,
        widthCm = 45,
        lengthCm = 90,
        heightCm = 45,
        sizeUnit = "cm",
        volumeUnit = "L",
        tankType = AquariumTankTaxonomy.TYPE_PLANTED,
        tankStyle = AquariumTankTaxonomy.STYLE_NATURE_AQUARIUM,
        createdAtMillis = NOW_MILLIS,
        smartCareEnabled = true,
        careRemindersEnabled = true,
        plants = emptyList(),
        materials = emptyList(),
        livestock = emptyList()
    )

    private fun completeProfile() = AquariumLightingProfile(
        plantDensity = PlantDensity.MEDIUM,
        highestPlantLightDemand = PlantLightDemand.MEDIUM,
        co2Status = Co2Status.ACTIVE,
        isActiveSoil = true,
        waterDepthCm = 40,
        fixtureMountHeightCm = 10,
        preferredViewingStartMinuteOfDay = 10 * 60,
        preferredViewingEndMinuteOfDay = 19 * 60,
        algaeObservation = AquariumObservationSeverity.NONE,
        plantStressObservation = AquariumObservationSeverity.NONE,
        observationDateEpochDay = EVALUATION_EPOCH_DAY
    )

    private object FailOnFixtureSmartSetupOperations : SmartSetupOperations {
        override suspend fun read(deviceUid: String): SmartSetupReadResult =
            error("Fixture reads must not enter the production Smart Setup boundary.")

        override suspend fun saveProfile(
            deviceUid: String,
            setupDateEpochDay: Long,
            profile: AquariumLightingProfile
        ): SmartSetupProfileSaveResult =
            error("Fixture saves must not enter the production Smart Setup boundary.")

        override suspend fun apply(
            deviceUid: String,
            expectedProfileFingerprint: String
        ): SmartSetupApplyResult =
            error("Fixture apply must not enter the production Smart Setup boundary.")
    }

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")
        val NOW_MILLIS: Long = LocalDate.of(2026, 9, 15)
            .atStartOfDay(UTC)
            .toInstant()
            .toEpochMilli()
        val EVALUATION_EPOCH_DAY: Long = Instant.ofEpochMilli(NOW_MILLIS)
            .atZone(UTC)
            .toLocalDate()
            .toEpochDay()
        const val TANK_ID = 41L
    }
}
