package com.aqua.aqualight.data.devices.light.programs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
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
            preferences.programsList.map { proto ->
                LightProgramProtoMapper.fromProto(proto)
            }
        }

    suspend fun saveProgram(
        program: SavedLightProgram
    ) {
        context.lightProgramsDataStore.updateData { preferences ->
            val existingPrograms = preferences.programsList.filterNot { existing ->
                existing.id == program.id
            }

            preferences.toBuilder()
                .clearPrograms()
                .addAllPrograms(existingPrograms)
                .addPrograms(
                    LightProgramProtoMapper.toProto(program)
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
                program.id == programId
            }

            preferences.toBuilder()
                .clearPrograms()
                .addAllPrograms(remainingPrograms)
                .build()
        }
    }
}