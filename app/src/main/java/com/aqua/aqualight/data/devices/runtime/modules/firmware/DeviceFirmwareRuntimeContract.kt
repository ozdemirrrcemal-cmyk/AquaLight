package com.aqua.aqualight.data.devices.runtime.modules.firmware

/**
 * Production Android mirror of the firmware WebSocket OTA and signed manifest contracts.
 *
 * OTA control/status stays on authenticated WebSocket. The firmware downloads the matching
 * official binary itself; Android verifies and matches the signed manifest before requesting it.
 */
object DeviceFirmwareRuntimeContract {

    const val MODULE = "firmware"

    const val OFFICIAL_RELEASE_REPOSITORY =
        "ozdemirrrcemal-cmyk/AquaLight-OTA-Releases"

    const val OFFICIAL_RELEASE_URL_PREFIX =
        "https://github.com/$OFFICIAL_RELEASE_REPOSITORY/releases/download/"

    object Action {
        const val STATUS_GET = "status.get"
        const val OTA_STATUS = "ota.status"
        const val OTA_START = "ota.start"
        const val OTA_CLEAR = "ota.clear"
    }

    object Event {
        const val OTA_PROGRESS = "ota.progress"
        const val OTA_COMPLETED = "ota.completed"
    }

    object Manifest {
        const val SCHEMA = "aql.ota.manifest.v1"
        const val BRAND = "AquaLight"
        const val STABLE_CHANNEL = "stable"
        const val BETA_CHANNEL = "beta"
        const val DEV_CHANNEL = "dev"

        const val RELEASE_NOTES = "releaseNotes"
        const val RELEASE_NOTES_SCHEMA_FIELD = "schema"
        const val RELEASE_NOTES_SCHEMA = "aql.ota.release-notes.v1"
        const val DEFAULT_LOCALE = "defaultLocale"
        const val RELEASE_NOTE_ITEMS = "items"
        const val RELEASE_NOTE_TR = "tr"
        const val RELEASE_NOTE_EN = "en"
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
