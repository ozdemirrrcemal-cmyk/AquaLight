package com.aqua.aqualight.data.aquarium.health.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

/** Kept after event deletion so a delayed create retry cannot resurrect the event. */
@Entity(tableName = "water_analysis_request", primaryKeys = {"ownerUid", "requestId"}, indices = {
        @Index(value = {"ownerUid", "analysisId"}, unique = true)
})
public final class WaterRequestEntity {
    @NonNull public final String ownerUid;
    @NonNull public final String requestId;
    public final long analysisId;
    @NonNull public final String payloadSha256;

    public WaterRequestEntity(@NonNull String ownerUid, @NonNull String requestId,
            long analysisId, @NonNull String payloadSha256) {
        this.ownerUid = ownerUid;
        this.requestId = requestId;
        this.analysisId = analysisId;
        this.payloadSha256 = payloadSha256;
    }
}
