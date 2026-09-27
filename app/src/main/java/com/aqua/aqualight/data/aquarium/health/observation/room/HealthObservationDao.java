package com.aqua.aqualight.data.aquarium.health.observation.room;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface HealthObservationDao {
    @Nullable
    @Query("SELECT * FROM health_observation WHERE ownerUid = :owner AND tankId = :tank "
            + "AND observationId = :id AND deleteState != 2 LIMIT 1")
    HealthObservationEntity record(String owner, long tank, long id);

    @Nullable
    @Query("SELECT * FROM health_observation WHERE ownerUid = :owner AND sourceOwnerUid = :sourceOwner "
            + "AND sourceObservationId = :sourceId LIMIT 1")
    HealthObservationEntity origin(String owner, String sourceOwner, long sourceId);

    @Query("SELECT * FROM health_observation WHERE ownerUid = :owner AND tankId = :tank AND kind = :kind AND deleteState != 2 "
            + "AND (observedAtMillis, createdAtMillis, observationId) < (:observed, :created, :id) "
            + "ORDER BY observedAtMillis DESC, createdAtMillis DESC, observationId DESC LIMIT 50")
    List<HealthObservationEntity> page(String owner, long tank, String kind,
            long observed, long created, long id);

    @Query("SELECT COUNT(*) FROM health_observation WHERE ownerUid = :owner AND tankId = :tank AND kind = :kind AND deleteState != 2 ")
    long count(String owner, long tank, String kind);

    @Query("SELECT EXISTS(SELECT 1 FROM health_observation WHERE ownerUid = :owner AND tankId = :tank AND kind = :kind AND deleteState != 2 "
            + "AND (observedAtMillis, createdAtMillis, observationId) < (:observed, :created, :id) LIMIT 1)")
    boolean hasOlder(String owner, long tank, String kind, long observed, long created, long id);

    @Query("SELECT * FROM health_observation WHERE ownerUid = :owner AND tankId = :tank AND kind = :kind AND subjectId = :subject AND deleteState != 2 "
            + "AND (observedAtMillis, createdAtMillis, observationId) < (:observed, :created, :id) "
            + "ORDER BY observedAtMillis DESC, createdAtMillis DESC, observationId DESC LIMIT 50")
    List<HealthObservationEntity> pageForSubject(String owner, long tank, String kind, long subject,
            long observed, long created, long id);

    @Query("SELECT COUNT(*) FROM health_observation WHERE ownerUid = :owner AND tankId = :tank AND kind = :kind AND subjectId = :subject AND deleteState != 2 ")
    long countForSubject(String owner, long tank, String kind, long subject);

    @Query("SELECT EXISTS(SELECT 1 FROM health_observation WHERE ownerUid = :owner AND tankId = :tank AND kind = :kind AND subjectId = :subject AND deleteState != 2 "
            + "AND (observedAtMillis, createdAtMillis, observationId) < (:observed, :created, :id) LIMIT 1)")
    boolean hasOlderForSubject(String owner, long tank, String kind, long subject, long observed, long created, long id);

    @Query("SELECT * FROM health_observation WHERE ownerUid = :owner AND observationId > :after "
            + "ORDER BY observationId ASC LIMIT 50")
    List<HealthObservationEntity> ownerPage(String owner, long after);

    @Query("SELECT * FROM health_observation WHERE ownerUid = :owner AND tankId = :tank "
            + "AND observationId > :after ORDER BY observationId ASC LIMIT 50")
    List<HealthObservationEntity> tankPage(String owner, long tank, long after);

    @Nullable
    @Query("SELECT * FROM health_observation_request WHERE ownerUid = :owner AND requestId = :request LIMIT 1")
    HealthObservationRequest request(String owner, String request);

    @Query("SELECT MAX(value) FROM (SELECT COALESCE(MAX(observationId), 0) AS value "
            + "FROM health_observation WHERE ownerUid = :owner UNION ALL "
            + "SELECT COALESCE(MAX(observationId), 0) AS value FROM health_observation_request WHERE ownerUid = :owner)")
    long lastAllocatedId(String owner);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(HealthObservationEntity record);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertRequest(HealthObservationRequest request);

    @Query("DELETE FROM health_observation WHERE ownerUid = :owner AND tankId = :tank "
            + "AND observationId = :id AND deleteState = 0")
    int delete(String owner, long tank, long id);
}
