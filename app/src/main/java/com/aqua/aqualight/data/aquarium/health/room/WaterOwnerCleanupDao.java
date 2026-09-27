package com.aqua.aqualight.data.aquarium.health.room;

import androidx.room.Dao;
import androidx.room.Query;

/** Explicit account cleanup only; every table is restricted to the requested owner. */
@Dao
public interface WaterOwnerCleanupDao {
    @Query("DELETE FROM water_analysis WHERE ownerUid = :ownerUid")
    void events(String ownerUid);

    @Query("DELETE FROM water_analysis_request WHERE ownerUid = :ownerUid")
    void requests(String ownerUid);

    @Query("DELETE FROM water_analysis_migration WHERE ownerUid = :ownerUid")
    void migration(String ownerUid);

    @Query("DELETE FROM water_analysis_delete_stage WHERE ownerUid = :ownerUid")
    void stagedRows(String ownerUid);

    @Query("DELETE FROM water_analysis_delete_manifest WHERE ownerUid = :ownerUid")
    void stagedManifests(String ownerUid);
}
