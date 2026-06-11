package com.aqua.aqualight.data.devices.light.programs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.lightProgramsDataStore: DataStore<LightProgramsPreferences> by dataStore(
    fileName = "light_programs.pb",
    serializer = LightProgramsSerializer
)

class LightProgramsDataStoreManager(
    private val context: Context
) {

    val programsFlow: Flow<List<SavedLightProgram>> =
        context.lightProgramsDataStore.data.map { preferences ->
            preferences.programsList
                .filter { proto ->
                    proto.belongsToCurrentUser()
                }
                .map { proto ->
                    LightProgramProtoMapper.fromProto(proto)
                }
        }

    suspend fun saveProgram(
        program: SavedLightProgram
    ) {
        val ownerUid = program.ownerUid.ifBlank {
            UserDataScope.requireCurrentUid()
        }

        val scopedProgram = program.copy(
            ownerUid = ownerUid
        )

        context.lightProgramsDataStore.updateData { preferences ->
            val existingPrograms = preferences.programsList.filterNot { existing ->
                existing.id == scopedProgram.id && existing.belongsToOwner(ownerUid)
            }

            preferences.toBuilder()
                .clearPrograms()
                .addAllPrograms(existingPrograms)
                .addPrograms(
                    LightProgramProtoMapper.toProto(scopedProgram)
                )
                .build()
        }
    }

    suspend fun getProgram(
        programId: String
    ): SavedLightProgram? {
        return programsFlow
            .map { programs ->
                programs.firstOrNull { program ->
                    program.id == programId
                }
            }
            .first()
    }

    suspend fun deleteProgram(
        programId: String
    ) {
        context.lightProgramsDataStore.updateData { preferences ->
            val remainingPrograms = preferences.programsList.filterNot { program ->
                program.id == programId && program.belongsToCurrentUser()
            }

            preferences.toBuilder()
                .clearPrograms()
                .addAllPrograms(remainingPrograms)
                .build()
        }
    }

    suspend fun clearAllPrograms(
        ownerUid: String? = null
    ) {
        val targetOwnerUid = ownerUid.orCurrentOwnerUidOrReturn()

        context.lightProgramsDataStore.updateData { preferences ->
            val remainingPrograms = preferences.programsList.filterNot { program ->
                program.belongsToOwner(targetOwnerUid)
            }

            preferences.toBuilder()
                .clearPrograms()
                .addAllPrograms(remainingPrograms)
                .build()
        }
    }

    suspend fun assignLegacyProgramsToOwner(
        ownerUid: String
    ) {
        val targetOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)

        if (targetOwnerUid.isBlank()) {
            return
        }

        context.lightProgramsDataStore.updateData { preferences ->
            val updatedPrograms = preferences.programsList.map { program ->
                if (program.ownerUid.isBlank()) {
                    program.toBuilder()
                        .setOwnerUid(targetOwnerUid)
                        .build()
                } else {
                    program
                }
            }

            preferences.toBuilder()
                .clearPrograms()
                .addAllPrograms(updatedPrograms)
                .build()
        }
    }

    private fun String?.orCurrentOwnerUidOrReturn(): String {
        val explicitOwnerUid = UserDataScope.normalizeOwnerUid(this)

        if (explicitOwnerUid.isNotBlank()) {
            return explicitOwnerUid
        }

        return UserDataScope.currentUid()
    }

    private fun LightProgram.belongsToCurrentUser(): Boolean {
        return UserDataScope.belongsToCurrentUser(
            recordOwnerUid = ownerUid
        )
    }

    private fun LightProgram.belongsToOwner(
        ownerUid: String
    ): Boolean {
        return UserDataScope.belongsToOwner(
            recordOwnerUid = this.ownerUid,
            ownerUid = ownerUid
        )
    }
}