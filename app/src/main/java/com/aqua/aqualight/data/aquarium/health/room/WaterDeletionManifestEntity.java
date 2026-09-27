package com.aqua.aqualight.data.aquarium.health.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "water_analysis_delete_manifest", primaryKeys = {"ownerUid", "tankId"})
public final class WaterDeletionManifestEntity {
    public static final int PREPARED = 1;
    public static final int REMOVED = 2;

    @NonNull public final String ownerUid;
    public final long tankId;
    @NonNull public final String transactionId;
    public final long recordCount;
    @NonNull public final String sha256;
    public final int state;

    public WaterDeletionManifestEntity(@NonNull String ownerUid, long tankId,
            @NonNull String transactionId, long recordCount, @NonNull String sha256, int state) {
        this.ownerUid = ownerUid;
        this.tankId = tankId;
        this.transactionId = transactionId;
        this.recordCount = recordCount;
        this.sha256 = sha256;
        this.state = state;
    }
}
