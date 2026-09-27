package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
                createVersionOne(database)
                database.execSQL("INSERT INTO water_analysis VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(row.ownerUid, row.id, row.tankId, row.measuredAtMillis,
                        row.createdAtMillis, null, row.toByteArray()))
                database.version = 1
            }
            val upgraded = Room.databaseBuilder(context, WaterAnalysisDatabase::class.java, name)
                .addMigrations(WaterAnalysisDatabase.MIGRATION_1_2).build()
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

    private fun createVersionOne(database: SQLiteDatabase) {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schema = assets.open("com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase/1.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val entities = schema.getJSONArray("entities")
        repeat(entities.length()) { index ->
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.getJSONArray("indices")
            repeat(indices.length()) { item ->
                database.execSQL(indices.getJSONObject(item).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
    }
}
