package com.aqua.aqualight.data.aquarium.health

import androidx.sqlite.db.SupportSQLiteDatabase

/** Runs inside Room's schema transaction; visits one retained event at a time, changing no payloads. */
internal object WaterImportSchemaMigration {
    @JvmStatic
    fun backfill(database: SupportSQLiteDatabase) {
        database.query("SELECT ownerUid, analysisId, rawProto FROM water_analysis").use { cursor ->
            while (cursor.moveToNext()) {
                val record = StoredWaterAnalysis.parseFrom(cursor.getBlob(2))
                WaterAnalysisStoreRules.validateStoredAnalysis(record)
                check(record.ownerUid == cursor.getString(0) && record.id == cursor.getLong(1))
                if (record.hasImportOrigin()) {
                    val origin = record.importOrigin
                    database.execSQL("INSERT INTO water_analysis_import " +
                        "(ownerUid, sourceOwnerUid, sourceAnalysisId, analysisId, restoreTransactionId, " +
                        "sourceRecordSha256) VALUES (?, ?, ?, ?, ?, ?)", arrayOf(record.ownerUid,
                        origin.sourceOwnerUid, origin.sourceAnalysisId, record.id,
                        origin.restoreTransactionId, origin.sourceRecordSha256))
                }
            }
        }
    }
}
