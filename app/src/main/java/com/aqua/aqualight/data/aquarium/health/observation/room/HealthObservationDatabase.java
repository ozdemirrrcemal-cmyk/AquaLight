package com.aqua.aqualight.data.aquarium.health.observation.room;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/** Separate observation history; WaterAnalysisRecord never contains algae/plant/livestock events. */
@Database(entities = {HealthObservationEntity.class, HealthObservationRequest.class}, version = 1, exportSchema = true)
public abstract class HealthObservationDatabase extends RoomDatabase {
    private static volatile HealthObservationDatabase instance;

    public abstract HealthObservationDao observations();
    public abstract HealthObservationMaintenanceDao maintenance();

    public static synchronized HealthObservationDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    HealthObservationDatabase.class, "health_observation.db").build();
        }
        return instance;
    }
}
