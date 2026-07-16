package com.aqua.aqualight.ui.tabs.devices.add

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.provisioning.ProvisioningErrorCode
import com.aqua.aqualight.application.devices.provisioning.ProvisioningStatus
import com.aqua.aqualight.application.devices.provisioning.ProvisioningStatusMessage
import com.aqua.aqualight.application.text.AppTextResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningProgressPresenterTest {

    private val presenter = ProvisioningProgressPresenter(FakeTextResolver)

    @Test
    fun `wrong WiFi password returns password correction without progress`() {
        val presentation = presenter.status(
            statusMessage(ProvisioningErrorCode.WIFI_AUTH_FAILED)
        )

        assertTrue(presentation.canRetry)
        assertFalse(presentation.showProgress)
        assertEquals(
            DeviceProvisioningWifiCredentialField.PASSWORD,
            presentation.wifiCredentialFailure?.field
        )
        assertEquals(
            text(R.string.device_wifi_password_incorrect_error),
            presentation.wifiCredentialFailure?.message
        )
    }

    @Test
    fun `missing WiFi network returns SSID correction`() {
        val presentation = presenter.status(
            statusMessage(ProvisioningErrorCode.WIFI_NETWORK_NOT_FOUND)
        )

        assertEquals(
            DeviceProvisioningWifiCredentialField.SSID,
            presentation.wifiCredentialFailure?.field
        )
        assertEquals(
            text(R.string.device_wifi_network_not_found_error),
            presentation.wifiCredentialFailure?.message
        )
    }

    @Test
    fun `device network save failure remains on progress recovery instead of blaming credentials`() {
        val presentation = presenter.status(
            statusMessage(ProvisioningErrorCode.NETWORK_SAVE_FAILED)
        )

        assertTrue(presentation.canRetry)
        assertFalse(presentation.showProgress)
        assertNull(presentation.wifiCredentialFailure)
        assertEquals(
            text(R.string.device_provisioning_status_wifi_save_failed_message),
            presentation.message
        )
    }

    private fun statusMessage(errorCode: ProvisioningErrorCode) = ProvisioningStatusMessage(
        status = ProvisioningStatus.WIFI_FAILED,
        message = "firmware fallback",
        errorCode = errorCode,
        rawErrorCode = errorCode.name,
        retryable = true,
        rawPayload = "{}"
    )

    private object FakeTextResolver : AppTextResolver {
        override fun get(resId: Int, vararg args: Any): String = text(resId, *args)
    }

    private companion object {
        fun text(resId: Int, vararg args: Any): String = buildString {
            append("text:")
            append(resId)
            if (args.isNotEmpty()) {
                append(":")
                append(args.joinToString(separator = "|"))
            }
        }
    }
}
