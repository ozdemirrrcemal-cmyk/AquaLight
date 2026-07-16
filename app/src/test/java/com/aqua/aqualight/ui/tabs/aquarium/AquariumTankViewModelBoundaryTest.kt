package com.aqua.aqualight.ui.tabs.aquarium

import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankDraft
import com.aqua.aqualight.application.aquarium.AquariumTankOperations
import com.aqua.aqualight.application.aquarium.AquariumTankSize
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.DeleteAquariumTanksResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AquariumTankViewModelBoundaryTest {

    @Test
    fun `exposes application tank snapshots and delegates mutations`() = runTest {
        val tank = aquariumTank(id = 42L, name = "Reef")
        val fake = FakeAquariumTankOperations(listOf(tank))
        val viewModel = AquariumTankViewModel(fake)

        assertEquals(listOf(tank), viewModel.tanks.value)

        viewModel.updateTankName(42L, "Reef 2")
        viewModel.updateTankSize(42L, 80, 40, 45, "cm")
        viewModel.updateCareRemindersEnabled(42L, false)

        assertEquals(Triple(42L, "Reef 2", false), fake.latestUpdate)
        assertEquals(AquariumTankSize(80, 40, 45, "cm"), fake.latestSize)
    }

    @Test
    fun `returns typed delete result without exposing data errors`() = runTest {
        val expected = DeleteAquariumTanksResult.Deleted(
            tankIds = listOf(7L),
            cleanupIssues = emptyList()
        )
        val fake = FakeAquariumTankOperations(deleteResult = expected)
        val viewModel = AquariumTankViewModel(fake)

        val actual = viewModel.deleteTanks(listOf(7L, 7L))

        assertSame(expected, actual)
        assertEquals(listOf(7L, 7L), fake.deletedTankIds)
    }
}

private class FakeAquariumTankOperations(
    initialTanks: List<AquariumTankSnapshot> = emptyList(),
    private val deleteResult: DeleteAquariumTanksResult = DeleteAquariumTanksResult.NoOp
) : AquariumTankOperations {
    private val tankFlow = MutableStateFlow(initialTanks)
    override val tanks: Flow<List<AquariumTankSnapshot>> = tankFlow

    var latestUpdate: Triple<Long, String, Boolean>? = null
    var latestSize: AquariumTankSize? = null
    var deletedTankIds: List<Long> = emptyList()

    override suspend fun addTank(draft: AquariumTankDraft): Long = 1L
    override suspend fun duplicateTank(tankId: Long): Long = tankId + 1L
    override suspend fun deleteTanks(tankIds: Collection<Long>): DeleteAquariumTanksResult {
        deletedTankIds = tankIds.toList()
        return deleteResult
    }
    override suspend fun updateTankPhoto(tankId: Long, photoUri: String?) = Unit
    override suspend fun updateTankName(tankId: Long, name: String) {
        latestUpdate = Triple(tankId, name, latestUpdate?.third ?: true)
    }
    override suspend fun updateTankType(tankId: Long, tankType: String) = Unit
    override suspend fun updateTankSize(tankId: Long, size: AquariumTankSize) {
        latestSize = size
    }
    override suspend fun updateTankVolumeUnit(tankId: Long, volumeUnit: String) = Unit
    override suspend fun updateTankSetupDate(tankId: Long, setupDateMillis: Long) = Unit
    override suspend fun updateTankStyle(tankId: Long, tankStyle: String) = Unit
    override suspend fun updateTankDescription(tankId: Long, description: String) = Unit
    override suspend fun updateTankMaterials(
        tankId: Long,
        categoryKey: String,
        materials: List<AquariumMaterialSelection>
    ) = Unit
    override suspend fun updateTankPlants(tankId: Long, plants: List<AquariumPlantTag>) = Unit
    override suspend fun addLivestock(tankId: Long, livestock: AquariumLivestock) = Unit
    override suspend fun updateLivestock(tankId: Long, livestock: AquariumLivestock) = Unit
    override suspend fun removeLivestock(tankId: Long, livestockId: Long) = Unit
    override suspend fun updateSmartCareEnabled(tankId: Long, enabled: Boolean) = Unit
    override suspend fun updateCareRemindersEnabled(tankId: Long, enabled: Boolean) {
        latestUpdate = Triple(tankId, latestUpdate?.second.orEmpty(), enabled)
    }
}

private fun aquariumTank(id: Long, name: String): AquariumTankSnapshot =
    AquariumTankSnapshot(
        id = id,
        name = name,
        description = "",
        photoUri = null,
        setupDateMillis = null,
        widthCm = 80,
        lengthCm = 40,
        heightCm = 45,
        sizeUnit = "cm",
        volumeUnit = "L",
        tankType = "Reef",
        tankStyle = "Mixed reef",
        createdAtMillis = 1L,
        smartCareEnabled = true,
        careRemindersEnabled = true,
        plants = emptyList(),
        materials = emptyList(),
        livestock = emptyList()
    )
