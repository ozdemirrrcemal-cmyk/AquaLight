package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.Base64

/** Immutable per-owner migration input, separate from the changing multi-owner Proto file. */
internal class WaterAnalysisCutoverSourceFiles(context: Context) {
    private val directory = File(context.applicationContext.noBackupFilesDir, "water_history_migration")

    fun hasRetained(owner: String): Boolean = read(owner) != null

    fun retain(owner: String, store: WaterAnalysesStore): WaterAnalysisMigrationSource {
        val selected = WaterAnalysisStoreRules.validateStore(store).toBuilder().clearAnalyses()
            .addAllAnalyses(store.analysesList.filter { it.ownerUid == owner }).build()
        val bytes = selected.toByteArray()
        val current = WaterAnalysisMigrationSource.readFrom(ByteArrayInputStream(bytes), owner)
        val retained = read(owner) ?: write(owner, bytes)
        check(retained.manifest.recordCount == current.manifest.recordCount &&
            retained.manifest.recordsSha256 == current.manifest.recordsSha256) {
            "Legacy water history changed during migration; retained evidence was not overwritten."
        }
        return retained
    }

    fun clear(owner: String) {
        atomic(owner).delete()
        check(read(owner) == null) { "Water migration source could not be removed." }
    }

    private fun read(owner: String): WaterAnalysisMigrationSource? {
        val file = atomic(owner)
        val input = try {
            file.openRead()
        } catch (missing: FileNotFoundException) {
            if (file.baseFile.exists()) throw missing
            null
        }
        return input?.use { WaterAnalysisMigrationSource.readFrom(it, owner) }
    }

    private fun write(owner: String, bytes: ByteArray): WaterAnalysisMigrationSource {
        check(directory.isDirectory || directory.mkdirs()) { "Water migration directory is unavailable." }
        val file = atomic(owner)
        val output = file.startWrite()
        var committed = false
        try {
            output.write(bytes)
            file.finishWrite(output)
            committed = true
        } finally {
            if (!committed) file.failWrite(output)
        }
        return checkNotNull(read(owner))
    }

    private fun atomic(owner: String): AtomicFile {
        require(owner.isNotBlank() && owner == owner.trim())
        val name = Base64.getUrlEncoder().withoutPadding().encodeToString(owner.toByteArray(Charsets.UTF_8))
        return AtomicFile(File(directory, "$name.pb"))
    }
}
