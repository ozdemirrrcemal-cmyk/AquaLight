package com.aqua.aqualight.data.devices.runtime.modules.firmware

/**
 * Exact Android mirror of the firmware WebSocket OTA and published manifest contracts.
 *
 * The values in this file follow AquaLight-Firmware/main. Product identity and OTA artifact
 * selection must never depend on an owner-defined device name.
 */
object DeviceFirmwareRuntimeContract {

    const val MODULE = "firmware"

    const val OFFICIAL_RELEASE_REPOSITORY =
        "ozdemirrrcemal-cmyk/AquaLight-OTA-Releases"

    /** Coordinated AquaLight-Firmware commit for the first production exact-product contract. */
    const val FIRMWARE_PRODUCT_RELEASE_CONTRACT_COMMIT =
        "90919c12ee269c20cff8affa4b417393126fabb6"

    const val OFFICIAL_RELEASE_URL_PREFIX =
        "https://github.com/$OFFICIAL_RELEASE_REPOSITORY/releases/download/"

    const val OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX =
        "https://raw.githubusercontent.com/$OFFICIAL_RELEASE_REPOSITORY/main/channels/"

    object Action {
        const val STATUS_GET = "status.get"
        const val OTA_STATUS = "ota.status"
        const val OTA_START = "ota.start"
        const val OTA_CLEAR = "ota.clear"
    }

    /**
     * Fully qualified names written inside firmware command-response payloads.
     *
     * These are deliberately different from the top-level WebSocket event actions registered in
     * AqlWsEventContract (`ota.progress` and `ota.completed`). Firmware publishes the qualified
     * name to its event bus and the WebSocket layer splits it into module + action for the envelope.
     */
    object Event {
        const val OTA_PROGRESS = "firmware.ota.progress"
        const val OTA_COMPLETED = "firmware.ota.completed"
    }

    object ErrorCode {
        const val BAD_REQUEST = "BAD_REQUEST"
        const val INVALID_VALUE = "INVALID_VALUE"
        const val MISSING_FIELD = "MISSING_FIELD"
        const val NOT_FOUND = "NOT_FOUND"
        const val MODULE_NOT_AVAILABLE = "MODULE_NOT_AVAILABLE"
        const val FEATURE_NOT_AVAILABLE = "FEATURE_NOT_AVAILABLE"
        const val DEVICE_BUSY = "DEVICE_BUSY"
        const val STORAGE_ERROR = "STORAGE_ERROR"
        const val HARDWARE_ERROR = "HARDWARE_ERROR"
        const val UNAUTHORIZED = "UNAUTHORIZED"
        const val INTERNAL_ERROR = "INTERNAL_ERROR"
    }

    object ErrorField {
        const val OTA = "ota"
        const val STATE = "state"
        const val WIFI = "wifi"
        const val URL = "url"
        const val VERSION = "version"
        const val SHA256 = "sha256"
        const val EXPECTED_SIZE = "expectedSize"
        const val PRODUCT_KEY = "productKey"
        const val PRODUCT_ID = "productId"
        const val MODEL = "model"
        const val HARDWARE_REVISION = "hardwareRevision"
        const val SIZE = "size"
        const val TASK = "task"
        const val SAFE_MODE = "safeMode"
        const val TLS = "tls"
        const val HTTP_STATUS = "httpStatus"
        const val FLASH = "flash"
        const val STREAM = "stream"
    }

    object Manifest {
        const val SCHEMA = "aql.ota.product-manifest.v1"
        const val BRAND = "AquaLight"
        const val STABLE_CHANNEL = "stable"
        const val BETA_CHANNEL = "beta"
        const val DEV_CHANNEL = "dev"
        const val FIRMWARE_FORMAT = "esp32-app-bin"

        const val PLATFORM_FRAMEWORK = "arduino-esp32"
        const val PLATFORM_CORE = "3.3.9"
        const val PLATFORM_PACKAGE = "pioarduino/platform-espressif32#55.03.39"
        const val PARTITION_TABLE = "aql_ota_16mb"
        const val NORMAL_OTA_ASSET_TYPE = "firmware.bin"
    }

    object ReleaseNotes {
        const val SCHEMA = "aql.ota.release-notes.v1"
        const val DEFAULT_LOCALE = "tr"
        const val TURKISH = "tr"
        const val ENGLISH = "en"
    }

    object Signature {
        const val FIELD = "signature"
        const val SCHEME_ECDSA_P256_SHA256 = "ECDSA_P256_SHA256"
    }

    object Field {
        const val URL = "url"
        const val VERSION = "version"
        const val SHA256 = "sha256"
        const val EXPECTED_SIZE = "expectedSize"
        const val APPLY_NOW = "applyNow"
        const val PRODUCT_KEY = "productKey"
        const val PRODUCT_ID = "productId"
        const val MODEL = "model"
        const val HARDWARE_REVISION = "hardwareRevision"
        const val ALLOW_INSECURE_HTTP = "allowInsecureHttp"
    }

    object Limit {
        const val SHA256_HEX_LENGTH = 64
        const val MAX_URL_LENGTH = 300
        const val MAX_RELEASE_NOTE_ITEMS = 20
        const val MAX_RELEASE_NOTE_TEXT_LENGTH = 500
    }
}
