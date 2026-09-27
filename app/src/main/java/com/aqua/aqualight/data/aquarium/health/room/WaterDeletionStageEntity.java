package com.aqua.aqualight.data.aquarium.health.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/** Bounded-query rollback rows; never placed in the SharedPreferences integrity journal. */
@Entity(tableName = "water_analysis_delete_stage", primaryKeys = {"ownerUid", "tankId", "analysisId"})
public final class WaterDeletionStageEntity {
    @NonNull public final String ownerUid;
    public final long tankId;
    public final long analysisId;
    @NonNull public final byte[] rawProto;

    public WaterDeletionStageEntity(@NonNull String ownerUid, long tankId, long analysisId,
            @NonNull byte[] rawProto) {
        this.ownerUid = ownerUid;
        this.tankId = tankId;
        this.analysisId = analysisId;
        this.rawProto = rawProto.clone();
    }
}
