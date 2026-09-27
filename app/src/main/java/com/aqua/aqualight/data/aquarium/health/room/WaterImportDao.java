package com.aqua.aqualight.data.aquarium.health.room;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface WaterImportDao {
    @Nullable
    @Query("SELECT * FROM water_analysis_import WHERE ownerUid = :ownerUid "
            + "AND sourceOwnerUid = :sourceOwnerUid AND sourceAnalysisId = :sourceAnalysisId LIMIT 1")
    WaterImportEntity original(String ownerUid, String sourceOwnerUid, long sourceAnalysisId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(WaterImportEntity mapping);

    @Query("SELECT event.* FROM water_analysis AS event INNER JOIN water_analysis_import AS origin "
            + "ON event.ownerUid = origin.ownerUid AND event.analysisId = origin.analysisId "
            + "WHERE origin.ownerUid = :ownerUid AND origin.restoreTransactionId = :transactionId "
            + "AND origin.analysisId > :afterId ORDER BY origin.analysisId ASC LIMIT 50")
    List<WaterAnalysisEntity> transactionPage(String ownerUid, String transactionId, long afterId);

    @Query("DELETE FROM water_analysis WHERE ownerUid = :ownerUid AND analysisId IN "
            + "(SELECT analysisId FROM water_analysis_import WHERE ownerUid = :ownerUid "
            + "AND restoreTransactionId = :transactionId)")
    int rollback(String ownerUid, String transactionId);
}
