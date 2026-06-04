package com.aqua.aqualight.data.devices.light.programs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.lightProgramsDataStore: DataStore<LightProgramsPreferences> by dataStore(
    fileName = "light_programs.pb",
    serializer = LightProgramsSerializer
)

class LightProgramsDataStoreManager(
    private val context: Context
) {

    val programsFlow: Flow<List<SavedLightProgram>> =
    context.lightProgramsDataStore.data.map {
        preferences ->
        preferences.programsList.map {
            proto ->
            LightProgramProtoMapper.fromProto(proto)
        }
    }

    suspend fun saveProgram(
        program: SavedLightProgram
    ) {
        context.lightProgramsDataStore.updateData {
            preferences ->
            val existingPrograms = preferences.programsList
            .filterNot {
                it.id == program.id
            }

            val finalPrograms = if (program.isActive) {
                existingPrograms.map {
                    existing ->
                    existing.toBuilder()
                    .setIsActive(false)
                    .build()
                }
            } else {
                existingPrograms
            }

            preferences.toBuilder()
            .clearPrograms()
            .addAllPrograms(finalPrograms)
            .addPrograms(LightProgramProtoMapper.toProto(program))
            .build()
        }
    }

    suspend fun getProgram(
        programId: String
    ): SavedLightProgram? {
        return programsFlow
        .map {
            programs ->
            programs.firstOrNull {
                it.id == programId
            }
        }
        .first()
    }

    suspend fun deleteProgram(
        programId: String
    ) {
        context.lightProgramsDataStore.updateData {
            preferences ->
            preferences.toBuilder()
            .clearPrograms()
            .addAllPrograms(
                preferences.programsList.filterNot {
                    it.id == programId
                }
            )
            .build()
        }
    }

    suspend fun setActiveProgram(
        programId: String
    ) {
        context.lightProgramsDataStore.updateData {
            preferences ->
            preferences.toBuilder()
            .clearPrograms()
            .addAllPrograms(
                preferences.programsList.map {
                    program ->
                    program.toBuilder()
                    .setIsActive(program.id == programId)
                    .setUpdatedAt(System.currentTimeMillis())
                    .build()
                }
            )
            .build()
        }
    }
}