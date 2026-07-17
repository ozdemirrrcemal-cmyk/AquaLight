package com.aqua.aqualight.data.recovery

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentsSerializer
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentsStore
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

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun allAuthoritativeProtoStoresRecoverFailClosedAndReportRecovery() = runBlocking {
        val ownerUid = "corruption-${UUID.randomUUID()}"
        val orphanDeviceUid = DeviceUid("orphan-device")
        val credentialStore = DeviceCredentialStore(context, ownerUid)
        val testDirectory = File(
            context.cacheDir,
            "proto-corruption-${UUID.randomUUID()}"
        ).apply {
            mkdirs()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        LocalDataRecoveryTracker.initialize(context)
        LocalDataRecoveryTracker.consumeRecoveredAreas()

        try {
            credentialStore.saveToken(orphanDeviceUid, VALID_TOKEN)

            val tankStore = createCorruptedStore(
                file = File(testDirectory, "aquarium_tanks.pb"),
                serializer = AquariumTanksSerializer,
                replacement = TankStoreRules.defaultStore(),
                area = LocalDataRecoveryTracker.Area.AQUARIUM_TANKS,
                scope = scope
            )
            val careStore = createCorruptedStore(
                file = File(testDirectory, "care_tasks.pb"),
                serializer = CareTasksCommercialSerializer,
                replacement = CareTaskStoreRules.defaultStore(),
                area = LocalDataRecoveryTracker.Area.CARE_TASKS,
                scope = scope
            )
            val knownStore = createCorruptedStore(
                file = File(testDirectory, "known_devices.pb"),
                serializer = KnownDevicesSerializer,
                replacement = KnownDevicesStore.getDefaultInstance(),
                area = LocalDataRecoveryTracker.Area.KNOWN_DEVICES,
                scope = scope
            )
            val assignmentStore = createCorruptedStore(
                file = File(testDirectory, "tank_device_assignments.pb"),
                serializer = TankDeviceAssignmentsSerializer,
                replacement = TankDeviceAssignmentsStore.getDefaultInstance(),
                area = LocalDataRecoveryTracker.Area.TANK_DEVICE_ASSIGNMENTS,
                scope = scope
            )

            assertTrue(tankStore.data.first().tanksList.isEmpty())
            assertTrue(careStore.data.first().tasksList.isEmpty())
            assertTrue(knownStore.data.first().devicesList.isEmpty())
            assertTrue(assignmentStore.data.first().assignmentsList.isEmpty())

            assertEquals(
                1,
                credentialStore.retainTokensFor(emptyList())
            )
            assertNull(credentialStore.getToken(orphanDeviceUid))

            assertEquals(
                setOf(
                    LocalDataRecoveryTracker.Area.AQUARIUM_TANKS,
                    LocalDataRecoveryTracker.Area.CARE_TASKS,
                    LocalDataRecoveryTracker.Area.KNOWN_DEVICES,
                    LocalDataRecoveryTracker.Area.TANK_DEVICE_ASSIGNMENTS
                ),
                LocalDataRecoveryTracker.consumeRecoveredAreas()
            )
        } finally {
            scope.cancel()
            credentialStore.clearOwner()
            testDirectory.deleteRecursively()
            LocalDataRecoveryTracker.consumeRecoveredAreas()
        }
    }

    private fun <T> createCorruptedStore(
        file: File,
        serializer: Serializer<T>,
        replacement: T,
        area: LocalDataRecoveryTracker.Area,
        scope: CoroutineScope
    ) = DataStoreFactory.create(
        serializer = serializer,
        corruptionHandler = ReplaceFileCorruptionHandler {
            LocalDataRecoveryTracker.markRecovered(area)
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
                0x0A,
                0x7F,
                0x01
            )
        )
    }

    private companion object {
        val VALID_TOKEN = "d".repeat(
            AqlBleProvisioningContract.RUNTIME_TOKEN_HEX_LENGTH
        )
    }
}
