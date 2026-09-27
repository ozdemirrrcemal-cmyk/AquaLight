package com.aqua.aqualight.data.aquarium.health.room;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface WaterDeletionDao {
    @Query("INSERT INTO water_analysis_delete_stage(ownerUid, tankId, analysisId, rawProto) "
            + "SELECT ownerUid, tankId, analysisId, rawProto FROM water_analysis "
            + "WHERE ownerUid = :ownerUid AND tankId = :tankId")
    void capture(String ownerUid, long tankId);

    @Query("SELECT * FROM water_analysis_delete_stage WHERE ownerUid = :ownerUid AND tankId = :tankId "
            + "AND analysisId > :afterId ORDER BY analysisId ASC LIMIT 50")
    List<WaterDeletionStageEntity> page(String ownerUid, long tankId, long afterId);

    @Query("SELECT COUNT(*) FROM water_analysis_delete_stage WHERE ownerUid = :ownerUid AND tankId = :tankId")
    long count(String ownerUid, long tankId);

    @Nullable
    @Query("SELECT * FROM water_analysis_delete_manifest WHERE ownerUid = :ownerUid AND tankId = :tankId")
    WaterDeletionManifestEntity manifest(String ownerUid, long tankId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertManifest(WaterDeletionManifestEntity manifest);

    @Query("UPDATE water_analysis_delete_manifest SET state = 2 WHERE ownerUid = :ownerUid AND tankId = :tankId")
    void markRemoved(String ownerUid, long tankId);

    @Query("DELETE FROM water_analysis WHERE ownerUid = :ownerUid AND tankId = :tankId")
    int removeEvents(String ownerUid, long tankId);

    @Query("DELETE FROM water_analysis_delete_stage WHERE ownerUid = :ownerUid AND tankId = :tankId")
    void clearRows(String ownerUid, long tankId);

    @Query("DELETE FROM water_analysis_delete_manifest WHERE ownerUid = :ownerUid AND tankId = :tankId")
    void clearManifest(String ownerUid, long tankId);
}
