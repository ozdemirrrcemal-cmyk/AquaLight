package com.aqua.aqualight.data.aquarium.health.room;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/** Every query explicitly includes an owner; public history queries additionally include the tank. */
@Dao
public interface WaterAnalysisDao {
    @Nullable
    @Query("SELECT * FROM water_analysis WHERE ownerUid = :ownerUid AND tankId = :tankId "
            + "AND analysisId = :analysisId LIMIT 1")
    WaterAnalysisEntity record(String ownerUid, long tankId, long analysisId);

    @Nullable
    @Query("SELECT * FROM water_analysis WHERE ownerUid = :ownerUid AND analysisId = :analysisId LIMIT 1")
    WaterAnalysisEntity recordForOwner(String ownerUid, long analysisId);

    @Nullable
    @Query("SELECT * FROM water_analysis_request WHERE ownerUid = :ownerUid AND requestId = :requestId LIMIT 1")
    WaterRequestEntity request(String ownerUid, String requestId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertRequests(List<WaterRequestEntity> requests);

    @Query("SELECT MAX(value) FROM (SELECT COALESCE(MAX(analysisId), 0) AS value FROM water_analysis "
            + "WHERE ownerUid = :ownerUid UNION ALL SELECT COALESCE(MAX(analysisId), 0) AS value "
            + "FROM water_analysis_request WHERE ownerUid = :ownerUid UNION ALL "
            + "SELECT COALESCE(MAX(lastAnalysisId), 0) AS value FROM water_analysis_migration "
            + "WHERE ownerUid = :ownerUid)")
    long lastAllocatedId(String ownerUid);

    @Nullable
    @Query("SELECT * FROM water_analysis WHERE ownerUid = :ownerUid AND tankId = :tankId "
            + "ORDER BY observedAtMillis DESC, createdAtMillis DESC, analysisId DESC LIMIT 1")
    WaterAnalysisEntity latest(String ownerUid, long tankId);

    @Query("SELECT * FROM water_analysis WHERE ownerUid = :ownerUid AND tankId = :tankId "
            + "ORDER BY observedAtMillis DESC, createdAtMillis DESC, analysisId DESC LIMIT 50")
    List<WaterAnalysisEntity> firstPage(String ownerUid, long tankId);

    @Query("SELECT * FROM water_analysis WHERE ownerUid = :ownerUid AND tankId = :tankId AND "
            + "(observedAtMillis, createdAtMillis, analysisId) < (:observedAtMillis, :createdAtMillis, :analysisId) "
            + "ORDER BY observedAtMillis DESC, createdAtMillis DESC, analysisId DESC LIMIT 50")
    List<WaterAnalysisEntity> pageAfter(String ownerUid, long tankId, long observedAtMillis,
            long createdAtMillis, long analysisId);

    @Query("SELECT EXISTS(SELECT 1 FROM water_analysis WHERE ownerUid = :ownerUid AND tankId = :tankId "
            + "AND (observedAtMillis, createdAtMillis, analysisId) < "
            + "(:observedAtMillis, :createdAtMillis, :analysisId) LIMIT 1)")
    boolean hasOlder(String ownerUid, long tankId, long observedAtMillis, long createdAtMillis, long analysisId);

    @Query("SELECT COUNT(*) FROM water_analysis WHERE ownerUid = :ownerUid")
    long countForOwner(String ownerUid);

    @Query("SELECT COUNT(*) FROM water_analysis WHERE ownerUid = :ownerUid AND tankId = :tankId")
    long countForTank(String ownerUid, long tankId);

    @Query("SELECT * FROM water_analysis WHERE ownerUid = :ownerUid AND analysisId > :analysisId "
            + "ORDER BY analysisId ASC LIMIT 50")
    List<WaterAnalysisEntity> migrationPage(String ownerUid, long analysisId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(List<WaterAnalysisEntity> rows);

    @Query("DELETE FROM water_analysis WHERE ownerUid = :ownerUid AND tankId = :tankId AND analysisId = :analysisId")
    int delete(String ownerUid, long tankId, long analysisId);

    @Nullable
    @Query("SELECT * FROM water_analysis_migration WHERE ownerUid = :ownerUid LIMIT 1")
    WaterMigrationEntity migration(String ownerUid);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveMigration(WaterMigrationEntity migration);
}
