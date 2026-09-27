package com.aqua.aqualight.data.aquarium.health.observation.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

/** Retained after event deletion so a delayed create cannot resurrect an observation. */
@Entity(tableName = "health_observation_request", primaryKeys = {"ownerUid", "requestId"}, indices = {
        @Index(value = {"ownerUid", "observationId"}, unique = true)})
public class HealthObservationRequest {
    @NonNull public final String ownerUid;
    @NonNull public final String requestId;
    public final long observationId;
    @NonNull public final String payloadSha256;

    public HealthObservationRequest(@NonNull String ownerUid, @NonNull String requestId,
            long observationId, @NonNull String payloadSha256) {
        this.ownerUid = ownerUid;
        this.requestId = requestId;
        this.observationId = observationId;
        this.payloadSha256 = payloadSha256;
    }
}
