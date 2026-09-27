package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase

internal class WaterAnalysisOwnerCleanup(private val database: WaterAnalysisDatabase) {
    /** Caller has durably removed this owner's legacy source before clearing its activation checkpoint. */
    fun clear(owner: String) {
        require(owner.isNotBlank() && owner == owner.trim())
        database.runInTransaction {
            val cleanup = database.ownerCleanup()
            cleanup.events(owner)
            cleanup.requests(owner)
            cleanup.stagedRows(owner)
            cleanup.stagedManifests(owner)
            cleanup.migration(owner)
        }
    }
}
