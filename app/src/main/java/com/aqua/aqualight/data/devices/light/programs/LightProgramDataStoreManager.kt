package com.aqua.aqualight.data.devices.light.programs

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramFirmwareProfile
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramSource
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramSyncStatus
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.light.programs.store.LightProgramsStore
import com.aqua.aqualight.data.devices.light.programs.store.StoredLightProgram
import com.aqua.aqualight.data.user.UserDataScope
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
                            if (program.isActive) 1 else 0
                        }.thenByDescending { program ->
                            program.updatedAt
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

        return dataStore.data.first()
            .programsList
            .firstOrNull { program ->
                program.id == programId &&
                    program.deviceId == deviceId &&
                    program.belongsToCurrentUser()
            }
            ?.toSavedLightProgram()
    }

    suspend fun createProgram(
        deviceId: Long,
        name: String,
        draft: LightProgramDraft,
        isActive: Boolean = false,
        source: LightProgramSource = LightProgramSource.USER_CREATED,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedLightProgram {
        val safeName = name.trim()
        require(safeName.isNotBlank()) {
            "Program name is required"
        }
        require(deviceId > 0L) {
            "Light device id is missing"
        }

        val device = devicesStore.devicesFlow.first()
            .firstOrNull { storedDevice ->
                storedDevice.id == deviceId
            }

        val program = SavedLightProgram(
            id = buildProgramId(
                deviceId = deviceId,
                nowMillis = nowMillis
            ),
            ownerUid = UserDataScope.currentUid(),
            deviceId = deviceId,
            deviceUid = device?.deviceUid.orEmpty(),
            productId = device?.productId.orEmpty(),
            name = safeName,
            draft = draft.sanitized(),
            isActive = isActive,
            syncStatus = LightProgramSyncStatus.LOCAL_ONLY,
            source = source,
            createdAt = nowMillis,
            updatedAt = nowMillis,
            activatedAt = if (isActive) nowMillis else null
        )

        dataStore.updateData { store ->
            val builder = store.toBuilder()

            if (isActive) {
                builder.clearPrograms()
                store.programsList.forEach { existing ->
                    builder.addPrograms(
                        if (existing.deviceId == deviceId && existing.belongsToCurrentUser()) {
                            existing.toBuilder()
                                .setActive(false)
                                .setUpdatedAtMillis(nowMillis)
                                .setActivatedAtMillis(0L)
                                .build()
                        } else {
                            existing
                        }
                    )
                }
            }

            builder.addPrograms(program.toStoredLightProgram())
                .build()
        }

        return program
    }

    suspend fun createRecoveredProgramFromDevice(
        deviceId: Long,
        draft: LightProgramDraft,
        checksum: String,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedLightProgram? {
        val safeChecksum = checksum.trim()
        if (deviceId <= 0L || safeChecksum.isBlank()) return null

        val device = devicesStore.devicesFlow.first()
            .firstOrNull { storedDevice ->
                storedDevice.id == deviceId
            }

        var recoveredProgram: SavedLightProgram? = null

        dataStore.updateData { store ->
            val existingMatch = store.programsList.firstOrNull { stored ->
                stored.deviceId == deviceId &&
                    stored.belongsToCurrentUser() &&
                    stored.compiledChecksum == safeChecksum
            }

            if (existingMatch != null) {
                recoveredProgram = existingMatch.toSavedLightProgram()
                return@updateData store
            }

            val existingNames = store.programsList
                .filter { stored ->
                    stored.deviceId == deviceId && stored.belongsToCurrentUser()
                }
                .map { stored -> stored.name }
                .toSet()

            val program = SavedLightProgram(
                id = buildProgramId(
                    deviceId = deviceId,
                    nowMillis = nowMillis
                ),
                ownerUid = UserDataScope.currentUid(),
                deviceId = deviceId,
                deviceUid = device?.deviceUid.orEmpty(),
                productId = device?.productId.orEmpty(),
                name = recoveredName(existingNames),
                draft = draft.sanitized(),
                isActive = true,
                syncStatus = LightProgramSyncStatus.READ_FROM_DEVICE,
                source = LightProgramSource.RECOVERED_FROM_DEVICE,
                compiledChecksum = safeChecksum,
                createdAt = nowMillis,
                updatedAt = nowMillis,
                activatedAt = nowMillis,
                lastSyncedAt = nowMillis,
                lastError = ""
            )

            recoveredProgram = program

            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { existing ->
                builder.addPrograms(
                    if (existing.deviceId == deviceId && existing.belongsToCurrentUser()) {
                        existing.toBuilder()
                            .setActive(false)
                            .setUpdatedAtMillis(nowMillis)
                            .setActivatedAtMillis(0L)
                            .build()
                    } else {
                        existing
                    }
                )
            }

            builder.addPrograms(program.toStoredLightProgram())
                .build()
        }

        return recoveredProgram
    }

    suspend fun updateProgram(
        deviceId: Long,
        programId: String,
        name: String,
        draft: LightProgramDraft,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedLightProgram? {
        if (deviceId <= 0L || programId.isBlank()) return null

        val safeName = name.trim()
        require(safeName.isNotBlank()) {
            "Program name is required"
        }

        var updatedProgram: SavedLightProgram? = null

        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { stored ->
                val shouldUpdate = stored.id == programId &&
                    stored.deviceId == deviceId &&
                    stored.belongsToCurrentUser()

                if (shouldUpdate) {
                    val saved = stored.toSavedLightProgram()
                    val updated = saved.copy(
                        name = safeName,
                        draft = draft.sanitized(),
                        syncStatus = LightProgramSyncStatus.LOCAL_ONLY,
                        compiledChecksum = "",
                        lastError = "",
                        updatedAt = nowMillis
                    )
                    updatedProgram = updated
                    builder.addPrograms(updated.toStoredLightProgram())
                } else {
                    builder.addPrograms(stored)
                }
            }

            builder.build()
        }

        return updatedProgram
    }

    suspend fun renameProgram(
        deviceId: Long,
        programId: String,
        name: String,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedLightProgram? {
        if (deviceId <= 0L || programId.isBlank()) return null

        val safeName = name.trim()
        require(safeName.isNotBlank()) {
            "Program name is required"
        }

        var renamedProgram: SavedLightProgram? = null

        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { stored ->
                val shouldRename = stored.id == programId &&
                    stored.deviceId == deviceId &&
                    stored.belongsToCurrentUser()

                if (shouldRename) {
                    val renamed = stored.toSavedLightProgram().copy(
                        name = safeName,
                        updatedAt = nowMillis
                    )
                    renamedProgram = renamed
                    builder.addPrograms(renamed.toStoredLightProgram())
                } else {
                    builder.addPrograms(stored)
                }
            }

            builder.build()
        }

        return renamedProgram
    }

    suspend fun duplicateProgram(
        deviceId: Long,
        programId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedLightProgram? {
        val original = getProgram(
            deviceId = deviceId,
            programId = programId
        ) ?: return null

        return createProgram(
            deviceId = deviceId,
            name = duplicateName(original.name),
            draft = original.draft,
            isActive = false,
            source = LightProgramSource.DUPLICATED,
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

    suspend fun setProgramActive(
        deviceId: Long,
        programId: String,
        isActive: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedLightProgram? {
        if (deviceId <= 0L || programId.isBlank()) return null

        var activeProgram: SavedLightProgram? = null

        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { stored ->
                val belongsToSameDevice = stored.deviceId == deviceId && stored.belongsToCurrentUser()
                val isTarget = belongsToSameDevice && stored.id == programId

                val updatedStored = when {
                    isTarget -> {
                        val updated = stored.toSavedLightProgram().copy(
                            isActive = isActive,
                            syncStatus = LightProgramSyncStatus.LOCAL_ONLY,
                            updatedAt = nowMillis,
                            activatedAt = if (isActive) nowMillis else null,
                            lastError = ""
                        )
                        activeProgram = updated
                        updated.toStoredLightProgram()
                    }

                    belongsToSameDevice && isActive -> {
                        stored.toBuilder()
                            .setActive(false)
                            .setUpdatedAtMillis(nowMillis)
                            .setActivatedAtMillis(0L)
                            .build()
                    }

                    else -> stored
                }

                builder.addPrograms(updatedStored)
            }

            builder.build()
        }

        return activeProgram
    }

    suspend fun markProgramActiveFromDevice(
        deviceId: Long,
        programId: String,
        checksum: String,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedLightProgram? {
        val safeChecksum = checksum.trim()
        if (deviceId <= 0L || programId.isBlank() || safeChecksum.isBlank()) return null

        var syncedProgram: SavedLightProgram? = null

        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { stored ->
                val belongsToSameDevice = stored.deviceId == deviceId && stored.belongsToCurrentUser()
                val isTarget = belongsToSameDevice && stored.id == programId

                val updatedStored = when {
                    isTarget -> {
                        val current = stored.toSavedLightProgram()
                        val syncedStatus = if (current.source == LightProgramSource.RECOVERED_FROM_DEVICE) {
                            LightProgramSyncStatus.READ_FROM_DEVICE
                        } else {
                            LightProgramSyncStatus.SYNCED_TO_DEVICE
                        }
                        val updated = current.copy(
                            isActive = true,
                            syncStatus = syncedStatus,
                            compiledChecksum = safeChecksum,
                            updatedAt = nowMillis,
                            activatedAt = nowMillis,
                            lastSyncedAt = nowMillis,
                            lastError = ""
                        )
                        syncedProgram = updated
                        updated.toStoredLightProgram()
                    }

                    belongsToSameDevice -> {
                        if (stored.active) {
                            stored.toBuilder()
                                .setActive(false)
                                .setUpdatedAtMillis(nowMillis)
                                .setActivatedAtMillis(0L)
                                .build()
                        } else {
                            stored
                        }
                    }

                    else -> stored
                }

                builder.addPrograms(updatedStored)
            }

            builder.build()
        }

        return syncedProgram
    }

    suspend fun markProgramSynced(
        deviceId: Long,
        programId: String,
        checksum: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        updateProgramSyncState(
            deviceId = deviceId,
            programId = programId,
            syncStatus = LightProgramSyncStatus.SYNCED_TO_DEVICE,
            checksum = checksum,
            lastSyncedAt = nowMillis,
            lastError = "",
            nowMillis = nowMillis
        )
    }

    suspend fun markProgramSyncFailed(
        deviceId: Long,
        programId: String,
        error: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        updateProgramSyncState(
            deviceId = deviceId,
            programId = programId,
            syncStatus = LightProgramSyncStatus.SYNC_FAILED,
            checksum = null,
            lastSyncedAt = null,
            lastError = error,
            nowMillis = nowMillis
        )
    }

    private suspend fun updateProgramSyncState(
        deviceId: Long,
        programId: String,
        syncStatus: LightProgramSyncStatus,
        checksum: String?,
        lastSyncedAt: Long?,
        lastError: String,
        nowMillis: Long
    ) {
        if (deviceId <= 0L || programId.isBlank()) return

        dataStore.updateData { store ->
            val builder = store.toBuilder()
                .clearPrograms()

            store.programsList.forEach { stored ->
                val shouldUpdate = stored.id == programId &&
                    stored.deviceId == deviceId &&
                    stored.belongsToCurrentUser()

                if (shouldUpdate) {
                    val saved = stored.toSavedLightProgram().copy(
                        syncStatus = syncStatus,
                        compiledChecksum = checksum ?: stored.compiledChecksum,
                        lastSyncedAt = lastSyncedAt ?: stored.lastSyncedAtMillis.takeIf { it > 0L },
                        lastError = lastError,
                        updatedAt = nowMillis
                    )
                    builder.addPrograms(saved.toStoredLightProgram())
                } else {
                    builder.addPrograms(stored)
                }
            }

            builder.build()
        }
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
            draft = LightProgramDraft(
                start = pointFromMinutes(startMinute),
                peakStart = pointFromMinutes(peakStartMinute),
                peakEnd = pointFromMinutes(peakEndMinute),
                end = pointFromMinutes(endMinute),
                channelValues = LightCurveChannelValues(
                    red = red.coerceIn(0, 100),
                    green = green.coerceIn(0, 100),
                    blue = blue.coerceIn(0, 100),
                    white = white.coerceIn(0, 100)
                ),
                repeatMode = enumValueOrDefault(
                    rawValue = repeatMode,
                    defaultValue = RepeatMode.EVERY
                ),
                selectedDays = selectedDaysList
                    .map { day -> day.coerceIn(1, 7) }
                    .toSet()
                    .ifEmpty {
                        EVERY_DAY_SELECTION
                    },
                transitionMode = enumValueOrDefault(
                    rawValue = transitionMode,
                    defaultValue = LightCurveTransitionMode.LINEAR
                )
            ),
            isActive = active,
            syncStatus = enumValueOrDefault(
                rawValue = syncStatus,
                defaultValue = LightProgramSyncStatus.LOCAL_ONLY
            ),
            source = enumValueOrDefault(
                rawValue = source,
                defaultValue = LightProgramSource.USER_CREATED
            ),
            compiledChecksum = compiledChecksum,
            firmwareProfile = LightProgramFirmwareProfile(
                supportsWeeklySchedule = firmwareSupportsWeeklySchedule,
                supportsNativeTransition = firmwareSupportsNativeTransition,
                supportsTemporaryLivePreview = firmwareSupportsTemporaryLivePreview
            ),
            createdAt = createdAtMillis,
            updatedAt = updatedAtMillis,
            activatedAt = activatedAtMillis.takeIf { value -> value > 0L },
            lastSyncedAt = lastSyncedAtMillis.takeIf { value -> value > 0L },
            lastError = lastError,
            schemaVersion = schemaVersion.takeIf { version -> version > 0 }
                ?: SavedLightProgram.SCHEMA_VERSION
        )
    }

    private fun SavedLightProgram.toStoredLightProgram(): StoredLightProgram {
        val safeDraft = draft.sanitized()
        return StoredLightProgram.newBuilder()
            .setId(id)
            .setOwnerUid(ownerUid)
            .setDeviceId(deviceId)
            .setDeviceUid(deviceUid)
            .setProductId(productId)
            .setName(name)
            .setStartMinute(safeDraft.start.totalMinutes.coerceIn(0, MINUTES_PER_DAY))
            .setPeakStartMinute(safeDraft.peakStart.totalMinutes.coerceIn(0, MINUTES_PER_DAY))
            .setPeakEndMinute(safeDraft.peakEnd.totalMinutes.coerceIn(0, MINUTES_PER_DAY))
            .setEndMinute(safeDraft.end.totalMinutes.coerceIn(0, MINUTES_PER_DAY))
            .setRed(safeDraft.channelValues.red)
            .setGreen(safeDraft.channelValues.green)
            .setBlue(safeDraft.channelValues.blue)
            .setWhite(safeDraft.channelValues.white)
            .setRepeatMode(safeDraft.repeatMode.name)
            .addAllSelectedDays(safeDraft.selectedDays.sorted())
            .setTransitionMode(safeDraft.transitionMode.name)
            .setActive(isActive)
            .setSyncStatus(syncStatus.name)
            .setSource(source.name)
            .setCompiledChecksum(compiledChecksum)
            .setFirmwareSupportsWeeklySchedule(firmwareProfile.supportsWeeklySchedule)
            .setFirmwareSupportsNativeTransition(firmwareProfile.supportsNativeTransition)
            .setFirmwareSupportsTemporaryLivePreview(firmwareProfile.supportsTemporaryLivePreview)
            .setCreatedAtMillis(createdAt)
            .setUpdatedAtMillis(updatedAt)
            .setActivatedAtMillis(activatedAt ?: 0L)
            .setLastSyncedAtMillis(lastSyncedAt ?: 0L)
            .setLastError(lastError)
            .setSchemaVersion(schemaVersion)
            .build()
    }

    private fun LightProgramDraft.sanitized(): LightProgramDraft {
        return copy(
            start = pointFromMinutes(start.totalMinutes),
            peakStart = pointFromMinutes(peakStart.totalMinutes),
            peakEnd = pointFromMinutes(peakEnd.totalMinutes),
            end = pointFromMinutes(end.totalMinutes),
            channelValues = channelValues.normalized(),
            selectedDays = selectedDays
                .map { day -> day.coerceIn(1, 7) }
                .toSet()
                .ifEmpty {
                    EVERY_DAY_SELECTION
                }
        )
    }

    private fun pointFromMinutes(
        totalMinutes: Int
    ): LightCurvePoint {
        val safeMinutes = totalMinutes.coerceIn(0, MINUTES_PER_DAY)
        return LightCurvePoint.of(
            hour = safeMinutes / 60,
            minute = safeMinutes % 60
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        rawValue: String,
        defaultValue: T
    ): T {
        return runCatching {
            enumValueOf<T>(rawValue)
        }.getOrDefault(defaultValue)
    }

    private fun duplicateName(
        name: String
    ): String {
        return "${name.trim().ifBlank { "Program" }} Copy"
    }

    private fun recoveredName(
        existingNames: Set<String>
    ): String {
        val baseName = "Device Program"
        val normalizedNames = existingNames
            .map { name -> name.trim().lowercase() }
            .toSet()

        if (baseName.lowercase() !in normalizedNames) {
            return baseName
        }

        for (index in 2..99) {
            val candidate = "$baseName $index"
            if (candidate.lowercase() !in normalizedNames) {
                return candidate
            }
        }

        return "$baseName ${System.currentTimeMillis()}"
    }

    private fun buildProgramId(
        deviceId: Long,
        nowMillis: Long
    ): String {
        return "program_${deviceId}_${nowMillis}_${UUID.randomUUID()}"
    }

}

private const val MINUTES_PER_DAY = 24 * 60
private val EVERY_DAY_SELECTION: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)

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
