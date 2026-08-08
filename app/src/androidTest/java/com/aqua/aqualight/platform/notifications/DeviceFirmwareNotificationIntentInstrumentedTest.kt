package com.aqua.aqualight.platform.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.notifications.DeviceFirmwareNotificationIntentContract
import com.aqua.aqualight.application.notifications.DeviceFirmwareNotificationKind
import com.aqua.aqualight.application.notifications.DeviceFirmwareNotificationRoute
import com.aqua.aqualight.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceFirmwareNotificationIntentInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun availabilityIntentCarriesOwnerDeviceKindAndTargetVersion() {
        val intent = AndroidNotificationRenderer(context).deviceUpdateLaunchIntent(
            ownerUid = OWNER_UID,
            deviceUid = DEVICE_UID,
            route = DeviceFirmwareNotificationRoute(
                kind = DeviceFirmwareNotificationKind.AVAILABILITY,
                targetVersion = TARGET_VERSION
            )
        )

        assertEquals(OWNER_UID, intent.getStringExtra(MainActivity.EXTRA_OWNER_UID))
        assertEquals(
            DEVICE_UID,
            intent.getStringExtra(MainActivity.EXTRA_OPEN_DEVICE_FIRMWARE_UID)
        )
        assertEquals(
            DeviceFirmwareNotificationKind.AVAILABILITY.name,
            intent.getStringExtra(DeviceFirmwareNotificationIntentContract.EXTRA_KIND)
        )
        assertEquals(
            TARGET_VERSION,
            intent.getStringExtra(
                DeviceFirmwareNotificationIntentContract.EXTRA_TARGET_VERSION
            )
        )
    }

    private companion object {
        const val OWNER_UID = "owner-intent"
        const val DEVICE_UID = "device-intent"
        const val TARGET_VERSION = "1.5.0"
    }
}
