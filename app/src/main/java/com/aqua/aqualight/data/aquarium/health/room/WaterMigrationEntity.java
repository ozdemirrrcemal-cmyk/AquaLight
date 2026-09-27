package com.aqua.aqualight.data.aquarium.health.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Checkpoint and row insertions commit in the same database transaction. */
@Entity(tableName = "water_analysis_migration")
public final class WaterMigrationEntity {
    public static final int COPYING = 1;
    public static final int VERIFIED = 2;
    public static final int ACTIVE = 3;

    @PrimaryKey @NonNull public final String ownerUid;
    @NonNull public final String sourceSha256;
    @NonNull public final String recordsSha256;
    public final int expectedCount;
    public final int copiedCount;
    public final long lastAnalysisId;
    public final int state;

    public WaterMigrationEntity(@NonNull String ownerUid, @NonNull String sourceSha256,
            @NonNull String recordsSha256, int expectedCount, int copiedCount,
            long lastAnalysisId, int state) {
        this.ownerUid = ownerUid;
        this.sourceSha256 = sourceSha256;
        this.recordsSha256 = recordsSha256;
        this.expectedCount = expectedCount;
        this.copiedCount = copiedCount;
        this.lastAnalysisId = lastAnalysisId;
        this.state = state;
    }
}
