package com.aqua.aqualight.data.devices.runtime.modules.firmware

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("LongMethod", "MagicNumber")
class SharedPreferencesDeviceOtaTransactionStoreTest {

    @Test
    fun activeTransactionAndQuarantineSurviveStoreRecreation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ownerUid = "ota-owner-${System.nanoTime()}"
        val deviceUid = DeviceUid("AQL-OTA-PERSISTENCE")
        val plan = preparedPlan(deviceUid.value)

        val first = SharedPreferencesDeviceOtaTransactionStore.create(context, ownerUid)
        first.saveActive(
            DeviceOtaTransaction(
                plan = plan,
                startedAtEpochMillis = 1_000L,
                recoveryDeadlineEpochMillis = 121_000L,
                awaitingVersionVerification = true
            )
        )
        first.saveQuarantine(
            DeviceOtaQuarantine(
                deviceUid = deviceUid.value,
                previousVersion = plan.currentVersion,
                rejectedVersion = plan.targetVersion,
                productKey = plan.productKey,
                hardwareRevision = plan.hardwareRevision,
                manifestTag = plan.manifestTag,
                sha256 = plan.sha256,
                recordedAtEpochMillis = 2_000L
            )
        )

        val restored = SharedPreferencesDeviceOtaTransactionStore.create(context, ownerUid)
        assertEquals(plan, restored.active(deviceUid)?.plan)
        assertEquals(121_000L, restored.active(deviceUid)?.recoveryDeadlineEpochMillis)
        assertTrue(restored.active(deviceUid)?.awaitingVersionVerification == true)
        assertTrue(restored.activeTransactions().any { transaction -> transaction.plan == plan })
        assertNotNull(restored.quarantine(deviceUid))
        assertTrue(restored.quarantine(deviceUid)?.matches(plan) == true)
        assertTrue(restored.quarantine(deviceUid)?.matches(plan.copy(sha256 = "b".repeat(64))) == false)
        assertTrue(
            SharedPreferencesDeviceOtaTransactionStore.create(context, "different-owner")
                .activeTransactions()
                .isEmpty()
        )
        restored.clearOwner()
        assertTrue(restored.activeTransactions().isEmpty())
        assertTrue(restored.quarantine(deviceUid) == null)
    }

    private fun preparedPlan(deviceUid: String) = PreparedDeviceFirmwareUpdate(
        deviceUid = deviceUid,
        currentVersion = "1.0.0",
        targetVersion = "2.0.0",
        channel = "stable",
        environment = "dosing_dose_pro_2",
        productKey = "DOSING_DOSE_PRO_2",
        productId = "com.aqualight.dosing.dose_pro_2",
        model = "dose_pro_2",
        hardwareRevision = "2.0",
        displayName = "Dose Pro 2",
        filename = "AquaLight-dosing_dose_pro_2-v2.0.0-ota.bin",
        downloadUrl = "https://github.com/ozdemirrrcemal-cmyk/AquaLight-Firmware/releases/" +
            "download/dosing_dose_pro_2-v2.0.0/AquaLight-dosing_dose_pro_2-v2.0.0-ota.bin",
        sha256 = "a".repeat(64),
        sizeBytes = 1_024,
        applyNow = true,
        runtimeMetadataGeneration = 7L,
        manifestTag = "dosing_dose_pro_2-v2.0.0",
        releaseContent = DeviceFirmwareReleaseContent(
            localeTag = "tr",
            title = "",
            summary = "",
            changes = listOf("Rollback doğrulaması."),
            warnings = emptyList(),
            mandatory = false
        )
    )
}
