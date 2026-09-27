package com.aqua.aqualight.data.aquarium.health.observation.room;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;

/** Immutable event payload; deletion staging changes only the two coordinator-owned stage columns. */
@Entity(tableName = "health_observation", primaryKeys = {"ownerUid", "observationId"}, indices = {
        @Index(value = {"ownerUid", "tankId", "kind", "observedAtMillis", "createdAtMillis", "observationId"}),
        @Index(value = {"ownerUid", "tankId", "observationId"}),
        @Index(value = {"ownerUid", "tankId", "kind", "subjectId", "observedAtMillis", "createdAtMillis", "observationId"}),
        @Index(value = {"ownerUid", "sourceOwnerUid", "sourceObservationId"}, unique = true),
        @Index(value = {"ownerUid", "restoreTransactionId"}),
        @Index(value = {"ownerUid", "deleteTransactionId"})})
public class HealthObservationEntity {
    @NonNull public final String ownerUid;
    public final long observationId;
    public final long tankId;
    @NonNull public final String kind;
    public final long subjectId;
    public final long observedAtMillis;
    public final long createdAtMillis;
    @NonNull public final String requestId;
    @NonNull public final byte[] payload;
    @NonNull public final String payloadSha256;
    @Nullable public final String photoUri;
    @NonNull public final String sourceOwnerUid;
    public final long sourceObservationId;
    @NonNull public final String sourceSha256;
    @NonNull public final String restoreTransactionId;
    @NonNull public final String deleteTransactionId;
    /** 0: visible, 1: prepared and visible, 2: removed but retained for coordinator rollback. */
    public final int deleteState;

    public HealthObservationEntity(@NonNull String ownerUid, long observationId, long tankId,
            @NonNull String kind, long subjectId, long observedAtMillis, long createdAtMillis,
            @NonNull String requestId, @NonNull byte[] payload, @NonNull String payloadSha256, @Nullable String photoUri,
            @NonNull String sourceOwnerUid, long sourceObservationId, @NonNull String sourceSha256,
            @NonNull String restoreTransactionId, @NonNull String deleteTransactionId, int deleteState) {
        this.ownerUid = ownerUid;
        this.observationId = observationId;
        this.tankId = tankId;
        this.kind = kind;
        this.subjectId = subjectId;
        this.observedAtMillis = observedAtMillis;
        this.createdAtMillis = createdAtMillis;
        this.requestId = requestId;
        this.payload = payload;
        this.payloadSha256 = payloadSha256;
        this.photoUri = photoUri;
        this.sourceOwnerUid = sourceOwnerUid;
        this.sourceObservationId = sourceObservationId;
        this.sourceSha256 = sourceSha256;
        this.restoreTransactionId = restoreTransactionId;
        this.deleteTransactionId = deleteTransactionId;
        this.deleteState = deleteState;
    }
}
