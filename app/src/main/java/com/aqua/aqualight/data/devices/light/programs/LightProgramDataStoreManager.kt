package com.aqua.aqualight.data.devices.light.programs

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramCompileResult
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramScheduleCompiler
import com.aqua.aqualight.data.devices.light.programs.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.light.programs.store.LightProgramsStore
import com.aqua.aqualight.data.devices.light.programs.store.StoredLightProgram
import com.aqua.aqualight.data.user.UserDataScope
import com.google.protobuf.InvalidProtocolBufferException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class LightProgramDataStoreManager private constructor(
    private val dataStore: DataStore<LightProgramsStore>,
    private val devicesStore: DevicesDataStoreManager
) {

    companion object {
        @Volatile
        private var INSTANCE: LightProgramDataStoreManager? = null

        fun create(
            context: Context
        ): LightProgramDataStoreManager {
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
        ): LightProgramDataStoreManager {
            val dataStore = DataStoreFactory.create(
                serializer = LightProgramsSerializer,
                scope = CoroutineScope(
                    SupervisorJob() + Dispatchers.IO
                ),
                produceFile = {
                    appContext.dataStoreFile("light_programs.pb")
                }
            )

            return LightProgramDataStoreManager(
                dataStore = dataStore,
                devicesStore = DevicesDataStoreManager.create(appContext)
            )
        }
    }

    val programsFlow: Flow<List<SavedLightProgram>> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(LightProgramsStore.getDefaultInstance())
                } else {
                    throw exception
                }
            }
            .map { store ->
                store.programsList
                    .filter { program -> program.belongsToCurrentUser() }
                    .map { program -> program.toSavedLightProgram() }
                    .sortedWith(
                        compareByDescending<SavedLightProgram> { program -> if (program.active) 1 else 0 }
                            .thenByDescending { program -> program.updatedAt }
                            .thenBy { program -> program.name.lowercase() }
                    )
            }

    fun programsForDeviceFlow(
        deviceId: Long
    ): Flow<List<SavedLightProgram>> {
        return programsFlow.map { programs ->
            programs.filter { program -> program.deviceId == deviceId }
        }
    }

    suspend fun findProgram(
        deviceId: Long,
        programId: String
    ): SavedLightProgram? {
        if (deviceId <= 0L || programId.isBlank()) return null
        return programsFlow.first().firstOrNull { program ->
            program.deviceId == deviceId && program.id == programId
        }
    }

    suspend fun saveProgram(
        deviceId: Long,
        programId: String?,
        name: String,
        draft: LightProgramDraft,
        activate: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedLightProgram {
        val safeName = name.trim()
        require(safeName.isNotBlank()) {
            "Program name is required"
        }
        require(deviceId > 0L) {
            "Light device id is missing"
        }

        val compiled = when (val result = LightProgramScheduleCompiler.compile(
            draft = draft,
            programId = programId.orEmpty(),
            programName = safeName
        )) {
            is LightProgramCompileResult.Valid -> result.schedule
            is LightProgramCompileResult.Invalid -> throw IllegalArgumentException(result.message)
        }

        val device = devicesStore.devicesFlow.first()
            .firstOrNull { storedDevice -> storedDevice.id == deviceId }

        val ownerUid = UserDataScope.currentUid()
        val existing = programId
            ?.takeIf { id -> id.isNotBlank() }
            ?.let { id -> findProgram(deviceId, id) }

        val savedProgram = SavedLightProgram(
            id = existing?.id ?: buildProgramId(
                deviceId = deviceId,
                nowMillis = nowMillis
            ),
            ownerUid = ownerUid,
            deviceId = deviceId,
            deviceUid = device?.deviceUid.orEmpty(),
            productId = device?.productId.orEmpty(),
            name = safeName,
            active = activate,
            startMinute = compiled.startMinute,
            peakStartMinute = compiled.peakStartMinute,
            peakEndMinute = compiled.peakEndMinute,
            endMinute = compiled.endMinute,
            red = compiled.peakChannels.red,
            green = compiled.peakChannels.green,
            blue = compiled.peakChannels.blue,
            white = compiled.peakChannels.white,
            repeatMode = compiled.repeatMode,
            repeatDays = compiled.repeatDays,
            transitionMode = compiled.transitionMode,
            createdAt = existing?.createdAt ?: nowMillis,
            updatedAt = nowMillis
        )

        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPrograms()

            var replaced = false
            store.programsList.forEach { stored ->
                val sameProgram = stored.id == savedProgram.id &&
                    stored.deviceId == deviceId &&
                    stored.belongsToCurrentUser()

                when {
                    sameProgram -> {
                        builder.addPrograms(savedProgram.toStoredLightProgram())
                        replaced = true
                    }

                    activate && stored.deviceId == deviceId && stored.belongsToCurrentUser() -> {
                        builder.addPrograms(
                            stored.toBuilder()
                                .setActive(false)
                                .build()
                        )
                    }

                    else -> builder.addPrograms(stored)
                }
            }

            if (!replaced) {
                builder.addPrograms(savedProgram.toStoredLightProgram())
            }

            builder.build()
        }

        return savedProgram
    }

    suspend fun setProgramActive(
        deviceId: Long,
        programId: String,
        active: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (deviceId <= 0L || programId.isBlank()) return false

        var found = false
        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { stored ->
                val isSameDevice = stored.deviceId == deviceId && stored.belongsToCurrentUser()
                val isTarget = isSameDevice && stored.id == programId

                val updated = when {
                    isTarget -> {
                        found = true
                        stored.toBuilder()
                            .setActive(active)
                            .setUpdatedAtMillis(nowMillis)
                            .build()
                    }

                    active && isSameDevice -> {
                        stored.toBuilder()
                            .setActive(false)
                            .setUpdatedAtMillis(nowMillis)
                            .build()
                    }

                    else -> stored
                }

                builder.addPrograms(updated)
            }

            builder.build()
        }

        return found
    }

    suspend fun renameProgram(
        deviceId: Long,
        programId: String,
        newName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val safeName = newName.trim()
        if (deviceId <= 0L || programId.isBlank() || safeName.isBlank()) return false

        var renamed = false
        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { stored ->
                val shouldRename = stored.deviceId == deviceId &&
                    stored.id == programId &&
                    stored.belongsToCurrentUser()

                if (shouldRename) {
                    renamed = true
                    builder.addPrograms(
                        stored.toBuilder()
                            .setName(safeName)
                            .setUpdatedAtMillis(nowMillis)
                            .build()
                    )
                } else {
                    builder.addPrograms(stored)
                }
            }

            builder.build()
        }

        return renamed
    }

    suspend fun duplicateProgram(
        deviceId: Long,
        programId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedLightProgram? {
        val original = findProgram(deviceId, programId) ?: return null
        return saveProgram(
            deviceId = deviceId,
            programId = null,
            name = "${original.name} Copy",
            draft = original.toDraft(),
            activate = false,
            nowMillis = nowMillis
        )
    }

    suspend fun deleteProgram(
        deviceId: Long,
        programId: String
    ): Boolean {
        if (deviceId <= 0L || programId.isBlank()) return false

        var deleted = false
        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { stored ->
                val shouldDelete = stored.deviceId == deviceId &&
                    stored.id == programId &&
                    stored.belongsToCurrentUser()

                if (shouldDelete) {
                    deleted = true
                } else {
                    builder.addPrograms(stored)
                }
            }

            builder.build()
        }

        return deleted
    }

    private fun StoredLightProgram.belongsToCurrentUser(): Boolean {
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

    private fun StoredLightProgram.toSavedLightProgram(): SavedLightProgram {
        return SavedLightProgram(
            id = id,
            ownerUid = ownerUid,
            deviceId = deviceId,
            deviceUid = deviceUid,
            productId = productId,
            name = name,
            active = active,
            startMinute = startMinute.coerceIn(0, MINUTES_PER_DAY),
            peakStartMinute = peakStartMinute.coerceIn(0, MINUTES_PER_DAY),
            peakEndMinute = peakEndMinute.coerceIn(0, MINUTES_PER_DAY),
            endMinute = endMinute.coerceIn(0, MINUTES_PER_DAY),
            red = red.coerceIn(0, 100),
            green = green.coerceIn(0, 100),
            blue = blue.coerceIn(0, 100),
            white = white.coerceIn(0, 100),
            repeatMode = parseRepeatMode(repeatMode),
            repeatDays = repeatDaysList
                .filter { day -> day in 1..7 }
                .toSet()
                .ifEmpty { ALL_DAYS },
            transitionMode = parseTransitionMode(transitionMode),
            createdAt = createdAtMillis,
            updatedAt = updatedAtMillis
        )
    }

    private fun SavedLightProgram.toStoredLightProgram(): StoredLightProgram {
        val builder = StoredLightProgram.newBuilder()
            .setId(id)
            .setOwnerUid(ownerUid)
            .setDeviceId(deviceId)
            .setDeviceUid(deviceUid)
            .setProductId(productId)
            .setName(name)
            .setActive(active)
            .setStartMinute(startMinute.coerceIn(0, MINUTES_PER_DAY))
            .setPeakStartMinute(peakStartMinute.coerceIn(0, MINUTES_PER_DAY))
            .setPeakEndMinute(peakEndMinute.coerceIn(0, MINUTES_PER_DAY))
            .setEndMinute(endMinute.coerceIn(0, MINUTES_PER_DAY))
            .setRed(red.coerceIn(0, 100))
            .setGreen(green.coerceIn(0, 100))
            .setBlue(blue.coerceIn(0, 100))
            .setWhite(white.coerceIn(0, 100))
            .setRepeatMode(repeatMode.name)
            .setTransitionMode(transitionMode.name)
            .setCreatedAtMillis(createdAt)
            .setUpdatedAtMillis(updatedAt)
            .clearRepeatDays()

        repeatDays
            .filter { day -> day in 1..7 }
            .sorted()
            .forEach { day -> builder.addRepeatDays(day) }

        return builder.build()
    }

    private fun parseRepeatMode(
        value: String
    ): RepeatMode {
        return runCatching { RepeatMode.valueOf(value) }
            .getOrDefault(RepeatMode.EVERY)
    }

    private fun parseTransitionMode(
        value: String
    ): LightCurveTransitionMode {
        return runCatching { LightCurveTransitionMode.valueOf(value) }
            .getOrDefault(LightCurveTransitionMode.NATURAL)
    }

    private fun buildProgramId(
        deviceId: Long,
        nowMillis: Long
    ): String {
        return "program_${deviceId}_${nowMillis}_${UUID.randomUUID()}"
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
        val ALL_DAYS = setOf(1, 2, 3, 4, 5, 6, 7)
    }
}

private object LightProgramsSerializer : Serializer<LightProgramsStore> {

    override val defaultValue: LightProgramsStore =
        LightProgramsStore.getDefaultInstance()

    override suspend fun readFrom(
        input: InputStream
    ): LightProgramsStore {
        return try {
            LightProgramsStore.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                message = "Cannot read light programs proto.",
                cause = exception
            )
        }
    }

    override suspend fun writeTo(
        t: LightProgramsStore,
        output: OutputStream
    ) {
        t.writeTo(output)
    }
}
