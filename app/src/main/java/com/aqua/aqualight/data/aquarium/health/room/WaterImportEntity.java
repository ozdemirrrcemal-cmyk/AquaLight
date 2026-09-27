package com.aqua.aqualight.data.aquarium.health.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Indexed original archive identity; deleting an event also removes its live import mapping. */
@Entity(tableName = "water_analysis_import",
        primaryKeys = {"ownerUid", "sourceOwnerUid", "sourceAnalysisId"},
        foreignKeys = @ForeignKey(entity = WaterAnalysisEntity.class,
                parentColumns = {"ownerUid", "analysisId"}, childColumns = {"ownerUid", "analysisId"},
                onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"ownerUid", "analysisId"}, unique = true),
                @Index(value = {"ownerUid", "restoreTransactionId", "analysisId"})})
public final class WaterImportEntity {
    @NonNull public final String ownerUid;
    @NonNull public final String sourceOwnerUid;
    public final long sourceAnalysisId;
    public final long analysisId;
    @NonNull public final String restoreTransactionId;
    @NonNull public final String sourceRecordSha256;

    public WaterImportEntity(@NonNull String ownerUid, @NonNull String sourceOwnerUid,
            long sourceAnalysisId, long analysisId, @NonNull String restoreTransactionId,
            @NonNull String sourceRecordSha256) {
        this.ownerUid = ownerUid;
        this.sourceOwnerUid = sourceOwnerUid;
        this.sourceAnalysisId = sourceAnalysisId;
        this.analysisId = analysisId;
        this.restoreTransactionId = restoreTransactionId;
        this.sourceRecordSha256 = sourceRecordSha256;
    }
}
