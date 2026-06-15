package com.aqua.aqualight.data.devices.light.presets

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.presets.store.LightPresetsStore
import com.aqua.aqualight.data.devices.light.presets.store.StoredLightPreset
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.color.LightRgbwChannels
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.presets.model.SavedLightPreset
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class LightPresetDataStoreManager private constructor(
    private val dataStore: DataStore<LightPresetsStore>,
    private val devicesStore: DevicesDataStoreManager
) {

    companion object {
        @Volatile
        private var INSTANCE: LightPresetDataStoreManager? = null

        fun create(
            context: Context
        ): LightPresetDataStoreManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDataStore(
                    appContext = context.applicationContext
                ).also { manager ->
                    INSTANCE = manager
                }
            }
        }

        private fun buildDataStore(
            appContext: Context
        ): LightPresetDataStoreManager {
            val dataStore = DataStoreFactory.create(
                serializer = LightPresetsSerializer,
                scope = CoroutineScope(
                    SupervisorJob() + Dispatchers.IO
                ),
                produceFile = {
                    appContext.dataStoreFile("light_presets.pb")
                }
            )

            return LightPresetDataStoreManager(
                dataStore = dataStore,
                devicesStore = DevicesDataStoreManager.create(appContext)
            )
        }
    }

    val presetsFlow: Flow<List<SavedLightPreset>> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(LightPresetsStore.getDefaultInstance())
                } else {
                    throw exception
                }
            }
            .map { store ->
                store.presetsList
                    .filter { preset ->
                        preset.belongsToCurrentUser()
                    }
                    .map { preset ->
                        preset.toSavedLightPreset()
                    }
                    .sortedWith(
                        compareByDescending<SavedLightPreset> { preset ->
                            preset.updatedAt
                        }.thenBy { preset ->
                            preset.name.lowercase()
                        }
                    )
            }

    fun presetsForDeviceFlow(
        deviceId: Long
    ): Flow<List<SavedLightPreset>> {
        return presetsFlow.map { presets ->
            presets.filter { preset ->
                preset.deviceId == deviceId
            }
        }
    }

    suspend fun savePreset(
        deviceId: Long,
        name: String,
        channels: LightRgbwChannels,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedLightPreset {
        val safeName = name.trim()
        require(safeName.isNotBlank()) {
            "Preset name is required"
        }
        require(deviceId > 0L) {
            "Light device id is missing"
        }

        val device = devicesStore.devicesFlow.first()
            .firstOrNull { storedDevice ->
                storedDevice.id == deviceId
            }

        val ownerUid = UserDataScope.currentUid()
        val safeChannels = channels.sanitized()
        val preset = SavedLightPreset(
            id = buildPresetId(
                deviceId = deviceId,
                nowMillis = nowMillis
            ),
            ownerUid = ownerUid,
            deviceId = deviceId,
            deviceUid = device?.deviceUid.orEmpty(),
            productId = device?.productId.orEmpty(),
            name = safeName,
            red = safeChannels.safeRed,
            green = safeChannels.safeGreen,
            blue = safeChannels.safeBlue,
            white = safeChannels.safeWhite,
            createdAt = nowMillis,
            updatedAt = nowMillis
        )

        dataStore.updateData { store ->
            store.toBuilder()
                .addPresets(preset.toStoredLightPreset())
                .build()
        }

        return preset
    }

    suspend fun deletePreset(
        presetId: String,
        deviceId: Long
    ): Boolean {
        if (presetId.isBlank() || deviceId <= 0L) return false

        var deleted = false
        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPresets()

            store.presetsList.forEach { preset ->
                val shouldDelete = preset.id == presetId &&
                    preset.deviceId == deviceId &&
                    preset.belongsToCurrentUser()

                if (shouldDelete) {
                    deleted = true
                } else {
                    builder.addPresets(preset)
                }
            }

            builder.build()
        }

        return deleted
    }

    private fun StoredLightPreset.belongsToCurrentUser(): Boolean {
        val currentUid = UserDataScope.currentUid()
        if (currentUid.isBlank()) {
            return ownerUid.isBlank()
        }

        return UserDataScope.belongsToOwner(
            recordOwnerUid = ownerUid,
            ownerUid = currentUid,
            includeLegacy = true
        )
    }

    private fun StoredLightPreset.toSavedLightPreset(): SavedLightPreset {
        return SavedLightPreset(
            id = id,
            ownerUid = ownerUid,
            deviceId = deviceId,
            deviceUid = deviceUid,
            productId = productId,
            name = name,
            red = red.coerceIn(0, 100),
            green = green.coerceIn(0, 100),
            blue = blue.coerceIn(0, 100),
            white = white.coerceIn(0, 100),
            createdAt = createdAtMillis,
            updatedAt = updatedAtMillis
        )
    }

    private fun SavedLightPreset.toStoredLightPreset(): StoredLightPreset {
        return StoredLightPreset.newBuilder()
            .setId(id)
            .setOwnerUid(ownerUid)
            .setDeviceId(deviceId)
            .setDeviceUid(deviceUid)
            .setProductId(productId)
            .setName(name)
            .setRed(red.coerceIn(0, 100))
            .setGreen(green.coerceIn(0, 100))
            .setBlue(blue.coerceIn(0, 100))
            .setWhite(white.coerceIn(0, 100))
            .setCreatedAtMillis(createdAt)
            .setUpdatedAtMillis(updatedAt)
            .build()
    }

    private fun LightRgbwChannels.sanitized(): LightRgbwChannels {
        return LightRgbwChannels(
            red = safeRed,
            green = safeGreen,
            blue = safeBlue,
            white = safeWhite
        )
    }

    private fun buildPresetId(
        deviceId: Long,
        nowMillis: Long
    ): String {
        return "preset_${deviceId}_${nowMillis}_${UUID.randomUUID()}"
    }
}

private object LightPresetsSerializer : Serializer<LightPresetsStore> {

    override val defaultValue: LightPresetsStore =
        LightPresetsStore.getDefaultInstance()

    override suspend fun readFrom(
        input: InputStream
    ): LightPresetsStore {
        return try {
            LightPresetsStore.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                message = "Cannot read light presets proto.",
                cause = exception
            )
        }
    }

    override suspend fun writeTo(
        t: LightPresetsStore,
        output: OutputStream
    ) {
        t.writeTo(output)
    }
}
