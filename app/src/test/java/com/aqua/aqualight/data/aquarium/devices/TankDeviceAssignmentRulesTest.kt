package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.application.aquarium.AquariumCo2Readiness
import com.aqua.aqualight.application.aquarium.AquariumDaylightExposure
import com.aqua.aqualight.application.aquarium.AquariumPlantCoverage
import com.aqua.aqualight.application.aquarium.AquariumShelterAvailability
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.application.aquarium.AquariumSurfaceGrowth
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanConfidence
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupCalculator
import com.aqua.aqualight.data.store.CommercialStoreSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TankDeviceAssignmentRulesTest {

    @Test
    fun `assign stores one owner scoped relationship`() {
        val mutation = TankDeviceAssignmentRules.assign(
            store = emptyStore(),
            ownerUid = OWNER_A,
            tankId = TANK_A,
            deviceUid = DEVICE_A,
            assignedAtMillis = 100L
        )

        assertTrue(mutation.decision is TankDeviceStoreAssignDecision.Assigned)
        assertEquals(1, mutation.store.assignmentsCount)
        assertEquals(OWNER_A, mutation.store.getAssignments(0).ownerUid)
        assertEquals(TANK_A, mutation.store.getAssignments(0).tankId)
        assertEquals(DEVICE_A, mutation.store.getAssignments(0).deviceUid)
    }

    @Test
    fun `assign is idempotent for same owner device and tank`() {
        val first = assignedStore(
            ownerUid = OWNER_A,
            tankId = TANK_A,
            deviceUid = DEVICE_A
        )

        val mutation = TankDeviceAssignmentRules.assign(
            store = first,
            ownerUid = OWNER_A,
            tankId = TANK_A,
            deviceUid = DEVICE_A,
            assignedAtMillis = 200L
        )

        assertTrue(mutation.decision is TankDeviceStoreAssignDecision.AlreadyAssigned)
        assertSame(first, mutation.store)
        assertEquals(1, mutation.store.assignmentsCount)
        assertEquals(100L, mutation.store.getAssignments(0).assignedAtMillis)
    }

    @Test
    fun `assign rejects second tank for same owner device`() {
        val first = assignedStore(
            ownerUid = OWNER_A,
            tankId = TANK_A,
            deviceUid = DEVICE_A
        )

        val mutation = TankDeviceAssignmentRules.assign(
            store = first,
            ownerUid = OWNER_A,
            tankId = TANK_B,
            deviceUid = DEVICE_A,
            assignedAtMillis = 200L
        )

        val conflict = mutation.decision as TankDeviceStoreAssignDecision.Conflict
        assertSame(first, mutation.store)
        assertEquals(TANK_A, conflict.existingAssignment.tankId)
        assertEquals(1, mutation.store.assignmentsCount)
    }

    @Test
    fun `same device uid can be assigned independently by different owners`() {
        val ownerAStore = TankDeviceAssignmentRules.assign(
            store = emptyStore(),
            ownerUid = OWNER_A,
            tankId = TANK_A,
            deviceUid = DEVICE_A,
            assignedAtMillis = 100L
        ).store

        val mutation = TankDeviceAssignmentRules.assign(
            store = ownerAStore,
            ownerUid = OWNER_B,
            tankId = TANK_B,
            deviceUid = DEVICE_A,
            assignedAtMillis = 200L
        )

        assertTrue(mutation.decision is TankDeviceStoreAssignDecision.Assigned)
        assertEquals(2, mutation.store.assignmentsCount)
    }

    @Test
    fun `repair removes only stale records for target owner`() {
        val store = TankDeviceAssignmentsStore.newBuilder()
            .setSchemaVersion(CommercialStoreSchema.TANK_DEVICE_ASSIGNMENTS_VERSION)
            .addAssignments(stored(OWNER_A, TANK_A, DEVICE_A, 100L))
            .addAssignments(stored(OWNER_A, TANK_B, DEVICE_B, 200L))
            .addAssignments(stored(OWNER_B, TANK_B, DEVICE_B, 300L))
            .build()

        val mutation = TankDeviceAssignmentRules.repairOwner(
            store = store,
            ownerUid = OWNER_A,
            validTankIds = setOf(TANK_A),
            validDeviceUids = setOf(DEVICE_A)
        )

        assertEquals(1, mutation.removedAssignments.size)
        assertEquals(2, mutation.store.assignmentsCount)
        assertTrue(
            mutation.store.getAssignmentsList().any { assignment ->
                assignment.ownerUid == OWNER_B
            }
        )
    }

    @Test(expected = TankDeviceAssignmentsValidationException::class)
    fun `validation rejects duplicate device assignment for one owner`() {
        val store = TankDeviceAssignmentsStore.newBuilder()
            .setSchemaVersion(CommercialStoreSchema.TANK_DEVICE_ASSIGNMENTS_VERSION)
            .addAssignments(stored(OWNER_A, TANK_A, DEVICE_A, 100L))
            .addAssignments(stored(OWNER_A, TANK_B, DEVICE_A, 200L))
            .build()

        TankDeviceAssignmentRules.validate(store)
    }

    @Test
    fun `validation accepts a policy compliant first recommendation`() {
        val store = storeWithRecommendation(validRecommendation())

        TankDeviceAssignmentRules.validate(store)
        assertEquals(1, store.assignmentsCount)
    }

    @Test
    fun `validation rejects an eight hour first recommendation`() {
        val invalid = validRecommendation().toBuilder()
            .setProgramStartMinute(12 * 60)
            .setPhotoperiodMinutes(8 * 60)
            .build()

        assertThrows(TankDeviceAssignmentsValidationException::class.java) {
            TankDeviceAssignmentRules.validate(storeWithRecommendation(invalid))
        }
    }

    @Test
    fun `validation rejects output above the uncalibrated startup cap`() {
        val invalid = validRecommendation().toBuilder()
            .setRedPercent(31)
            .setWhitePercent(31)
            .setMaximumChannelPercent(31)
            .build()

        assertThrows(TankDeviceAssignmentsValidationException::class.java) {
            TankDeviceAssignmentRules.validate(storeWithRecommendation(invalid))
        }
    }

    @Test
    fun `validation rejects a direct six to eight hour jump`() {
        val invalid = validRecommendation().toBuilder()
            .setProgramStartMinute(12 * 60)
            .setPhotoperiodMinutes(8 * 60)
            .setLastLightingResetEpochDay(TODAY - 100)
            .setPriorAppliedPhotoperiodMinutes(6 * 60)
            .setPriorAppliedMaximumChannelPercent(30)
            .setPriorAppliedEpochDay(TODAY - 14)
            .build()

        assertThrows(TankDeviceAssignmentsValidationException::class.java) {
            TankDeviceAssignmentRules.validate(storeWithRecommendation(invalid))
        }
    }

    private fun emptyStore(): TankDeviceAssignmentsStore {
        return TankDeviceAssignmentsSerializer.defaultValue
    }

    private fun assignedStore(
        ownerUid: String,
        tankId: Long,
        deviceUid: String
    ): TankDeviceAssignmentsStore {
        return TankDeviceAssignmentsStore.newBuilder()
            .setSchemaVersion(CommercialStoreSchema.TANK_DEVICE_ASSIGNMENTS_VERSION)
            .addAssignments(stored(ownerUid, tankId, deviceUid, 100L))
            .build()
    }

    private fun storeWithRecommendation(
        recommendation: StoredLightRecommendationSnapshot
    ): TankDeviceAssignmentsStore = TankDeviceAssignmentsStore.newBuilder()
        .setSchemaVersion(CommercialStoreSchema.TANK_DEVICE_ASSIGNMENTS_VERSION)
        .addAssignments(
            stored(OWNER_A, TANK_A, DEVICE_A, 100L).toBuilder()
                .addLightRecommendations(recommendation)
                .build()
        )
        .build()

    private fun validRecommendation(): StoredLightRecommendationSnapshot =
        StoredLightRecommendationSnapshot.newBuilder()
            .setRecommendationId("slr-0123456789abcdef01234567")
            .setProfileFingerprint("0123456789abcdef")
            .setPolicyVersion(DeviceLightQuickSetupCalculator.POLICY_VERSION)
            .addAllEvidenceSourceIds(
                listOf(
                    "chihiros_light_intensity_guidance",
                    "tropica_growing_in",
                    "tropica_plant_4442",
                    "tropica_plant_database",
                    "tropica_quick_guide"
                )
            )
            .addReasonCodes(DeviceLightPlanReason.EVIDENCE_SIX_HOUR_START.name)
            .addPlantCatalogIds("plant:micranthemum_tweediei_monte_carlo")
            .setProductKey("LIGHT_WRGB_PRO_ELITE")
            .setHardwareRevision("rev-a")
            .setFixtureLengthMm(900)
            .setConfidence(DeviceLightPlanConfidence.CONSERVATIVE_UNCALIBRATED.name)
            .setTankHeightCm(45)
            .setHasPlants(true)
            .setPlantedFreshwater(true)
            .setCo2ComponentPresent(false)
            .setHasShrimp(false)
            .setWaterDepthCm(35)
            .setFixtureHeightAboveWaterCm(10)
            .setPlantDemand(DeviceLightPlantDemand.MEDIUM.name)
            .setPlantCoverage(AquariumPlantCoverage.MEDIUM.name)
            .setCo2Readiness(AquariumCo2Readiness.NOT_INSTALLED.name)
            .setSubstrateSemantic(AquariumSubstrateSemantic.NOT_APPLICABLE.name)
            .setDaylightExposure(AquariumDaylightExposure.LOW.name)
            .setSurfaceGrowth(AquariumSurfaceGrowth.NONE.name)
            .setShelterAvailability(AquariumShelterAvailability.NOT_REQUIRED.name)
            .setProgramStartMinute(14 * 60)
            .setProgramEndMinute(20 * 60)
            .setRampDurationMinutes(60)
            .setPhotoperiodMinutes(6 * 60)
            .setInitialStartPercent(100)
            .setRedPercent(30)
            .setGreenPercent(25)
            .setBluePercent(20)
            .setWhitePercent(30)
            .setMaximumChannelPercent(30)
            .setRecommendationEpochDay(TODAY)
            .setReevaluationEpochDay(TODAY + 21)
            .setLastLightingResetEpochDay(TODAY)
            .setCreatedAtMillis(1_767_225_600_000L)
            .setOutcome(TankLightRecommendationOutcome.PREPARED.name)
            .build()

    private fun stored(
        ownerUid: String,
        tankId: Long,
        deviceUid: String,
        assignedAtMillis: Long
    ): StoredTankDeviceAssignment {
        return StoredTankDeviceAssignment.newBuilder()
            .setOwnerUid(ownerUid)
            .setTankId(tankId)
            .setDeviceUid(deviceUid)
            .setAssignedAtMillis(assignedAtMillis)
            .setLightInstallation(
                StoredLightInstallationProfile.newBuilder()
                    .setContractRevision(TankLightInstallationProfile.CONTRACT_REVISION)
                    .build()
            )
            .build()
    }

    private companion object {
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
        const val DEVICE_A = "device-a"
        const val DEVICE_B = "device-b"
        const val TANK_A = 10L
        const val TANK_B = 20L
        const val TODAY = 20_000L
    }
}
