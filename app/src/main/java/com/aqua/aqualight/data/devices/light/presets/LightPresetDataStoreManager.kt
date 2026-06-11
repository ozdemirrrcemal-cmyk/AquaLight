package com.aqua.aqualight.data.devices.light.presets

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.light.presets.model.SavedLightPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lightPresetDataStore: DataStore<LightPresetsPreferences> by dataStore(
    fileName = "light_presets_preferences.pb",
    serializer = LightPresetDataStoreSerializer
)

class LightPresetDataStoreManager(
    private val context: Context
) {

    val presetsFlow: Flow<List<SavedLightPreset>> =
        context.lightPresetDataStore.data.map { preferences ->
            preferences.presetsList.map { proto ->
                LightPresetProtoMapper.fromProto(proto)
            }
        }

    suspend fun savePreset(
        preset: SavedLightPreset
    ) {
        context.lightPresetDataStore.updateData { preferences ->
            val currentPresets = preferences.presetsList.toMutableList()
            val proto = LightPresetProtoMapper.toProto(preset)

            val index = currentPresets.indexOfFirst {
                it.id == preset.id
            }

            if (index >= 0) {
                currentPresets[index] = proto
            } else {
                currentPresets.add(proto)
            }

            LightPresetsPreferences
                .newBuilder()
                .addAllPresets(currentPresets)
                .build()
        }
    }

    suspend fun deletePreset(
        presetId: String
    ) {
        context.lightPresetDataStore.updateData { preferences ->
            val updatedPresets = preferences.presetsList.filterNot {
                it.id == presetId
            }

            LightPresetsPreferences
                .newBuilder()
                .addAllPresets(updatedPresets)
                .build()
        }
    }

    suspend fun clearAllPresets() {
        context.lightPresetDataStore.updateData { preferences ->
            preferences.toBuilder()
                .clearPresets()
                .build()
        }
    }
}