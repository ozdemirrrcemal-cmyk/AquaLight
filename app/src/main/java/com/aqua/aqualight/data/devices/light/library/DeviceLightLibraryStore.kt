package com.aqua.aqualight.data.devices.light.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.deviceLightLibraryDataStore: DataStore<DeviceLightLibraryStoreData> by dataStore(
    fileName = "light_library.pb",
    serializer = DeviceLightLibrarySerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(LocalDataRecoveryTracker.Area.LIGHT_LIBRARY)
        DeviceLightLibraryStoreRules.defaultStore()
    }
)

internal class DeviceLightLibraryStore private constructor(
    private val context: Context,
    ownerUid: String
) {
    private val ownerUid = DeviceLightLibraryStoreRules.canonicalOwnerUid(ownerUid)

    fun observeEntries(): Flow<List<StoredDeviceLightLibraryEntry>> =
        context.deviceLightLibraryDataStore.data.map { store ->
            DeviceLightLibraryStoreRules.validateStore(store)
                .entriesList
                .filter { entry -> entry.ownerUid == ownerUid }
                .sortedWith(
                    compareByDescending<StoredDeviceLightLibraryEntry> { entry ->
                        entry.updatedAtMillis
                    }.thenByDescending { entry -> entry.createdAtMillis }
                )
        }

    suspend fun snapshot(): List<StoredDeviceLightLibraryEntry> = observeEntries().first()

    suspend fun insert(entry: StoredDeviceLightLibraryEntry) {
        require(entry.ownerUid == ownerUid) { "Light-library entry crossed owner scope." }
        DeviceLightLibraryStoreRules.validateEntry(entry)
        context.deviceLightLibraryDataStore.updateData { current ->
            val validated = DeviceLightLibraryStoreRules.validateStore(current)
            if (validated.entriesList.any { existing -> existing.id == entry.id }) {
                throw DeviceLightLibraryStoreConflict.Id(entry.id)
            }
            requireUniqueName(validated.entriesList, entry, excludedId = null)
            validated.toBuilder()
                .addEntries(entry)
                .build()
                .let(DeviceLightLibraryStoreRules::validateStore)
        }
    }

    suspend fun rename(
        entryId: String,
        displayName: String,
        normalizedName: String,
        updatedAtMillis: Long
    ) {
        context.deviceLightLibraryDataStore.updateData { current ->
            val validated = DeviceLightLibraryStoreRules.validateStore(current)
            val index = validated.entriesList.indexOfFirst { entry ->
                entry.ownerUid == ownerUid && entry.id == entryId
            }
            if (index < 0) throw DeviceLightLibraryStoreConflict.NotFound(entryId)
            val renamed = validated.entriesList[index].toBuilder()
                .setDisplayName(displayName)
                .setNormalizedName(normalizedName)
                .setUpdatedAtMillis(updatedAtMillis)
                .build()
            DeviceLightLibraryStoreRules.validateEntry(renamed)
            requireUniqueName(validated.entriesList, renamed, excludedId = entryId)
            validated.toBuilder()
                .setEntries(index, renamed)
                .build()
                .let(DeviceLightLibraryStoreRules::validateStore)
        }
    }

    suspend fun delete(entryId: String) {
        context.deviceLightLibraryDataStore.updateData { current ->
            val validated = DeviceLightLibraryStoreRules.validateStore(current)
            val remaining = validated.entriesList.filterNot { entry ->
                entry.ownerUid == ownerUid && entry.id == entryId
            }
            if (remaining.size == validated.entriesCount) {
                throw DeviceLightLibraryStoreConflict.NotFound(entryId)
            }
            validated.toBuilder()
                .clearEntries()
                .addAllEntries(remaining)
                .build()
                .let(DeviceLightLibraryStoreRules::validateStore)
        }
    }

    suspend fun clearOwner() {
        context.deviceLightLibraryDataStore.updateData { current ->
            val validated = DeviceLightLibraryStoreRules.validateStore(current)
            validated.toBuilder()
                .clearEntries()
                .addAllEntries(
                    validated.entriesList.filterNot { entry -> entry.ownerUid == ownerUid }
                )
                .build()
                .let(DeviceLightLibraryStoreRules::validateStore)
        }
    }

    private fun requireUniqueName(
        entries: List<StoredDeviceLightLibraryEntry>,
        candidate: StoredDeviceLightLibraryEntry,
        excludedId: String?
    ) {
        val duplicate = entries.any { entry ->
            entry.ownerUid == ownerUid &&
                entry.id != excludedId &&
                entry.kind == candidate.kind &&
                entry.normalizedName == candidate.normalizedName
        }
        if (duplicate) {
            throw DeviceLightLibraryStoreConflict.Name(candidate.displayName)
        }
    }

    companion object {
        fun create(
            context: Context,
            ownerUid: String
        ): DeviceLightLibraryStore = DeviceLightLibraryStore(
            context = context.applicationContext,
            ownerUid = ownerUid
        )
    }
}

internal sealed class DeviceLightLibraryStoreConflict(message: String) :
    IllegalStateException(message) {
    class Id(id: String) : DeviceLightLibraryStoreConflict("Duplicate light-library id: $id")
    class Name(name: String) :
        DeviceLightLibraryStoreConflict("Duplicate light-library name: $name")
    class NotFound(id: String) :
        DeviceLightLibraryStoreConflict("Light-library entry not found: $id")
}
