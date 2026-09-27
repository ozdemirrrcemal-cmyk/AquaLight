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
        require(version.asString in setOf("1", USER_DATA_BACKUP_SCHEMA_VERSION.toString())) {
            "Unsupported backup schema version."
        }
        if (version.asInt == LEGACY_VERSION) {
            upgradeLegacyIdentities(document)
            document.addProperty("schemaVersion", USER_DATA_BACKUP_SCHEMA_VERSION)
        }
        return requireNotNull(gson.fromJson(document, UserDataBackupManifest::class.java))
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

    private companion object { const val LEGACY_VERSION = 1 }
}
