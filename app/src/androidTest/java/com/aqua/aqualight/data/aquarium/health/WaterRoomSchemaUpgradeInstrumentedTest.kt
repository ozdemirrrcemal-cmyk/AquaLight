package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterRoomSchemaUpgradeInstrumentedTest {
    @Test
    fun generatedVersionOneDatabaseUpgradesWithoutRewritingPayloadsOrInventingRequests() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "water-schema-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        check(path.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        val row = WaterRoomFixture.record(1)
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
                createVersion(database, 1)
                database.execSQL("INSERT INTO water_analysis VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(row.ownerUid, row.id, row.tankId, row.measuredAtMillis,
                        row.createdAtMillis, null, row.toByteArray()))
                database.version = 1
            }
            val upgraded = Room.databaseBuilder(context, WaterAnalysisDatabase::class.java, name)
                .addMigrations(WaterAnalysisDatabase.MIGRATION_1_2, WaterAnalysisDatabase.MIGRATION_2_3).build()
            try {
                val read = checkNotNull(upgraded.analyses().record(row.ownerUid, row.tankId, row.id))
                assertArrayEquals(row.toByteArray(), read.rawProto)
                assertEquals(1L, upgraded.analyses().countForOwner(row.ownerUid))
                assertNull(upgraded.analyses().request(row.ownerUid, ""))
                assertNull(upgraded.analyses().migration(row.ownerUid))
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun versionTwoUpgradeBackfillsImportIdentityWithoutChangingRawEvidence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "water-import-schema-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        check(path.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        val original = WaterRoomFixture.record(1)
        val row = WaterAnalysisImportIdentity.remap(original,
            WaterAnalysisImportTarget("restored-owner", 7L, 22L, UUID.randomUUID().toString()))
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
                createVersion(database, 2)
                database.execSQL("INSERT INTO water_analysis VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(row.ownerUid, row.id, row.tankId, row.measuredAtMillis,
                        row.createdAtMillis, row.requestId, row.toByteArray()))
                database.version = 2
            }
            val upgraded = Room.databaseBuilder(context, WaterAnalysisDatabase::class.java, name)
                .addMigrations(WaterAnalysisDatabase.MIGRATION_2_3).build()
            try {
                val read = checkNotNull(upgraded.analyses().record(row.ownerUid, row.tankId, row.id))
                assertArrayEquals(row.toByteArray(), read.rawProto)
                val mapping = checkNotNull(upgraded.imports()
                    .original(row.ownerUid, original.ownerUid, original.id))
                assertEquals(row.id, mapping.analysisId)
                assertEquals(row.importOrigin.restoreTransactionId, mapping.restoreTransactionId)
                assertEquals(row.importOrigin.sourceRecordSha256, mapping.sourceRecordSha256)
                assertNotNull(upgraded.analyses().recordForOwner(row.ownerUid, row.id))
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun createVersion(database: SQLiteDatabase, version: Int) {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schema = assets.open("com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase/$version.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val entities = schema.getJSONArray("entities")
        repeat(entities.length()) { index ->
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            // Room omits this property for entities without indices (including migration state).
            val indices = entity.optJSONArray("indices") ?: JSONArray()
            repeat(indices.length()) { item ->
                database.execSQL(indices.getJSONObject(item).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
    }
}
