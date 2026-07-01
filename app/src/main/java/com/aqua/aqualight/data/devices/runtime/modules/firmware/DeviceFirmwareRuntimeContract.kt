package com.aqua.aqualight.data.devices.runtime.modules.firmware

/**
 * Production Android mirror of the firmware WebSocket OTA contract.
 *
 * OTA is device firmware level logic. Product screens may expose update entry points, but all
 * matching, validation, command payload generation and progress parsing must stay in this shared
 * module.
 */
object DeviceFirmwareRuntimeContract {

    const val MODULE = "firmware"

    const val OFFICIAL_RELEASE_URL_PREFIX =
        "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/releases/download/"

    object Action {
        const val STATUS_GET = "status.get"
        const val OTA_STATUS = "ota.status"
        const val OTA_START = "ota.start"
        const val OTA_CLEAR = "ota.clear"
    }

    object Event {
        const val OTA_PROGRESS = "firmware.ota.progress"
        const val OTA_COMPLETED = "firmware.ota.completed"
    }

    object Manifest {
        const val SCHEMA = "aql.ota.manifest.v1"
        const val BRAND = "AquaLight"
        const val STABLE_CHANNEL = "stable"
        const val BETA_CHANNEL = "beta"
        const val DEV_CHANNEL = "dev"
    }

    object Field {
        const val URL = "url"
        const val VERSION = "version"
        const val SHA256 = "sha256"
        const val EXPECTED_SIZE = "expectedSize"
        const val APPLY_NOW = "applyNow"
        const val PRODUCT_KEY = "productKey"
        const val PRODUCT_ID = "productId"
        const val HARDWARE_REVISION = "hardwareRevision"
        const val ALLOW_INSECURE_HTTP = "allowInsecureHttp"
    }

    object Limit {
        const val SHA256_HEX_LENGTH = 64
        const val MAX_URL_LENGTH = 300
    }
}
