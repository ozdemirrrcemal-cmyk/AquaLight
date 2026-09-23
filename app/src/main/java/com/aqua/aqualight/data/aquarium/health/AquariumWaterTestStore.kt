package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.AquariumHealthMeasurementPolicy
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestInput
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestRecord
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class AquariumWaterTestStore(
    private val access: AquariumHealthStoreAccess
) {

    fun observe(
        ownerUid: String,
        tankId: Long
    ): Flow<List<AquariumWaterTestRecord>> {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("tankId", tankId)
        return access.data.map { store ->
            if (UserDataScope.currentUid() != owner) {
                emptyList()
            } else {
                store.waterTestsList
                    .asSequence()
                    .filter { test ->
                        test.ownerUid == owner && test.tankId == tankId
                    }
                    .map(AquariumHealthRecordMapper::waterTestToApplication)
                    .sortedWith(
                        compareByDescending<AquariumWaterTestRecord> {
                            it.measuredAtMillis
                        }.thenByDescending { it.id }
                    )
                    .toList()
            }
        }
    }

    suspend fun add(
        ownerUid: String,
        input: AquariumWaterTestInput,
        nowMillis: Long
    ): Long {
        val owner = access.requireOwnerUid(ownerUid)
        val canonical = canonicalize(input)
        AquariumHealthMeasurementPolicy.validateWaterTestInput(
            canonical,
            nowMillis
        )
        access.requireTank(owner, canonical.tankId)

        var createdId = 0L
        access.updateTank(owner, canonical.tankId) { current ->
            val id = AquariumHealthStoreRules.nextUniqueId(current, nowMillis)
            createdId = id
            current.toBuilder()
                .addWaterTests(
                    AquariumHealthRecordMapper.waterTestToStored(
                        input = canonical,
                        metadata = HealthRecordWriteMetadata(
                            id = id,
                            ownerUid = owner,
                            createdAtMillis = nowMillis,
                            updatedAtMillis = nowMillis
                        )
                    )
                )
                .build()
        }
        check(createdId > 0L) {
            "Aquarium health water-test creation completed without an id."
        }
        return createdId
    }

    suspend fun update(
        ownerUid: String,
        testId: Long,
        input: AquariumWaterTestInput,
        nowMillis: Long
    ) {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("testId", testId)
        val canonical = canonicalize(input)
        AquariumHealthMeasurementPolicy.validateWaterTestInput(
            canonical,
            nowMillis
        )
        access.requireTank(owner, canonical.tankId)

        access.updateTank(owner, canonical.tankId) { current ->
            val target = requireNotNull(
                current.waterTestsList.firstOrNull { test ->
                    test.ownerUid == owner && test.id == testId
                }
            ) {
                "Water-test record not found for the active owner."
            }
            require(target.tankId == canonical.tankId) {
                "A water-test record cannot move between tanks."
            }
            current.toBuilder()
                .clearWaterTests()
                .addAllWaterTests(
                    current.waterTestsList.map { stored ->
                        if (stored === target) {
                            AquariumHealthRecordMapper.waterTestToStored(
                                input = canonical,
                                metadata = HealthRecordWriteMetadata(
                                    id = target.id,
                                    ownerUid = owner,
                                    createdAtMillis = target.createdAtMillis,
                                    updatedAtMillis = nowMillis
                                )
                            )
                        } else {
                            stored
                        }
                    }
                )
                .build()
        }
    }

    suspend fun delete(
        ownerUid: String,
        testId: Long
    ) {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("testId", testId)
        val current = access.snapshot()
        val target = requireNotNull(
            current.waterTestsList.firstOrNull { test ->
                test.ownerUid == owner && test.id == testId
            }
        ) {
            "Water-test record not found for the active owner."
        }

        access.updateTank(owner, target.tankId) { store ->
            store.toBuilder()
                .clearWaterTests()
                .addAllWaterTests(
                    store.waterTestsList.filterNot { test ->
                        test.ownerUid == owner && test.id == testId
                    }
                )
                .build()
        }
    }

    private fun canonicalize(
        input: AquariumWaterTestInput
    ): AquariumWaterTestInput = input.copy(
        readings = input.readings.toList(),
        note = input.note.trim()
    )
}
