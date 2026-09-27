package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Instrumentation assertions may assemble rows; production UI only receives bounded pages. */
internal suspend fun waterHistoryTestRows(context: Context, owner: String, tankId: Long? = null):
    List<WaterAnalysisRecord> = withContext(Dispatchers.IO) {
    val dao = WaterAnalysisDatabase.getInstance(context).analyses()
    val records = mutableListOf<WaterAnalysisRecord>()
    var after = 0L
    var page = dao.migrationPage(owner, after)
    while (page.isNotEmpty()) {
        records += page.map { it.toMigrationRecord().toRecordStrict() }
            .filter { tankId == null || it.tankId == tankId }
        after = page.last().analysisId
        page = dao.migrationPage(owner, after)
    }
    WaterAnalysisIdentityRules.newestFirst(records)
}
