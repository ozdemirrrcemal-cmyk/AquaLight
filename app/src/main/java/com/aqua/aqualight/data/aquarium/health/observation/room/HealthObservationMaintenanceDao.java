package com.aqua.aqualight.data.aquarium.health.observation.room;

import androidx.room.Dao;
import androidx.room.Query;
import java.util.List;

/** Staging is ordered by the existing owner/tank deletion coordinator and its durable transaction ID. */
@Dao
public interface HealthObservationMaintenanceDao {
    @Query("SELECT DISTINCT deleteTransactionId FROM health_observation WHERE ownerUid = :owner "
            + "AND tankId = :tank AND deleteState != 0")
    List<String> stages(String owner, long tank);

    @Query("UPDATE health_observation SET deleteTransactionId = :transaction, deleteState = 1 "
            + "WHERE ownerUid = :owner AND tankId = :tank AND deleteState = 0")
    void prepare(String owner, long tank, String transaction);

    @Query("UPDATE health_observation SET deleteState = 2 WHERE ownerUid = :owner "
            + "AND tankId = :tank AND deleteTransactionId = :transaction")
    void remove(String owner, long tank, String transaction);

    @Query("UPDATE health_observation SET deleteTransactionId = '', deleteState = 0 "
            + "WHERE ownerUid = :owner AND tankId = :tank AND deleteTransactionId = :transaction")
    void restore(String owner, long tank, String transaction);

    @Query("DELETE FROM health_observation WHERE ownerUid = :owner AND tankId = :tank "
            + "AND deleteTransactionId = :transaction AND deleteState = 2")
    void complete(String owner, long tank, String transaction);

    @Query("DELETE FROM health_observation WHERE ownerUid = :owner AND tankId = :tank")
    void removeMissingTank(String owner, long tank);

    @Query("SELECT DISTINCT tankId FROM health_observation WHERE ownerUid = :owner "
            + "AND deleteState != 0 AND tankId > :after ORDER BY tankId ASC LIMIT 50")
    List<Long> stagedTanks(String owner, long after);

    @Query("DELETE FROM health_observation WHERE ownerUid = :owner")
    void clearEvents(String owner);

    @Query("DELETE FROM health_observation_request WHERE ownerUid = :owner")
    void clearRequests(String owner);

    @Query("DELETE FROM health_observation WHERE ownerUid = :owner AND restoreTransactionId = :transaction")
    void rollbackRestore(String owner, String transaction);
}
