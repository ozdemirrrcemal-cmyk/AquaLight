package com.aqua.aqualight.data.aquarium.health.room;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;

/** A single legacy event, with exact raw Proto bytes retained for lossless migration. */
@Entity(tableName = "water_analysis", primaryKeys = {"ownerUid", "analysisId"}, indices = {
        @Index(value = {"ownerUid", "tankId", "observedAtMillis", "createdAtMillis", "analysisId"},
                orders = {Index.Order.ASC, Index.Order.ASC, Index.Order.DESC, Index.Order.DESC, Index.Order.DESC}),
        @Index(value = {"ownerUid", "requestId"}, unique = true)
})
public final class WaterAnalysisEntity {
    @NonNull public final String ownerUid;
    public final long analysisId;
    public final long tankId;
    public final long observedAtMillis;
    public final long createdAtMillis;
    // Legacy blank request IDs become SQL NULL, so independent old rows do not collide.
    @Nullable public final String requestId;
    @NonNull public final byte[] rawProto;

    public WaterAnalysisEntity(@NonNull String ownerUid, long analysisId, long tankId,
            long observedAtMillis, long createdAtMillis, @Nullable String requestId,
            @NonNull byte[] rawProto) {
        this.ownerUid = ownerUid;
        this.analysisId = analysisId;
        this.tankId = tankId;
        this.observedAtMillis = observedAtMillis;
        this.createdAtMillis = createdAtMillis;
        this.requestId = requestId;
        this.rawProto = rawProto.clone();
    }
}
