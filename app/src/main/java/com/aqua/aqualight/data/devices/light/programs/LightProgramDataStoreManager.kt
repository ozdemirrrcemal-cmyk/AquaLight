package com.aqua.aqualight.data.devices.light.programs

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramRepeatMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramSyncState
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTransitionMode
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
                    .filter { program ->
                        program.belongsToCurrentUser()
                    }
                    .map { program ->
                        program.toSavedLightProgram()
                    }
                    .sortedWith(
                        compareByDescending<SavedLightProgram> { program ->
                            program.updatedAtMillis
                        }.thenBy { program ->
                            program.name.lowercase()
                        }
                    )
            }

    fun programsForDeviceFlow(
        deviceId: Long
    ): Flow<List<SavedLightProgram>> {
        return programsFlow.map { programs ->
            programs.filter { program ->
                program.deviceId == deviceId
            }
        }
    }

    suspend fun getProgram(
        deviceId: Long,
        programId: String
    ): SavedLightProgram? {
        if (deviceId <= 0L || programId.isBlank()) return null
        return programsFlow.first().firstOrNull { program ->
            program.deviceId == deviceId && program.id == programId
        }
    }

    suspend fun saveDraft(
        deviceId: Long,
        programId: String?,
        name: String,
        draft: LightProgramDraft,
        syncState: LightProgramSyncState? = null,
        active: Boolean? = null,
        lastLoadedAtMillis: Long? = null,
        lastLoadedHash: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedLightProgram {
        val safeName = name.trim()
        require(safeName.isNotBlank()) {
            "Program name is required"
        }
        require(deviceId > 0L) {
            "Light device id is missing"
        }

        val currentUid = UserDataScope.currentUid()
        val device = devicesStore.devicesFlow.first()
            .firstOrNull { storedDevice ->
                storedDevice.id == deviceId
            }

        val safeProgramId = programId
            ?.takeIf { id -> id.isNotBlank() }
            ?: buildProgramId(
                deviceId = deviceId,
                nowMillis = nowMillis
            )

        var savedProgram: SavedLightProgram? = null

        dataStore.updateData { store ->
            val existing = store.programsList.firstOrNull { program ->
                program.id == safeProgramId &&
                    program.deviceId == deviceId &&
                    program.belongsToCurrentUser()
            }

            val wasActive = existing?.isActive ?: false
            val resolvedSyncState = syncState ?: when {
                wasActive -> LightProgramSyncState.ACTIVE_DIRTY
                else -> LightProgramSyncState.LOCAL_ONLY
            }

            val resolvedActive = active ?: wasActive
            val program = SavedLightProgram(
                id = safeProgramId,
                ownerUid = existing?.ownerUid?.takeIf { it.isNotBlank() } ?: currentUid,
                deviceId = deviceId,
                deviceUid = device?.deviceUid.orEmpty(),
                productId = device?.productId.orEmpty(),
                name = safeName,
                isActive = resolvedActive,
                startMinute = draft.startMinute.coerceIn(0, 24 * 60 - 1),
                peakStartMinute = draft.peakStartMinute.coerceIn(0, 24 * 60 - 1),
                peakEndMinute = draft.peakEndMinute.coerceIn(0, 24 * 60 - 1),
                endMinute = draft.endMinute.coerceIn(1, 24 * 60),
                red = draft.red.coerceIn(0, 100),
                green = draft.green.coerceIn(0, 100),
                blue = draft.blue.coerceIn(0, 100),
                white = draft.white.coerceIn(0, 100),
                repeatMode = draft.repeatMode,
                selectedDays = draft.selectedDays.filter { day -> day in 1..7 }.toSet().ifEmpty { SavedLightProgram.ALL_DAYS },
                transitionMode = draft.transitionMode,
                syncState = resolvedSyncState,
                createdAtMillis = existing?.createdAtMillis ?: nowMillis,
                updatedAtMillis = nowMillis,
                lastLoadedAtMillis = lastLoadedAtMillis ?: existing?.lastLoadedAtMillis ?: 0L,
                lastLoadedHash = lastLoadedHash ?: existing?.lastLoadedHash.orEmpty()
            )

            savedProgram = program

            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { stored ->
                if (stored.id != safeProgramId || stored.deviceId != deviceId || !stored.belongsToCurrentUser()) {
                    builder.addPrograms(stored)
                }
            }

            builder.addPrograms(program.toStoredLightProgram())
                .build()
        }

        return requireNotNull(savedProgram)
    }

    suspend fun replaceAfterSuccessfulLoad(
        loadedProgram: SavedLightProgram,
        compiledHash: String,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedLightProgram {
        val syncedProgram = loadedProgram.copy(
            isActive = true,
            syncState = LightProgramSyncState.ACTIVE_SYNCED,
            updatedAtMillis = nowMillis,
            lastLoadedAtMillis = nowMillis,
            lastLoadedHash = compiledHash
        )

        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { stored ->
                val sameDevice = stored.deviceId == syncedProgram.deviceId
                val currentUserProgram = stored.belongsToCurrentUser()

                val nextStored = when {
                    stored.id == syncedProgram.id && sameDevice && currentUserProgram -> {
                        syncedProgram.toStoredLightProgram()
                    }

                    sameDevice && currentUserProgram -> {
                        stored.toBuilder()
                            .setIsActive(false)
                            .setSyncState(LightProgramSyncState.LOCAL_ONLY.name)
                            .setUpdatedAtMillis(nowMillis)
                            .build()
                    }

                    else -> stored
                }

                builder.addPrograms(nextStored)
            }

            if (store.programsList.none { stored ->
                    stored.id == syncedProgram.id &&
                        stored.deviceId == syncedProgram.deviceId &&
                        stored.belongsToCurrentUser()
                }) {
                builder.addPrograms(syncedProgram.toStoredLightProgram())
            }

            builder.build()
        }

        return syncedProgram
    }

    suspend fun markSyncFailed(
        deviceId: Long,
        programId: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (deviceId <= 0L || programId.isBlank()) return

        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { stored ->
                val shouldUpdate = stored.id == programId &&
                    stored.deviceId == deviceId &&
                    stored.belongsToCurrentUser()

                builder.addPrograms(
                    if (shouldUpdate) {
                        stored.toBuilder()
                            .setSyncState(LightProgramSyncState.SYNC_FAILED.name)
                            .setUpdatedAtMillis(nowMillis)
                            .build()
                    } else {
                        stored
                    }
                )
            }

            builder.build()
        }
    }

    suspend fun setProgramActiveLocal(
        deviceId: Long,
        programId: String,
        isActive: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (deviceId <= 0L || programId.isBlank()) return false
        var found = false

        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { stored ->
                val currentUserProgram = stored.belongsToCurrentUser()
                val sameDevice = stored.deviceId == deviceId
                val sameProgram = stored.id == programId

                val nextStored = when {
                    sameDevice && currentUserProgram && sameProgram -> {
                        found = true
                        stored.toBuilder()
                            .setIsActive(isActive)
                            .setSyncState(
                                if (isActive) {
                                    LightProgramSyncState.ACTIVE_DIRTY.name
                                } else {
                                    LightProgramSyncState.LOCAL_ONLY.name
                                }
                            )
                            .setUpdatedAtMillis(nowMillis)
                            .build()
                    }

                    sameDevice && currentUserProgram && isActive -> {
                        stored.toBuilder()
                            .setIsActive(false)
                            .setSyncState(LightProgramSyncState.LOCAL_ONLY.name)
                            .setUpdatedAtMillis(nowMillis)
                            .build()
                    }

                    else -> stored
                }

                builder.addPrograms(nextStored)
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
                val shouldRename = stored.id == programId &&
                    stored.deviceId == deviceId &&
                    stored.belongsToCurrentUser()

                builder.addPrograms(
                    if (shouldRename) {
                        renamed = true
                        stored.toBuilder()
                            .setName(safeName)
                            .setSyncState(
                                if (stored.isActive) {
                                    LightProgramSyncState.ACTIVE_DIRTY.name
                                } else {
                                    stored.syncState.ifBlank {
                                        LightProgramSyncState.LOCAL_ONLY.name
                                    }
                                }
                            )
                            .setUpdatedAtMillis(nowMillis)
                            .build()
                    } else {
                        stored
                    }
                )
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
        if (deviceId <= 0L || programId.isBlank()) return null
        var duplicate: SavedLightProgram? = null

        dataStore.updateData { store ->
            val source = store.programsList.firstOrNull { stored ->
                stored.id == programId &&
                    stored.deviceId == deviceId &&
                    stored.belongsToCurrentUser()
            }

            if (source == null) {
                return@updateData store
            }

            val copy = source.toSavedLightProgram().copy(
                id = buildProgramId(
                    deviceId = deviceId,
                    nowMillis = nowMillis
                ),
                name = "${source.name} Copy",
                isActive = false,
                syncState = LightProgramSyncState.LOCAL_ONLY,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
                lastLoadedAtMillis = 0L,
                lastLoadedHash = ""
            )

            duplicate = copy

            store.toBuilder()
                .addPrograms(copy.toStoredLightProgram())
                .build()
        }

        return duplicate
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
                val shouldDelete = stored.id == programId &&
                    stored.deviceId == deviceId &&
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
            isActive = isActive,
            startMinute = startMinute.coerceIn(0, 24 * 60 - 1),
            peakStartMinute = peakStartMinute.coerceIn(0, 24 * 60 - 1),
            peakEndMinute = peakEndMinute.coerceIn(0, 24 * 60 - 1),
            endMinute = endMinute.coerceIn(1, 24 * 60),
            red = red.coerceIn(0, 100),
            green = green.coerceIn(0, 100),
            blue = blue.coerceIn(0, 100),
            white = white.coerceIn(0, 100),
            repeatMode = LightProgramRepeatMode.fromStorage(repeatMode),
            selectedDays = selectedDaysList
                .filter { day -> day in 1..7 }
                .toSet()
                .ifEmpty { SavedLightProgram.ALL_DAYS },
            transitionMode = LightProgramTransitionMode.fromStorage(transitionMode),
            syncState = LightProgramSyncState.fromStorage(syncState),
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            lastLoadedAtMillis = lastLoadedAtMillis,
            lastLoadedHash = lastLoadedHash
        )
    }

    private fun SavedLightProgram.toStoredLightProgram(): StoredLightProgram {
        return StoredLightProgram.newBuilder()
            .setId(id)
            .setOwnerUid(ownerUid)
            .setDeviceId(deviceId)
            .setDeviceUid(deviceUid)
            .setProductId(productId)
            .setName(name)
            .setIsActive(isActive)
            .setStartMinute(startMinute.coerceIn(0, 24 * 60 - 1))
            .setPeakStartMinute(peakStartMinute.coerceIn(0, 24 * 60 - 1))
            .setPeakEndMinute(peakEndMinute.coerceIn(0, 24 * 60 - 1))
            .setEndMinute(endMinute.coerceIn(1, 24 * 60))
            .setRed(red.coerceIn(0, 100))
            .setGreen(green.coerceIn(0, 100))
            .setBlue(blue.coerceIn(0, 100))
            .setWhite(white.coerceIn(0, 100))
            .setRepeatMode(repeatMode.name)
            .addAllSelectedDays(
                selectedDays.filter { day -> day in 1..7 }.sorted()
            )
            .setTransitionMode(transitionMode.name)
            .setSyncState(syncState.name)
            .setCreatedAtMillis(createdAtMillis)
            .setUpdatedAtMillis(updatedAtMillis)
            .setLastLoadedAtMillis(lastLoadedAtMillis)
            .setLastLoadedHash(lastLoadedHash)
            .build()
    }

    private fun buildProgramId(
        deviceId: Long,
        nowMillis: Long
    ): String {
        return "program_${deviceId}_${nowMillis}_${UUID.randomUUID()}"
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
