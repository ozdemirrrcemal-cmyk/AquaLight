package com.aqua.aqualight.application.aquarium

import kotlinx.coroutines.flow.Flow

/** Application boundary for owner-scoped aquarium tank operations. */
interface AquariumTankOperations {
    val tanks: Flow<List<AquariumTankSnapshot>>

    suspend fun addTank(draft: AquariumTankDraft): Long
    suspend fun duplicateTank(tankId: Long): Long
    suspend fun deleteTanks(tankIds: Collection<Long>): DeleteAquariumTanksResult
    suspend fun updateTankPhoto(tankId: Long, photoUri: String?)
    suspend fun updateTankName(tankId: Long, name: String)
    suspend fun updateTankType(tankId: Long, tankType: String)
    suspend fun updateTankSize(tankId: Long, size: AquariumTankSize)
    suspend fun updateTankVolumeUnit(tankId: Long, volumeUnit: String)
    suspend fun updateTankSetupDate(tankId: Long, setupDateMillis: Long)
    suspend fun updateTankStyle(tankId: Long, tankStyle: String)
    suspend fun updateTankDescription(tankId: Long, description: String)
    suspend fun updateTankMaterials(
        tankId: Long,
        categoryKey: String,
        materials: List<AquariumMaterialSelection>
    )
    suspend fun updateTankPlants(tankId: Long, plants: List<AquariumPlantTag>)
    suspend fun addLivestock(tankId: Long, livestock: AquariumLivestock)
    suspend fun updateLivestock(tankId: Long, livestock: AquariumLivestock)
    suspend fun removeLivestock(tankId: Long, livestockId: Long)
    suspend fun updateSmartCareEnabled(tankId: Long, enabled: Boolean)
    suspend fun updateCareRemindersEnabled(tankId: Long, enabled: Boolean)
}

data class AquariumTankSnapshot(
    val id: Long,
    val name: String,
    val description: String,
    val photoUri: String?,
    val setupDateMillis: Long?,
    val widthCm: Int,
    val lengthCm: Int,
    val heightCm: Int,
    val sizeUnit: String,
    val volumeUnit: String,
    val tankType: String,
    val tankStyle: String,
    val createdAtMillis: Long,
    val smartCareEnabled: Boolean,
    val careRemindersEnabled: Boolean,
    val plants: List<AquariumPlantTag>,
    val materials: List<AquariumMaterialSelection>,
    val livestock: List<AquariumLivestock>
)

data class AquariumTankDraft(
    val name: String = "",
    val description: String = "",
    val photoUri: String? = null,
    val plants: List<AquariumPlantTag> = emptyList(),
    val materials: List<AquariumMaterialSelection> = emptyList(),
    val info: String = "",
    val setupDateMillis: Long? = null,
    val widthCm: Int = 10,
    val lengthCm: Int = 10,
    val heightCm: Int = 10,
    val sizeUnit: String = "cm",
    val volumeUnit: String = "L",
    val tankType: String = "",
    val tankStyle: String = ""
)

data class AquariumTankSize(
    val widthCm: Int,
    val lengthCm: Int,
    val heightCm: Int,
    val sizeUnit: String
)

data class AquariumPlantTag(
    val id: Long,
    val plantName: String,
    val category: String,
    val markerX: Float,
    val markerY: Float
)

data class AquariumMaterialSelection(
    val id: Long,
    val productId: String,
    val categoryKey: String,
    val categoryTitle: String,
    val name: String,
    val brand: String,
    val note: String
)

data class AquariumLivestock(
    val id: Long,
    val name: String,
    val category: String,
    val quantity: Int,
    val addedDateMillis: Long?,
    val note: String
)

sealed interface DeleteAquariumTanksResult {
    data object NoOp : DeleteAquariumTanksResult
    data object DeleteFailed : DeleteAquariumTanksResult
    data class Deleted(
        val tankIds: List<Long>,
        val cleanupIssues: List<AquariumTankCleanupIssue>
    ) : DeleteAquariumTanksResult {
        val hasCleanupIssues: Boolean
            get() = cleanupIssues.isNotEmpty()
    }
}

data class AquariumTankCleanupIssue(
    val tankId: Long,
    val stage: AquariumTankCleanupStage
)

enum class AquariumTankCleanupStage {
    CARE_TASKS,
    DEVICE_ASSIGNMENTS
}
