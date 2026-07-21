package com.aqua.aqualight.data.devices.contract

/**
 * Android mirror of the firmware BLE + QR provisioning contract.
 *
 * BLE is used only for provisioning: QR claim proof, physical-reset recovery,
 * encrypted Wi-Fi credential transfer, Wi-Fi status reporting and encrypted
 * first runtime endpoint/token handoff.
 */
object AqlBleProvisioningContract {
    const val CONTRACT_VERSION = 1
    const val PROVISIONING_SECURITY_VERSION = 2
    const val BRAND = "AquaLight"

    // Firmware advertises the compact 16-bit service UUID FFF0 to keep the
    // BLE advertisement payload under the 31-byte Android-compatible limit.
    // Android APIs require the expanded Bluetooth Base UUID representation.
    const val SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
    const val DEVICE_INFO_UUID = "9f4c0002-6f5a-4b7c-9a7f-2f8a3c1d0001"
    const val START_SESSION_UUID = "9f4c0003-6f5a-4b7c-9a7f-2f8a3c1d0001"
    const val WIFI_CREDENTIALS_UUID = "9f4c0004-6f5a-4b7c-9a7f-2f8a3c1d0001"
    const val PROVISIONING_STATUS_UUID = "9f4c0005-6f5a-4b7c-9a7f-2f8a3c1d0001"
    const val RUNTIME_ENDPOINT_UUID = "9f4c0006-6f5a-4b7c-9a7f-2f8a3c1d0001"
    const val FINALIZE_SETUP_UUID = "9f4c0007-6f5a-4b7c-9a7f-2f8a3c1d0001"

    const val QR_MAX_BYTES = 512
    const val BLE_JSON_MAX_BYTES = 512

    // Wi-Fi SSID/password limits are UTF-8 byte limits in the encrypted JSON payload.
    // Do not validate these as Kotlin character counts.
    const val WIFI_SSID_MAX_LENGTH = 32
    const val WIFI_PASSWORD_MAX_LENGTH = 64
    const val CLAIM_CODE_MIN_LENGTH = 8
    const val CLAIM_CODE_MAX_LENGTH = 64

    // Runtime pairing tokens are firmware-generated 32-byte random values encoded as 64 hex chars.
    const val RUNTIME_TOKEN_HEX_LENGTH = 64

    object SessionMode {
        const val QR_CLAIM_SECURE = "qrClaimSecure"
        const val PHYSICAL_RESET_SECURE = "physicalResetSecure"
    }

    object Qr {
        const val KEY_VERSION = "v"
        const val KEY_BRAND = "brand"
        const val KEY_DEVICE_UID = "uid"
        const val KEY_SERIAL_NUMBER = "sn"
        const val KEY_PRODUCT_ID = "productId"
        const val KEY_MODEL = "model"
        const val KEY_DISPLAY_NAME = "name"
        const val KEY_HARDWARE_REVISION = "hw"
        const val KEY_SKU_CODE = "sku"
        const val KEY_PROVISIONING_ID = "pid"
        const val KEY_CLAIM_CODE = "claim"
        const val KEY_BLE_NAME = "ble"
    }

    object Json {
        const val KEY_APP_NONCE = "appNonce"
        const val KEY_DEVICE_NONCE = "deviceNonce"
        const val KEY_CONTRACT_VERSION = "contractVersion"
        const val KEY_SECURITY_VERSION = "securityVersion"
        const val KEY_SESSION_MODE = "sessionMode"
        const val KEY_START_SESSION_PROOF = "proof"
        const val KEY_APP_PUBLIC_KEY = "appPublicKey"
        const val KEY_DEVICE_PUBLIC_KEY = "devicePublicKey"
        const val KEY_ENVELOPE_SEQUENCE = "seq"
        const val KEY_DEVICE_UID = "deviceUid"
        const val KEY_SERIAL_NUMBER = "serialNumber"
        const val KEY_SHORT_ID = "shortId"
        const val KEY_BRAND = "brand"
        const val KEY_PRODUCT_ID = "productId"
        const val KEY_PRODUCT_MODEL = "productModel"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_HARDWARE_REVISION = "hardwareRevision"
        const val KEY_FIRMWARE_VERSION = "firmwareVersion"
        const val KEY_BLE_NAME = "bleName"
        const val KEY_MODE = "mode"
        const val KEY_CLAIM_REQUIRED = "claimRequired"
        const val KEY_PHYSICAL_RESET = "physicalReset"
        const val KEY_PROVISIONING_ID = "provisioningId"
        const val KEY_WIFI_SSID = "ssid"
        const val KEY_WIFI_PASSWORD = "password"
        const val KEY_WIFI_BSSID = "bssid"
        const val KEY_WIFI_CHANNEL = "channel"
        const val KEY_WIFI_TIMEZONE = "timezone"
        const val KEY_WIFI_UTC_OFFSET_MINUTES = "utcOffsetMinutes"
        const val KEY_FINALIZE_ACCEPTED = "accepted"
        const val KEY_STATUS = "status"
        const val KEY_MESSAGE = "message"
        const val KEY_LAST_ERROR = "lastError"
        const val KEY_ERROR_CODE = "errorCode"
        const val KEY_RETRYABLE = "retryable"
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
        const val FINALIZING = "finalizing"
        const val COMPLETED = "completed"
        const val TIMEOUT = "timeout"
        const val ERROR = "error"
    }

    object ErrorCode {
        const val WIFI_AUTH_FAILED = "wifiAuthFailed"
        const val WIFI_NETWORK_NOT_FOUND = "wifiNetworkNotFound"
        const val WIFI_HANDSHAKE_FAILED = "wifiHandshakeFailed"
        const val WIFI_ASSOCIATION_FAILED = "wifiAssociationFailed"
        const val WIFI_TIMEOUT = "wifiTimeout"
        const val WIFI_CONNECT_FAILED = "wifiConnectFailed"
        const val NETWORK_SAVE_FAILED = "networkSaveFailed"
        const val SETUP_CONFIRMATION_TIMEOUT = "setupConfirmationTimeout"
        const val FINALIZE_REJECTED = "finalizeRejected"
    }
}
