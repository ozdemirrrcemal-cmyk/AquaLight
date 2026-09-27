package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Known v1 missing catalog identities become explicit custom identities, never inferred species. */
internal class UserDataBackupManifestReader(private val gson: Gson) {
    fun read(json: String): UserDataBackupManifest {
        val root = JsonParser.parseString(json)
        require(root.isJsonObject) { "Backup manifest must be an object." }
        val document = root.asJsonObject
        val version = document.get("schemaVersion")
        require(version?.isJsonPrimitive == true && version.asJsonPrimitive.isNumber)
        require(version.asString in setOf("1", "2", "3", USER_DATA_BACKUP_SCHEMA_VERSION.toString())) {
            "Unsupported backup schema version."
        }
        if (version.asInt == LEGACY_VERSION) {
            upgradeLegacyIdentities(document)
        }
        if (version.asInt < WATER_HISTORY_VERSION) {
            require(!document.has("waterHistory")) { "Legacy archives cannot declare analysis history." }
            document.add("waterHistory", com.google.gson.JsonNull.INSTANCE)
        }
        else {
            validateHistoryDeclaration(document.get("waterHistory"))
        }
        if (version.asInt < USER_DATA_BACKUP_SCHEMA_VERSION) {
            require(!document.has("healthHistory")) { "Legacy archives cannot declare observation history." }
            document.add("healthHistory", com.google.gson.JsonNull.INSTANCE)
        } else {
            HealthHistoryBackupIntegration.validateDeclaration(document.get("healthHistory"))
        }
        document.addProperty("schemaVersion", USER_DATA_BACKUP_SCHEMA_VERSION)
        return requireNotNull(gson.fromJson(document, UserDataBackupManifest::class.java))
    }

    private fun validateHistoryDeclaration(element: JsonElement?) {
        require(element?.isJsonObject == true) { "Current backup is missing its analysis history declaration." }
        val history = element.asJsonObject
        require(history.get("entryName")?.asString == WaterHistoryArchive.ENTRY)
        require(exactLong(history, "formatVersion") == WaterHistoryArchive.FORMAT_VERSION.toLong())
        require(exactLong(history, "recordCount") in 0L..WaterHistoryArchive.MAX_RECORDS.toLong())
        require(exactLong(history, "byteSize") in 1L..UserDataBackupLimits.MAX_UNCOMPRESSED_ARCHIVE_BYTES.toLong())
        val hash = history.get("sha256")
        require(hash?.isJsonPrimitive == true && hash.asJsonPrimitive.isString)
        require(Regex("[0-9a-f]{64}").matches(hash.asString))
    }

    private fun exactLong(document: JsonObject, key: String): Long {
        val value = document.get(key)
        require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isNumber)
        return requireNotNull(value.asString.toLongOrNull()) { "Archive history integer is invalid." }
    }

    private fun upgradeLegacyIdentities(document: JsonObject) {
        val aquariums = requireNotNull(document.getAsJsonArray("aquariums"))
        aquariums.forEach { tank ->
            val livestock = requireNotNull(tank.asJsonObject.getAsJsonArray("livestock"))
            livestock.forEach { upgradeLegacyIdentity(it.asJsonObject) }
        }
    }

    private fun upgradeLegacyIdentity(animal: JsonObject) {
        if (isMissingIdentity(animal.get("catalogEntryId"))) {
            val id = animal.get("id")
            require(id?.isJsonPrimitive == true && id.asJsonPrimitive.isNumber)
            val exactId = requireNotNull(id.asString.toLongOrNull())
            animal.addProperty("catalogEntryId", AquariumLivestockIdentity.custom(exactId))
        }
    }

    private fun isMissingIdentity(value: JsonElement?): Boolean = when {
        value == null -> true
        value.isJsonNull -> true
        value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString.isEmpty()
        else -> false
    }

    private companion object { const val LEGACY_VERSION = 1; const val WATER_HISTORY_VERSION = 3 }
}
