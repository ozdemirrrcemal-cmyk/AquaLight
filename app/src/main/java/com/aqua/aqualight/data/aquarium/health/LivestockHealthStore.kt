package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.application.aquarium.health.LivestockCheckInput
import com.aqua.aqualight.application.aquarium.health.LivestockCheckSnapshot
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import com.aqua.aqualight.platform.media.AppMediaScope
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private object LivestockHealthSerializer : Serializer<LivestockHealthStore> {
    override val defaultValue: LivestockHealthStore = LivestockHealthStore.newBuilder()
        .setSchemaVersion(CommercialStoreSchema.LIVESTOCK_HEALTH_VERSION)
        .build()

    override suspend fun readFrom(input: InputStream): LivestockHealthStore = try {
        validateLivestockHealthStore(LivestockHealthStore.parseFrom(input))
    } catch (error: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read livestock health records.", error)
    } catch (error: StoreInvariantViolation) {
        throw CorruptionException("Invalid livestock health records.", error)
    } catch (error: IllegalArgumentException) {
        throw CorruptionException("Invalid livestock health fields.", error)
    }

    override suspend fun writeTo(t: LivestockHealthStore, output: OutputStream) {
        validateLivestockHealthStore(t).writeTo(output)
    }
}

private val Context.livestockHealthDataStore: DataStore<LivestockHealthStore> by dataStore(
    fileName = "livestock_health.pb",
    serializer = LivestockHealthSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(LocalDataRecoveryTracker.Area.LIVESTOCK_HEALTH)
        LivestockHealthSerializer.defaultValue
    }
)

internal class LivestockHealthDataStoreManager(context: Context) {
    private val appContext = context.applicationContext
    private val tanks = AquariumTankDataStoreManager(appContext)

    suspend fun mediaUrisForOwner(ownerUid: String): Set<String> {
        require(ownerUid.isNotBlank())
        return appContext.livestockHealthDataStore.data.map { store ->
            validateLivestockHealthStore(store).observationsList
                .filter { it.ownerUid == ownerUid }
                .flatMap(StoredLivestockObservation::mediaUris)
                .toSet()
        }.first()
    }

    fun observationsForTank(tankId: Long): Flow<List<LivestockObservationSnapshot>> {
        require(tankId > 0)
        val ownerUid = UserDataScope.requireCurrentUid()
        return appContext.livestockHealthDataStore.data.map { store ->
            checkOwner(ownerUid)
            validateLivestockHealthStore(store).observationsList
                .filter { it.ownerUid == ownerUid && it.tankId == tankId }
                .map(StoredLivestockObservation::toSnapshot)
                .sortedWith(compareByDescending<LivestockObservationSnapshot> { it.createdAtMillis }
                    .thenByDescending { it.id })
        }
    }

    suspend fun create(input: LivestockObservationInput): Long =
        withContext(NonCancellable + Dispatchers.IO) {
            validateInput(input)
            val ownerUid = UserDataScope.requireCurrentUid()
            val livestock = tanks.tanksSnapshotForOwner(ownerUid)
                .firstOrNull { it.id == input.tankId }
                ?.livestock?.firstOrNull { it.id == input.livestockId }
            var id = 0L
            appContext.livestockHealthDataStore.updateData { current ->
                checkOwner(ownerUid)
                val existing = current.observationsList.firstOrNull {
                    it.ownerUid == ownerUid && it.requestId == input.requestId
                }
                if (existing != null) {
                    require(existing.tankId == input.tankId &&
                        existing.livestockId == input.livestockId &&
                        existing.symptomKeysList == input.symptomKeys &&
                        existing.onsetKey == input.onsetKey &&
                        existing.affectedCount == input.affectedCount &&
                        existing.otherObservation == input.otherObservation.trim() &&
                        existing.note == input.note.trim() &&
                        existing.photoUrisList == input.photoUris
                    ) { "Observation request was already used for different data." }
                    id = existing.id
                    return@updateData current
                }
                val selected = livestock
                    ?: throw StoreInvariantViolation("Selected livestock no longer exists in this tank.")
                require(input.affectedCount <= selected.quantity)
                require(input.photoUris.all {
                    AppMediaStorage.pendingMediaOwner(
                        appContext, it, AppMediaScope.LIVESTOCK
                    ) == ownerUid
                }) { "Observation photos must belong to the active owner." }
                val now = System.currentTimeMillis()
                val maxId = current.observationsList.maxOfOrNull { it.id } ?: 0L
                check(maxId < Long.MAX_VALUE)
                id = maxOf(now, maxId + 1)
                validateLivestockHealthStore(current.toBuilder().addObservations(
                    StoredLivestockObservation.newBuilder()
                        .setId(id)
                        .setRequestId(input.requestId)
                        .setOwnerUid(ownerUid)
                        .setTankId(input.tankId)
                        .setLivestockId(input.livestockId)
                        .addAllSymptomKeys(input.symptomKeys)
                        .setOnsetKey(input.onsetKey)
                        .setOtherObservation(input.otherObservation.trim())
                        .setNote(input.note.trim())
                        .addAllPhotoUris(input.photoUris)
                        .setAffectedCount(input.affectedCount)
                        .setTotalCount(selected.quantity)
                        .setCreatedAtMillis(now)
                        .build()
                ).build())
            }
            input.photoUris.forEach { AppMediaStorage.commitPendingMedia(appContext, it) }
            id
        }

    suspend fun addCheck(tankId: Long, observationId: Long, input: LivestockCheckInput) =
        withContext(NonCancellable + Dispatchers.IO) {
            require(tankId > 0 && observationId > 0)
            validateCheck(input)
            val ownerUid = UserDataScope.requireCurrentUid()
            mutate(tankId, observationId) { record ->
                val existing = record.checksList.firstOrNull { it.requestId == input.requestId }
                if (existing != null) {
                    require(existing.status == input.status &&
                        existing.affectedCount == input.affectedCount &&
                        existing.checkedAtMillis == input.checkedAtMillis &&
                        existing.note == input.note.trim() &&
                        existing.photoUri == input.photoUri.orEmpty()
                    ) { "Check request was already used for different data." }
                    return@mutate record
                }
                require(record.closedAtMillis == 0L) { "Follow-up already closed." }
                require(input.photoUri == null ||
                    AppMediaStorage.pendingMediaOwner(
                        appContext, input.photoUri, AppMediaScope.LIVESTOCK
                    ) == ownerUid
                ) { "Check photo must belong to the active owner." }
                require(input.checkedAtMillis >= record.createdAtMillis) {
                    "Check cannot predate the observation."
                }
                require(input.affectedCount <= record.totalCount)
                val updated = record.toBuilder().addChecks(
                    StoredLivestockCheck.newBuilder()
                        .setRequestId(input.requestId)
                        .setStatus(input.status)
                        .setAffectedCount(input.affectedCount)
                        .setCheckedAtMillis(input.checkedAtMillis)
                        .setNote(input.note.trim())
                        .setPhotoUri(input.photoUri.orEmpty())
                        .build()
                )
                if (input.status == "recovered") {
                    updated.setClosedAtMillis(maxOf(System.currentTimeMillis(), input.checkedAtMillis))
                        .setCloseReason("recovered")
                }
                updated.build()
            }
            AppMediaStorage.commitPendingMedia(appContext, input.photoUri)
        }

    suspend fun close(tankId: Long, observationId: Long, reason: String) {
        require(reason == "manual" || reason == "recovered")
        mutate(tankId, observationId) { record ->
            if (record.closedAtMillis != 0L) return@mutate record
            record.toBuilder()
                .setClosedAtMillis(maxOf(
                    System.currentTimeMillis(),
                    record.checksList.maxOfOrNull { it.checkedAtMillis }
                        ?: record.createdAtMillis
                ))
                .setCloseReason(reason)
                .build()
        }
    }

    suspend fun deleteForTank(tankId: Long) {
        require(tankId > 0)
        val ownerUid = UserDataScope.requireCurrentUid()
        val media = mutableSetOf<String>()
        appContext.livestockHealthDataStore.updateData { current ->
            checkOwner(ownerUid)
            current.observationsList.filter {
                it.ownerUid == ownerUid && it.tankId == tankId
            }.forEach { media += it.mediaUris() }
            validateLivestockHealthStore(current.toBuilder().clearObservations()
                .addAllObservations(current.observationsList.filterNot {
                    it.ownerUid == ownerUid && it.tankId == tankId
                }).build())
        }
        withContext(NonCancellable + Dispatchers.IO) {
            AppMediaStorage.deleteInternalMedia(appContext, media)
        }
    }

    suspend fun deleteForLivestock(tankId: Long, livestockId: Long) {
        require(tankId > 0 && livestockId > 0)
        val ownerUid = UserDataScope.requireCurrentUid()
        val media = mutableSetOf<String>()
        appContext.livestockHealthDataStore.updateData { current ->
            checkOwner(ownerUid)
            current.observationsList.filter {
                it.ownerUid == ownerUid && it.tankId == tankId &&
                    it.livestockId == livestockId
            }.forEach { media += it.mediaUris() }
            validateLivestockHealthStore(current.toBuilder().clearObservations()
                .addAllObservations(current.observationsList.filterNot {
                    it.ownerUid == ownerUid && it.tankId == tankId &&
                        it.livestockId == livestockId
                }).build())
        }
        withContext(NonCancellable + Dispatchers.IO) {
            AppMediaStorage.deleteInternalMedia(appContext, media)
        }
    }

    suspend fun clearAllForOwner(ownerUid: String) =
        withContext(NonCancellable + Dispatchers.IO) {
            require(ownerUid.isNotBlank())
            val media = mutableSetOf<String>()
            appContext.livestockHealthDataStore.updateData { current ->
                current.observationsList.filter { it.ownerUid == ownerUid }
                    .forEach { media += it.mediaUris() }
                validateLivestockHealthStore(current.toBuilder().clearObservations()
                    .addAllObservations(current.observationsList.filterNot { it.ownerUid == ownerUid })
                    .build())
            }
            AppMediaStorage.deleteInternalMedia(appContext, media)
        }

    private suspend fun mutate(
        tankId: Long,
        observationId: Long,
        update: (StoredLivestockObservation) -> StoredLivestockObservation
    ) {
        val ownerUid = UserDataScope.requireCurrentUid()
        appContext.livestockHealthDataStore.updateData { current ->
            checkOwner(ownerUid)
            val index = current.observationsList.indexOfFirst {
                it.id == observationId && it.tankId == tankId && it.ownerUid == ownerUid
            }
            if (index < 0) throw StoreInvariantViolation("Observation no longer exists.")
            validateLivestockHealthStore(current.toBuilder()
                .setObservations(index, update(current.getObservations(index))).build())
        }
    }

    private fun checkOwner(expected: String) {
        if (UserDataScope.requireCurrentUid() != expected) {
            throw StoreInvariantViolation("Active owner changed during livestock health operation.")
        }
    }
}

private fun StoredLivestockObservation.toSnapshot(): LivestockObservationSnapshot =
    LivestockObservationSnapshot(
        id = id, tankId = tankId, livestockId = livestockId,
        symptomKeys = symptomKeysList, onsetKey = onsetKey,
        otherObservation = otherObservation, note = note, photoUris = photoUrisList,
        affectedCount = affectedCount, totalCount = totalCount,
        createdAtMillis = createdAtMillis,
        closedAtMillis = closedAtMillis.takeIf { it > 0 },
        closeReason = closeReason.takeIf(String::isNotBlank),
        checks = checksList.map {
            LivestockCheckSnapshot(
                status = it.status, affectedCount = it.affectedCount,
                checkedAtMillis = it.checkedAtMillis, note = it.note,
                photoUri = it.photoUri.takeIf(String::isNotBlank)
            )
        }
    )

private fun StoredLivestockObservation.mediaUris(): List<String> =
    photoUrisList + checksList.mapNotNull { it.photoUri.takeIf(String::isNotBlank) }

private const val MAX_REQUEST_ID_LENGTH = 64
private const val MAX_OWNER_UID_LENGTH = 128
private const val MAX_SYMPTOMS = 16
private const val MAX_KEY_LENGTH = 64
private const val MAX_OTHER_LENGTH = 2000
private const val MAX_NOTE_LENGTH = 4000
private const val MAX_PHOTOS = 3
private const val MAX_URI_LENGTH = 2048
private const val MIN_RECORD_TIME_MILLIS = 946_684_800_000L
private const val MAX_RECORD_TIME_MILLIS = 4_102_444_800_000L
private const val CHECK_CLOCK_SKEW_MILLIS = 60_000L

private fun validateInput(input: LivestockObservationInput) {
    require(input.requestId.length in 1..MAX_REQUEST_ID_LENGTH)
    require(input.tankId > 0 && input.livestockId > 0 && input.affectedCount > 0)
    require(input.symptomKeys.isNotEmpty() && input.symptomKeys.size <= MAX_SYMPTOMS)
    require(input.symptomKeys.distinct().size == input.symptomKeys.size)
    require(input.symptomKeys.all { it.isNotBlank() && it.length <= MAX_KEY_LENGTH })
    require(input.onsetKey.isNotBlank() && input.onsetKey.length <= MAX_KEY_LENGTH)
    require(input.otherObservation.length <= MAX_OTHER_LENGTH && input.note.length <= MAX_NOTE_LENGTH)
    require(input.photoUris.size <= MAX_PHOTOS)
    require(input.photoUris.all { it.isNotBlank() && it.length <= MAX_URI_LENGTH })
    require("other" !in input.symptomKeys || input.otherObservation.isNotBlank())
}

private fun validateCheck(input: LivestockCheckInput) {
    require(input.requestId.length in 1..MAX_REQUEST_ID_LENGTH)
    require(input.status in setOf("increased", "same", "decreased", "recovered"))
    require(input.affectedCount > 0)
    require(input.checkedAtMillis in MIN_RECORD_TIME_MILLIS..
        (System.currentTimeMillis() + CHECK_CLOCK_SKEW_MILLIS))
    require(input.note.length <= MAX_NOTE_LENGTH)
    require(input.photoUri == null || input.photoUri.length <= MAX_URI_LENGTH)
}

internal fun validateLivestockHealthStore(store: LivestockHealthStore): LivestockHealthStore {
    CommercialStoreSchema.requireCurrent(
        "LivestockHealthStore", store.schemaVersion,
        CommercialStoreSchema.LIVESTOCK_HEALTH_VERSION
    )
    val ids = mutableSetOf<Pair<String, Long>>()
    val requestIds = mutableSetOf<Pair<String, String>>()
    store.observationsList.forEach { record ->
        validateStoredObservation(record)
        if (!ids.add(record.ownerUid to record.id)) invalidObservation()
        if (!requestIds.add(record.ownerUid to record.requestId)) invalidObservation()
        val checkRequestIds = mutableSetOf<String>()
        record.checksList.forEach { check ->
            validateStoredCheck(check, record)
            if (!checkRequestIds.add(check.requestId)) invalidCheck()
        }
    }
    return store
}

private fun validateStoredObservation(record: StoredLivestockObservation) {
    if (record.id <= 0 || record.tankId <= 0 || record.livestockId <= 0) invalidObservation()
    if (record.ownerUid.isBlank() || record.ownerUid.length > MAX_OWNER_UID_LENGTH ||
        record.ownerUid != record.ownerUid.trim()
    ) invalidObservation()
    if (record.createdAtMillis !in MIN_RECORD_TIME_MILLIS..MAX_RECORD_TIME_MILLIS) {
        invalidObservation()
    }
    if (record.totalCount <= 0 || record.affectedCount !in 1..record.totalCount) {
        invalidObservation()
    }
    if ((record.closedAtMillis == 0L) != record.closeReason.isBlank()) invalidObservation()
    if (record.closedAtMillis != 0L) {
        if (record.closedAtMillis < record.createdAtMillis) invalidObservation()
        if (record.closeReason !in setOf("manual", "recovered")) invalidObservation()
    }
    validateInput(LivestockObservationInput(
        record.requestId, record.tankId, record.livestockId, record.symptomKeysList,
        record.onsetKey, record.otherObservation, record.note,
        record.photoUrisList, record.affectedCount
    ))
}

private fun validateStoredCheck(check: StoredLivestockCheck, record: StoredLivestockObservation) {
    if (check.status !in setOf("increased", "same", "decreased", "recovered")) invalidCheck()
    if (check.requestId.length !in 1..MAX_REQUEST_ID_LENGTH) invalidCheck()
    if (check.affectedCount !in 1..record.totalCount) invalidCheck()
    if (check.checkedAtMillis < record.createdAtMillis) invalidCheck()
    if (check.note.length > MAX_NOTE_LENGTH || check.photoUri.length > MAX_URI_LENGTH) invalidCheck()
}

private fun invalidObservation(): Nothing =
    throw StoreInvariantViolation("Invalid livestock observation.")

private fun invalidCheck(): Nothing = throw StoreInvariantViolation("Invalid livestock check.")
