package com.aqua.aqualight.data.devices.light.presets

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.light.presets.model.SavedLightPreset
import com.aqua.aqualight.data.user.UserDataScope
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
            preferences.presetsList
                .filter { proto ->
                    proto.belongsToCurrentUser()
                }
                .map { proto ->
                    LightPresetProtoMapper.fromProto(proto)
                }
        }

    suspend fun savePreset(
        preset: SavedLightPreset
    ) {
        val ownerUid = preset.ownerUid.ifBlank {
            UserDataScope.requireCurrentUid()
        }

        val scopedPreset = preset.copy(
            ownerUid = ownerUid
        )

        context.lightPresetDataStore.updateData { preferences ->
            val currentPresets = preferences.presetsList.toMutableList()
            val proto = LightPresetProtoMapper.toProto(scopedPreset)

            val index = currentPresets.indexOfFirst {
                it.id == scopedPreset.id && it.belongsToOwner(ownerUid)
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
                it.id == presetId && it.belongsToCurrentUser()
            }

            LightPresetsPreferences
                .newBuilder()
                .addAllPresets(updatedPresets)
                .build()
        }
    }

    suspend fun clearAllPresets(
        ownerUid: String? = null
    ) {
        val targetOwnerUid = ownerUid.orCurrentOwnerUidOrReturn()

        context.lightPresetDataStore.updateData { preferences ->
            val remainingPresets = preferences.presetsList.filterNot { preset ->
                preset.belongsToOwner(targetOwnerUid)
            }

            preferences.toBuilder()
                .clearPresets()
                .addAllPresets(remainingPresets)
                .build()
        }
    }

    suspend fun assignLegacyPresetsToOwner(
        ownerUid: String
    ) {
        val targetOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)

        if (targetOwnerUid.isBlank()) {
            return
        }

        context.lightPresetDataStore.updateData { preferences ->
            val updatedPresets = preferences.presetsList.map { preset ->
                if (preset.ownerUid.isBlank()) {
                    preset.toBuilder()
                        .setOwnerUid(targetOwnerUid)
                        .build()
                } else {
                    preset
                }
            }

            preferences.toBuilder()
                .clearPresets()
                .addAllPresets(updatedPresets)
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

    private fun LightPresetProto.belongsToCurrentUser(): Boolean {
        return UserDataScope.belongsToCurrentUser(
            recordOwnerUid = ownerUid
        )
    }

    private fun LightPresetProto.belongsToOwner(
        ownerUid: String
    ): Boolean {
        return UserDataScope.belongsToOwner(
            recordOwnerUid = this.ownerUid,
            ownerUid = ownerUid
        )
    }
}