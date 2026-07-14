package com.aqua.aqualight.data.recovery

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentStore
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

        LocalDataRecoveryTracker.initialize(context)
        LocalDataRecoveryTracker.consumeRecoveredAreas()

        try {
            credentialStore.saveToken(orphanDeviceUid, VALID_TOKEN)
            corruptStoresBeforeFirstRead()

            val knownDevices = DeviceKnownStore(
                context = context,
                ownerUid = ownerUid
            ).loadSnapshots()
            val assignments = TankDeviceAssignmentStore
                .get(context)
                .assignmentsForOwner(ownerUid)
                .first()
            val tanks = AquariumTankDataStoreManager(context)
                .tanksSnapshotForOwner(ownerUid)
            val careTasks = CareTaskDataStoreManager
                .create(context)
                .tasksFlow
                .first()

            assertTrue(knownDevices.isEmpty())
            assertTrue(assignments.isEmpty())
            assertTrue(tanks.isEmpty())
            assertTrue(careTasks.isEmpty())

            assertEquals(
                1,
                credentialStore.retainTokensFor(
                    knownDevices.map { snapshot -> snapshot.deviceUid }
                )
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
            credentialStore.clearOwner()
            LocalDataRecoveryTracker.consumeRecoveredAreas()
        }
    }

    private suspend fun corruptStoresBeforeFirstRead() {
        withContext(Dispatchers.IO) {
            STORE_FILE_NAMES.forEach { fileName ->
                corrupt(context.dataStoreFile(fileName))
            }
        }
    }

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

        val STORE_FILE_NAMES = listOf(
            "aquarium_tanks.pb",
            "care_tasks.pb",
            "known_devices.pb",
            "tank_device_assignments.pb"
        )
    }
}
