package com.aqua.aqualight.data.aquarium.health

import java.io.ByteArrayInputStream

internal object WaterRoomFixture {
    const val OWNER = "water-room-owner"
    const val TANK_ID = 2L
    const val TIME = 1_770_000_000_000L

    fun record(id: Long): StoredWaterAnalysis = StoredWaterAnalysis.newBuilder()
        .setOwnerUid(OWNER).setId(id).setTankId(TANK_ID)
        .setMeasuredAtMillis(TIME).setCreatedAtMillis(TIME)
        .addMeasurements(StoredWaterMeasurement.newBuilder()
            .setParameter("NITRATE").setBasis("NO3").setUnit("MG_L").setMethod("MANUAL").setValue(2.0))
        .build()

    fun source(rows: List<StoredWaterAnalysis>): WaterAnalysisMigrationSource {
        val store = WaterAnalysesStore.newBuilder().setSchemaVersion(3).addAllAnalyses(rows).build()
        return WaterAnalysisMigrationSource.readFrom(ByteArrayInputStream(store.toByteArray()), OWNER)
    }
}
