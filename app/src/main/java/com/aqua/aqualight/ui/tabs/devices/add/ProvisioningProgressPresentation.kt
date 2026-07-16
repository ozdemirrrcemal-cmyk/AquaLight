package com.aqua.aqualight.ui.tabs.devices.add

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.provisioning.ProvisioningErrorCode
import com.aqua.aqualight.application.devices.provisioning.ProvisioningStatus
import com.aqua.aqualight.application.devices.provisioning.ProvisioningStatusMessage
import com.aqua.aqualight.application.text.AppTextResolver

internal class ProvisioningProgressPresenter(
    private val textResolver: AppTextResolver
) {

    fun status(message: ProvisioningStatusMessage): ProvisioningStatusPresentation =
        ProvisioningStatusPresentation(
            title = message.status.toTitle(),
            message = message.toMessage(),
            step = message.status.toStep(),
            showProgress = message.status !in TERMINAL_STATUSES,
            canRetry = message.status in RETRY_STATUSES,
            wifiCredentialFailure = message.toWifiCredentialFailure()
        )

    fun friendlyTransportError(message: String): String {
        val normalized = message.trim()
        return when {
            ProvisioningFailurePolicy.isSecureSessionFailure(normalized) ->
                string(R.string.device_provisioning_error_secure_session_ended)
            normalized.contains("StartSession is required", ignoreCase = true) ->
                string(R.string.device_provisioning_error_start_session)
            normalized.contains("wifi", ignoreCase = true) ->
                string(R.string.device_provisioning_error_wifi_failed)
            else -> string(R.string.device_provisioning_error_unexpected)
        }
    }

    fun friendlySaveError(message: String?): String {
        val normalized = message.orEmpty().trim()
        return when {
            ProvisioningFailurePolicy.isSecureSessionFailure(normalized) ->
                string(R.string.device_provisioning_error_secure_session_ended)
            ProvisioningFailurePolicy.isRuntimeConfirmationFailure(normalized) ->
                string(R.string.device_provisioning_error_runtime_confirmation)
            else -> string(R.string.device_provisioning_save_failed_message)
        }
    }

    private fun ProvisioningStatus.toTitle(): String = when (this) {
        ProvisioningStatus.IDLE,
        ProvisioningStatus.FACTORY,
        ProvisioningStatus.PHYSICAL_RESET ->
            string(R.string.device_provisioning_status_setup_mode_title)
        ProvisioningStatus.PROVISIONING_IN_PROGRESS,
        ProvisioningStatus.CLAIM_VALIDATING ->
            string(R.string.device_provisioning_status_verifying_title)
        ProvisioningStatus.WIFI_CREDENTIALS_RECEIVED ->
            string(R.string.device_provisioning_status_wifi_received_title)
        ProvisioningStatus.WIFI_CONNECTING ->
            string(R.string.device_provisioning_status_wifi_connecting_title)
        ProvisioningStatus.WIFI_CONNECTED ->
            string(R.string.device_provisioning_status_wifi_connected_title)
        ProvisioningStatus.WEB_SOCKET_TOKEN_READY ->
            string(R.string.device_provisioning_device_online_title)
        ProvisioningStatus.FINALIZING,
        ProvisioningStatus.COMPLETED ->
            string(R.string.device_provisioning_setup_complete_title)
        ProvisioningStatus.WIFI_FAILED ->
            string(R.string.device_provisioning_status_wifi_failed_title)
        ProvisioningStatus.CLAIM_REJECTED ->
            string(R.string.device_provisioning_status_claim_rejected_title)
        ProvisioningStatus.TIMEOUT ->
            string(R.string.device_provisioning_status_timeout_title)
        ProvisioningStatus.ERROR,
        ProvisioningStatus.UNKNOWN ->
            string(R.string.device_provisioning_status_waiting_title)
    }

    private fun ProvisioningStatusMessage.toMessage(): String {
        if (status == ProvisioningStatus.WIFI_FAILED) {
            return when (errorCode) {
                ProvisioningErrorCode.WIFI_AUTH_FAILED ->
                    string(R.string.device_provisioning_status_wifi_auth_failed_message)
                ProvisioningErrorCode.WIFI_NETWORK_NOT_FOUND ->
                    string(R.string.device_provisioning_status_wifi_network_not_found_message)
                ProvisioningErrorCode.WIFI_HANDSHAKE_FAILED,
                ProvisioningErrorCode.WIFI_ASSOCIATION_FAILED ->
                    string(R.string.device_provisioning_status_wifi_router_rejected_message)
                ProvisioningErrorCode.WIFI_TIMEOUT ->
                    string(R.string.device_provisioning_status_wifi_timeout_message)
                ProvisioningErrorCode.NETWORK_SAVE_FAILED ->
                    string(R.string.device_provisioning_status_wifi_save_failed_message)
                ProvisioningErrorCode.UNKNOWN -> message.ifBlank {
                    string(R.string.device_provisioning_status_wifi_failed_message)
                }
            }
        }
        return status.toMessage(message)
    }

    private fun ProvisioningStatus.toMessage(fallback: String): String = when (this) {
        ProvisioningStatus.IDLE,
        ProvisioningStatus.FACTORY,
        ProvisioningStatus.PHYSICAL_RESET ->
            string(R.string.device_provisioning_status_setup_mode_message)
        ProvisioningStatus.PROVISIONING_IN_PROGRESS,
        ProvisioningStatus.CLAIM_VALIDATING ->
            string(R.string.device_provisioning_status_verifying_message)
        ProvisioningStatus.WIFI_CREDENTIALS_RECEIVED ->
            string(R.string.device_provisioning_status_wifi_received_message)
        ProvisioningStatus.WIFI_CONNECTING ->
            string(R.string.device_provisioning_status_wifi_connecting_message)
        ProvisioningStatus.WIFI_CONNECTED ->
            string(R.string.device_provisioning_status_wifi_connected_message)
        ProvisioningStatus.WEB_SOCKET_TOKEN_READY ->
            string(R.string.device_provisioning_status_runtime_ready_message)
        ProvisioningStatus.FINALIZING ->
            string(R.string.device_provisioning_details_received_message)
        ProvisioningStatus.COMPLETED ->
            string(R.string.device_provisioning_status_setup_complete_message)
        ProvisioningStatus.WIFI_FAILED ->
            string(R.string.device_provisioning_status_wifi_failed_message)
        ProvisioningStatus.CLAIM_REJECTED ->
            string(R.string.device_provisioning_status_claim_rejected_message)
        ProvisioningStatus.TIMEOUT ->
            string(R.string.device_provisioning_status_timeout_message)
        ProvisioningStatus.ERROR,
        ProvisioningStatus.UNKNOWN -> fallback.ifBlank {
            string(R.string.device_provisioning_status_unknown_message)
        }
    }

    private fun ProvisioningStatus.toStep(): String = when (this) {
        ProvisioningStatus.WIFI_CREDENTIALS_RECEIVED ->
            string(R.string.device_provisioning_step_wifi_delivered)
        ProvisioningStatus.WIFI_CONNECTING ->
            string(R.string.device_provisioning_wifi_connecting_step)
        ProvisioningStatus.WIFI_CONNECTED ->
            string(R.string.device_provisioning_step_wifi_success)
        ProvisioningStatus.WEB_SOCKET_TOKEN_READY,
        ProvisioningStatus.FINALIZING ->
            string(R.string.device_provisioning_preparing_menu_step)
        ProvisioningStatus.COMPLETED ->
            string(R.string.device_provisioning_step_setup_complete)
        ProvisioningStatus.WIFI_FAILED ->
            string(R.string.device_provisioning_step_wifi_failed)
        ProvisioningStatus.CLAIM_REJECTED ->
            string(R.string.device_provisioning_step_verification_failed)
        ProvisioningStatus.TIMEOUT ->
            string(R.string.device_provisioning_step_timeout)
        ProvisioningStatus.ERROR ->
            string(R.string.device_provisioning_setup_stopped)
        else -> string(R.string.device_provisioning_step_in_progress)
    }

    private fun ProvisioningStatusMessage.toWifiCredentialFailure():
        DeviceProvisioningWifiCredentialFailure? {
        if (status != ProvisioningStatus.WIFI_FAILED) return null
        return when (errorCode) {
            ProvisioningErrorCode.WIFI_AUTH_FAILED -> DeviceProvisioningWifiCredentialFailure(
                message = string(R.string.device_wifi_password_incorrect_error),
                field = DeviceProvisioningWifiCredentialField.PASSWORD
            )
            ProvisioningErrorCode.WIFI_NETWORK_NOT_FOUND -> DeviceProvisioningWifiCredentialFailure(
                message = string(R.string.device_wifi_network_not_found_error),
                field = DeviceProvisioningWifiCredentialField.SSID
            )
            ProvisioningErrorCode.WIFI_HANDSHAKE_FAILED,
            ProvisioningErrorCode.WIFI_ASSOCIATION_FAILED ->
                DeviceProvisioningWifiCredentialFailure(
                    message = string(R.string.device_wifi_router_rejected_error),
                    field = DeviceProvisioningWifiCredentialField.PASSWORD
                )
            ProvisioningErrorCode.WIFI_TIMEOUT -> DeviceProvisioningWifiCredentialFailure(
                message = string(R.string.device_wifi_connection_timeout_error),
                field = DeviceProvisioningWifiCredentialField.PASSWORD
            )
            ProvisioningErrorCode.NETWORK_SAVE_FAILED -> null
            ProvisioningErrorCode.UNKNOWN -> DeviceProvisioningWifiCredentialFailure(
                message = string(R.string.device_wifi_provisioning_failed_error),
                field = DeviceProvisioningWifiCredentialField.PASSWORD
            )
        }
    }

    private fun string(resId: Int): String = textResolver.get(resId)

    private companion object {
        val TERMINAL_STATUSES = setOf(
            ProvisioningStatus.COMPLETED,
            ProvisioningStatus.WIFI_FAILED,
            ProvisioningStatus.CLAIM_REJECTED,
            ProvisioningStatus.TIMEOUT,
            ProvisioningStatus.ERROR
        )
        val RETRY_STATUSES = setOf(
            ProvisioningStatus.WIFI_FAILED,
            ProvisioningStatus.CLAIM_REJECTED,
            ProvisioningStatus.TIMEOUT,
            ProvisioningStatus.ERROR
        )
    }
}

internal data class ProvisioningStatusPresentation(
    val title: String,
    val message: String,
    val step: String,
    val showProgress: Boolean,
    val canRetry: Boolean,
    val wifiCredentialFailure: DeviceProvisioningWifiCredentialFailure?
)

internal object ProvisioningFailurePolicy {
    fun isSecureSessionFailure(message: String): Boolean =
        message.contains("status 147", ignoreCase = true) ||
            message.contains("ECDH", ignoreCase = true) ||
            message.contains("BAD_DECRYPT", ignoreCase = true) ||
            message.contains("decrypt", ignoreCase = true) ||
            message.contains(
                "secure BLE provisioning session is not active",
                ignoreCase = true
            ) ||
            message.contains("GATT connection is not active", ignoreCase = true)

    fun isRuntimeConfirmationFailure(message: String): Boolean =
        message.contains("identity and capabilities", ignoreCase = true) ||
            message.contains("supported product family", ignoreCase = true)
}
