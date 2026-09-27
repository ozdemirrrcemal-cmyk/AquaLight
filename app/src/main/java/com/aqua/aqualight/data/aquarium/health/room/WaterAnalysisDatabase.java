package com.aqua.aqualight.data.aquarium.health.room;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/** Dedicated indexed history. Never opts into destructive migration or main-thread I/O. */
@Database(entities = {WaterAnalysisEntity.class, WaterMigrationEntity.class}, version = 1, exportSchema = true)
public abstract class WaterAnalysisDatabase extends RoomDatabase {
    private static volatile WaterAnalysisDatabase instance;

    public abstract WaterAnalysisDao analyses();

    public static synchronized WaterAnalysisDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    WaterAnalysisDatabase.class, "water_analysis.db").build();
        }
        return instance;
    }
}
