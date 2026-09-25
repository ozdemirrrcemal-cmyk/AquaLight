package com.aqua.aqualight.data.aquarium.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.notifications.DeviceUpdateNotificationWorkCoordinator
import com.aqua.aqualight.application.notifications.NotificationPermissionPolicy
import com.aqua.aqualight.application.notifications.NotificationPreferenceRepository
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.application.notifications.NotificationRenderer
import com.aqua.aqualight.application.notifications.NotificationScheduler
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.data.aquarium.DefaultAquariumTankOperations
import com.aqua.aqualight.data.aquarium.delete.OwnerTankDataCleaner
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LivestockPhotoIsolationInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = AquariumTankDataStoreManager(context)

    @Test
    fun saveUpdatesOnlyOwnerTankAndRecordAndStaleEditsKeepThePhoto() = runBlocking {
        withTank { owner, tank ->
            val otherTank = store.addTankFromDraft(draft())
            try {
                val operation = operations()
                operation.saveLivestockWithPhoto(tank, item(11), owner, true, false)
                operation.saveLivestockWithPhoto(tank, item(12), owner, true, false)
                operation.saveLivestockWithPhoto(otherTank, item(11), owner, true, false)
                val photo = pending(owner)
                operation.saveLivestockWithPhoto(tank, item(11).copy(quantity = 4, photoUri = photo), owner, false, true)
                val selected = store.tanksSnapshotForOwner(owner).first { it.id == tank }.livestock
                assertEquals(photo, selected.first { it.id == 11L }.photoUri)
                assertEquals(4, selected.first { it.id == 11L }.quantity)
                assertNull(selected.first { it.id == 12L }.photoUri)
                assertNull(store.tanksSnapshotForOwner(owner).first { it.id == otherTank }.livestock.single().photoUri)
                operation.saveLivestockWithPhoto(tank, item(11).copy(note = "stale editor"), owner, false, false)
                assertEquals(photo, store.tanksSnapshotForOwner(owner).first { it.id == tank }.livestock.first().photoUri)
            } finally {
                store.deleteTanks(listOf(otherTank))
            }
        }
    }

    @Test
    fun foreignOwnerWrongScopeMissingRecordAndSharedPhotoAreRejected() = runBlocking {
        withTank { owner, tank ->
            val operation = operations()
            operation.saveLivestockWithPhoto(tank, item(11), owner, true, false)
            operation.saveLivestockWithPhoto(tank, item(12), owner, true, false)
            val foreignOwner = "${owner}_foreign"
            val foreign = pending(foreignOwner)
            val plant = pending(owner, AppMediaScope.PLANT)
            try {
                assertTrue(runCatching {
                    operation.saveLivestockWithPhoto(tank, item(11).copy(photoUri = foreign), owner, false, true)
                }.isFailure)
                assertTrue(AppMediaStorage.isAppOwned(context, foreign))
                assertTrue(runCatching {
                    operation.saveLivestockWithPhoto(tank, item(11).copy(photoUri = plant), owner, false, true)
                }.isFailure)
                assertTrue(AppMediaStorage.isAppOwned(context, plant))
                assertTrue(runCatching {
                    operation.removeLivestockWithPhoto(tank, 11, foreignOwner)
                }.isFailure)
                val own = pending(owner)
                operation.saveLivestockWithPhoto(tank, item(11).copy(photoUri = own), owner, false, true)
                assertTrue(runCatching {
                    operation.saveLivestockWithPhoto(tank, item(12).copy(photoUri = own), owner, false, true)
                }.isFailure)
                val missing = pending(owner)
                assertTrue(runCatching {
                    operation.saveLivestockWithPhoto(tank, item(999).copy(photoUri = missing), owner, false, true)
                }.isFailure)
                assertFalse(AppMediaStorage.isAppOwned(context, missing))
                assertTrue(AppMediaStorage.isAppOwned(context, own))
                UserDataScope.withOwnerUid(foreignOwner) {
                    assertTrue(runCatching { operation.removeLivestockWithPhoto(tank, 11, foreignOwner) }.isFailure)
                }
            } finally {
                AppMediaStorage.discardPendingMediaForOwner(context, foreignOwner)
            }
        }
    }

    @Test
    fun replacementRemovalAndTankDuplicationKeepIndependentFiles() = runBlocking {
        withTank { owner, tank ->
            val operation = operations()
            val first = pending(owner)
            operation.saveLivestockWithPhoto(tank, item(11).copy(photoUri = first), owner, true, true)
            val second = pending(owner)
            operation.saveLivestockWithPhoto(tank, item(11).copy(photoUri = second), owner, false, true)
            assertFalse(AppMediaStorage.isAppOwned(context, first))
            val copiedTank = operation.duplicateTank(tank)
            val copied = store.tanksSnapshotForOwner(owner).first { it.id == copiedTank }.livestock.single().photoUri
            try {
                assertTrue(copied != second)
                assertTrue(AppMediaStorage.isAppOwned(context, copied))
                operation.saveLivestockWithPhoto(tank, item(11), owner, false, true)
                assertFalse(AppMediaStorage.isAppOwned(context, second))
                assertTrue(AppMediaStorage.isAppOwned(context, copied))
                operation.removeLivestockWithPhoto(copiedTank, 11, owner)
                assertFalse(AppMediaStorage.isAppOwned(context, copied))
            } finally {
                store.deleteTanks(listOf(copiedTank))
            }
        }
    }

    @Test
    fun clearingOwnerTanksDeletesLivestockPhotos() = runBlocking {
        withTank { owner, tank ->
            val photo = pending(owner)
            operations().saveLivestockWithPhoto(tank, item(11).copy(photoUri = photo), owner, true, true)
            store.clearAllTanks(owner)
            assertFalse(AppMediaStorage.isAppOwned(context, photo))
        }
    }

    private suspend fun withTank(block: suspend (String, Long) -> Unit) {
        val owner = "livestock-photo-${UUID.randomUUID()}"
        UserDataScope.withOwnerUid(owner) {
            val tank = store.addTankFromDraft(draft())
            try { block(owner, tank) } finally {
                store.deleteTanks(listOf(tank))
                AppMediaStorage.discardPendingMediaForOwner(context, owner)
            }
        }
    }

    private fun item(id: Long) = AquariumLivestock(
        id = id, name = "Same species", category = "Fish", quantity = 1,
        catalogEntryId = "e194e2ec0b16b759"
    )

    private fun draft() = TankDraft(name = "Photo test", widthCm = 60, lengthCm = 40, heightCm = 40, tankType = "Planted")

    private fun operations() = DefaultAquariumTankOperations(
        context = context,
        tankStore = store,
        tankDataCleaner = OwnerTankDataCleaner(
            deleteTankRecords = { error("Unexpected tank deletion") },
            snapshotCareTasksForTank = { error("Unexpected care access") },
            deleteCareTasksForTank = { error("Unexpected care deletion") },
            restoreCareTasksForTank = { _, _ -> error("Unexpected care restore") },
            removeDeviceAssignmentsForTank = { error("Unexpected device access") },
            cancelCareTaskReminder = { _, _ -> error("Unexpected notification access") },
            reconcileCareReminders = { error("Unexpected notification access") }
        ),
        notificationPreferences = NotificationPreferenceUseCase(
            repository = unused(NotificationPreferenceRepository::class.java),
            permissionPolicy = unused(NotificationPermissionPolicy::class.java),
            scheduler = unused(NotificationScheduler::class.java),
            deviceUpdateWorkCoordinator = unused(DeviceUpdateNotificationWorkCoordinator::class.java),
            renderer = unused(NotificationRenderer::class.java)
        )
    )

    private fun <T> unused(type: Class<T>): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            error("Photo mutation unexpectedly called ${method.name}")
        }
    )

    private fun pending(owner: String, scope: AppMediaScope = AppMediaScope.LIVESTOCK): String {
        val crop = requireNotNull(AppMediaStorage.createCropOutputUri(context, scope, "7"))
        File(requireNotNull(crop.path)).writeBytes(byteArrayOf(1, 2, 3))
        return requireNotNull(AppMediaStorage.promoteCropOutput(context, scope, "7", owner, crop))
            .toString()
    }

}
