package com.aqua.aqualight.data.devices.contract

/**
 * Android mirror of the firmware BLE + QR provisioning contract.
 *
 * BLE is used only for provisioning: QR claim validation, Wi-Fi credential transfer,
 * Wi-Fi status reporting and first runtime endpoint/token handoff.
 */
object AqlBleProvisioningContract {
    const val CONTRACT_VERSION = 1
    const val BRAND = "AquaLight"

    const val SERVICE_UUID = "9f4c0001-6f5a-4b7c-9a7f-2f8a3c1d0001"
    const val DEVICE_INFO_UUID = "9f4c0002-6f5a-4b7c-9a7f-2f8a3c1d0001"
    const val START_SESSION_UUID = "9f4c0003-6f5a-4b7c-9a7f-2f8a3c1d0001"
    const val WIFI_CREDENTIALS_UUID = "9f4c0004-6f5a-4b7c-9a7f-2f8a3c1d0001"
    const val PROVISIONING_STATUS_UUID = "9f4c0005-6f5a-4b7c-9a7f-2f8a3c1d0001"
    const val RUNTIME_ENDPOINT_UUID = "9f4c0006-6f5a-4b7c-9a7f-2f8a3c1d0001"

    const val QR_MAX_BYTES = 512
    const val BLE_JSON_MAX_BYTES = 512
    const val WIFI_SSID_MAX_LENGTH = 32
    const val WIFI_PASSWORD_MAX_LENGTH = 64
    const val CLAIM_CODE_MIN_LENGTH = 8
    const val CLAIM_CODE_MAX_LENGTH = 64

    object Qr {
        const val KEY_VERSION = "v"
        const val KEY_BRAND = "brand"
        const val KEY_MODEL = "model"
        const val KEY_HARDWARE_REVISION = "hw"
        const val KEY_DEVICE_UID = "uid"
        const val KEY_PRODUCT_ID = "pid"
        const val KEY_CLAIM_CODE = "claim"
        const val KEY_BLE_NAME = "ble"
    }

    object Json {
        const val KEY_APP_NONCE = "appNonce"
        const val KEY_DEVICE_UID = "deviceUid"
        const val KEY_PROVISIONING_ID = "provisioningId"
        const val KEY_CLAIM_CODE = "claimCode"
        const val KEY_WIFI_SSID = "ssid"
        const val KEY_WIFI_PASSWORD = "password"
        const val KEY_STATUS = "status"
        const val KEY_MESSAGE = "message"
        const val KEY_LAST_ERROR = "lastError"
        const val KEY_TOKEN = "token"
        const val KEY_IP = "ip"
        const val KEY_WS_PORT = "webSocketPort"
        const val KEY_WS_PATH = "path"
        const val KEY_WS_PROTOCOL = "wsProtocol"
    }

    object Status {
        const val IDLE = "idle"
        const val FACTORY = "factory"
        const val PHYSICAL_RESET = "physicalReset"
        const val PROVISIONING_IN_PROGRESS = "provisioningInProgress"
        const val CLAIM_VALIDATING = "claimValidating"
        const val CLAIM_REJECTED = "claimRejected"
        const val WIFI_CREDENTIALS_RECEIVED = "wifiCredentialsReceived"
        const val WIFI_CONNECTING = "wifiConnecting"
        const val WIFI_CONNECTED = "wifiConnected"
        const val WIFI_FAILED = "wifiFailed"
        const val WEB_SOCKET_TOKEN_READY = "webSocketTokenReady"
        const val COMPLETED = "completed"
        const val TIMEOUT = "timeout"
        const val ERROR = "error"
    }
}
