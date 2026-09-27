package com.aqua.aqualight.data.aquarium.health.room;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;

/** Dedicated indexed history. Never opts into destructive migration or main-thread I/O. */
@Database(entities = {WaterAnalysisEntity.class, WaterMigrationEntity.class, WaterRequestEntity.class,
        WaterDeletionStageEntity.class, WaterDeletionManifestEntity.class},
        version = 2, exportSchema = true)
public abstract class WaterAnalysisDatabase extends RoomDatabase {
    private static volatile WaterAnalysisDatabase instance;

    public abstract WaterAnalysisDao analyses();
    public abstract WaterDeletionDao deletions();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS water_analysis_request "
                    + "(ownerUid TEXT NOT NULL, requestId TEXT NOT NULL, analysisId INTEGER NOT NULL, "
                    + "payloadSha256 TEXT NOT NULL, PRIMARY KEY(ownerUid, requestId))");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_water_analysis_request_ownerUid_analysisId "
                    + "ON water_analysis_request(ownerUid, analysisId)");
            database.execSQL("CREATE TABLE IF NOT EXISTS water_analysis_delete_stage "
                    + "(ownerUid TEXT NOT NULL, tankId INTEGER NOT NULL, analysisId INTEGER NOT NULL, "
                    + "rawProto BLOB NOT NULL, PRIMARY KEY(ownerUid, tankId, analysisId))");
            database.execSQL("CREATE TABLE IF NOT EXISTS water_analysis_delete_manifest "
                    + "(ownerUid TEXT NOT NULL, tankId INTEGER NOT NULL, transactionId TEXT NOT NULL, "
                    + "recordCount INTEGER NOT NULL, sha256 TEXT NOT NULL, state INTEGER NOT NULL, "
                    + "PRIMARY KEY(ownerUid, tankId))");
        }
    };

    public static synchronized WaterAnalysisDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    WaterAnalysisDatabase.class, "water_analysis.db")
                    .addMigrations(MIGRATION_1_2).build();
        }
        return instance;
    }
}
