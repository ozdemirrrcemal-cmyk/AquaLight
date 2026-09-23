package com.aqua.aqualight.data.recovery

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentsSerializer
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentsStore
import com.aqua.aqualight.data.aquarium.health.AquariumHealthCommercialSerializer
import com.aqua.aqualight.data.aquarium.health.AquariumHealthStore
import com.aqua.aqualight.data.aquarium.health.AquariumHealthStoreRules
import com.aqua.aqualight.data.aquarium.store.AquariumTanksSerializer
import com.aqua.aqualight.data.aquarium.store.AquariumTanksStore
import com.aqua.aqualight.data.aquarium.store.TankStoreRules
import com.aqua.aqualight.data.care.CareTaskStoreRules
import com.aqua.aqualight.data.care.CareTasksCommercialSerializer
import com.aqua.aqualight.data.care.CareTasksStore
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import com.aqua.aqualight.data.devices.store.KnownDevicesSerializer
import com.aqua.aqualight.data.devices.store.KnownDevicesStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtoCorruptionRecoveryInstrumentedTest {

    private val context =
        ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun allAuthoritativeProtoStoresRecoverFailClosedAndReportRecovery() =
        runBlocking {
            val ownerUid =
                "corruption-" + UUID.randomUUID()
            val orphanDeviceUid =
                DeviceUid("orphan-device")
            val credentialStore =
                DeviceCredentialStore(context, ownerUid)
            val testDirectory = createTestDirectory()
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.IO
            )

            LocalDataRecoveryTracker.initialize(context)
            LocalDataRecoveryTracker.consumeRecoveredAreas()

            try {
                credentialStore.saveToken(
                    orphanDeviceUid,
                    VALID_TOKEN
                )
                val stores = createRecoveryStores(
                    directory = testDirectory,
                    scope = scope
                )

                assertStoresRecoveredEmpty(stores)
                assertCredentialCleanup(
                    credentialStore,
                    orphanDeviceUid
                )
                assertEquals(
                    EXPECTED_RECOVERY_AREAS,
                    LocalDataRecoveryTracker
                        .consumeRecoveredAreas()
                )
            } finally {
                scope.cancel()
                credentialStore.clearOwner()
                testDirectory.deleteRecursively()
                LocalDataRecoveryTracker
                    .consumeRecoveredAreas()
            }
        }

    private fun createTestDirectory(): File =
        File(
            context.cacheDir,
            "proto-corruption-" + UUID.randomUUID()
        ).apply {
            mkdirs()
        }

    private fun createRecoveryStores(
        directory: File,
        scope: CoroutineScope
    ): RecoveryStores = RecoveryStores(
        tanks = createCorruptedStore(
            file = File(directory, "aquarium_tanks.pb"),
            serializer = AquariumTanksSerializer,
            replacement = TankStoreRules.defaultStore(),
            area =
                LocalDataRecoveryTracker.Area.AQUARIUM_TANKS,
            scope = scope
        ),
        health = createCorruptedStore(
            file = File(directory, "aquarium_health.pb"),
            serializer = AquariumHealthCommercialSerializer,
            replacement =
                AquariumHealthStoreRules.defaultStore(),
            area =
                LocalDataRecoveryTracker.Area.AQUARIUM_HEALTH,
            scope = scope
        ),
        care = createCorruptedStore(
            file = File(directory, "care_tasks.pb"),
            serializer = CareTasksCommercialSerializer,
            replacement = CareTaskStoreRules.defaultStore(),
            area =
                LocalDataRecoveryTracker.Area.CARE_TASKS,
            scope = scope
        ),
        knownDevices = createCorruptedStore(
            file = File(directory, "known_devices.pb"),
            serializer = KnownDevicesSerializer,
            replacement =
                KnownDevicesStore.getDefaultInstance(),
            area =
                LocalDataRecoveryTracker.Area.KNOWN_DEVICES,
            scope = scope
        ),
        assignments = createCorruptedStore(
            file = File(
                directory,
                "tank_device_assignments.pb"
            ),
            serializer = TankDeviceAssignmentsSerializer,
            replacement =
                TankDeviceAssignmentsStore
                    .getDefaultInstance(),
            area =
                LocalDataRecoveryTracker.Area
                    .TANK_DEVICE_ASSIGNMENTS,
            scope = scope
        )
    )

    private suspend fun assertStoresRecoveredEmpty(
        stores: RecoveryStores
    ) {
        assertTrue(
            stores.tanks.data.first().tanksList.isEmpty()
        )
        val health = stores.health.data.first()
        assertTrue(health.waterTestsList.isEmpty())
        assertTrue(health.livestockObservationsList.isEmpty())
        assertTrue(health.plantObservationsList.isEmpty())
        assertTrue(
            stores.care.data.first().tasksList.isEmpty()
        )
        assertTrue(
            stores.knownDevices.data.first()
                .devicesList
                .isEmpty()
        )
        assertTrue(
            stores.assignments.data.first()
                .assignmentsList
                .isEmpty()
        )
    }

    private suspend fun assertCredentialCleanup(
        credentialStore: DeviceCredentialStore,
        orphanDeviceUid: DeviceUid
    ) {
        assertEquals(
            1,
            credentialStore.retainTokensFor(emptyList())
        )
        assertNull(
            credentialStore.getToken(orphanDeviceUid)
        )
    }

    private fun <T> createCorruptedStore(
        file: File,
        serializer: Serializer<T>,
        replacement: T,
        area: LocalDataRecoveryTracker.Area,
        scope: CoroutineScope
    ): DataStore<T> = DataStoreFactory.create(
        serializer = serializer,
        corruptionHandler =
            ReplaceFileCorruptionHandler {
                LocalDataRecoveryTracker
                    .markRecovered(area)
                replacement
            },
        scope = scope,
        produceFile = {
            corrupt(file)
            file
        }
    )

    private fun corrupt(file: File) {
        file.parentFile?.mkdirs()
        file.writeBytes(
            byteArrayOf(
                CORRUPT_PROTO_TAG,
                CORRUPT_PROTO_LENGTH,
                CORRUPT_PROTO_VALUE
            )
        )
    }

    private data class RecoveryStores(
        val tanks: DataStore<AquariumTanksStore>,
        val health: DataStore<AquariumHealthStore>,
        val care: DataStore<CareTasksStore>,
        val knownDevices: DataStore<KnownDevicesStore>,
        val assignments:
            DataStore<TankDeviceAssignmentsStore>
    )

    private companion object {
        const val CORRUPT_PROTO_TAG: Byte = 0x0A
        const val CORRUPT_PROTO_LENGTH: Byte = 0x7F
        const val CORRUPT_PROTO_VALUE: Byte = 0x01

        val VALID_TOKEN = "d".repeat(
            AqlBleProvisioningContract
                .RUNTIME_TOKEN_HEX_LENGTH
        )

        val EXPECTED_RECOVERY_AREAS = setOf(
            LocalDataRecoveryTracker.Area.AQUARIUM_TANKS,
            LocalDataRecoveryTracker.Area.AQUARIUM_HEALTH,
            LocalDataRecoveryTracker.Area.CARE_TASKS,
            LocalDataRecoveryTracker.Area.KNOWN_DEVICES,
            LocalDataRecoveryTracker.Area
                .TANK_DEVICE_ASSIGNMENTS
        )
    }
}
