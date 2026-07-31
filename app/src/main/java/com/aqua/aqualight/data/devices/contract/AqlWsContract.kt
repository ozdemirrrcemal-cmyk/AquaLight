package com.aqua.aqualight.data.devices.contract

/**
 * Authoritative Android mirror of the commercial AquaLight WebSocket v1 contract.
 *
 * Runtime credentials are inputs to the mutual HMAC handshake only and are never
 * serialized to the wire. The authenticated command matrix is mirrored byte-for-byte
 * from the firmware golden fixture and fails closed on any unregistered command.
 */
object AqlWsContract {
    const val SCHEMA = "aql.ws.v1"
    const val SCHEMA_VERSION = 1
    const val DEFAULT_PORT = 80
    const val DEFAULT_PATH = "/aql/v1/ws"
    const val DEFAULT_PROTOCOL = SCHEMA
    const val PROTOCOL_VERSION = 1
    const val AUTH_SCHEME = "hmac-sha256"

    const val TYPE_HELLO = "hello"
    const val TYPE_AUTH = "auth"
    const val TYPE_COMMAND = "cmd"
    const val TYPE_RESPONSE = "res"
    const val TYPE_EVENT = "evt"
    const val TYPE_ERROR = "err"

    const val MODULE_DEVICE = "device"
    const val MODULE_SECURITY = "security"
    const val MODULE_NETWORK = "network"
    const val MODULE_TIME = "time"
    const val MODULE_LIGHT = "light"
    const val MODULE_COOLING = "cooling"
    const val MODULE_TIMER = "timer"
    const val MODULE_DOSING = "dosing"
    const val MODULE_FIRMWARE = "firmware"
    const val MODULE_SYSTEM = "system"

    const val ACTION_SESSION_CHALLENGE = "session.challenge"
    const val ACTION_SESSION_AUTHENTICATE = "session.authenticate"
    const val ACTION_STATUS_GET = "status.get"
    const val ACTION_CONFIG_APPLY = "config.apply"

    const val ACTION_DEVICE_IDENTITY_GET = "identity.get"
    const val ACTION_DEVICE_STATUS_GET = ACTION_STATUS_GET
    const val ACTION_DEVICE_CAPABILITIES_GET = "capabilities.get"
    const val ACTION_DEVICE_NAME_SET = "name.set"

    const val ACTION_SECURITY_STATUS_GET = ACTION_STATUS_GET
    const val ACTION_SECURITY_PAIR = "pair"
    const val ACTION_SECURITY_UNPAIR = "unpair"
    const val ACTION_SECURITY_RESET = "reset"

    const val ACTION_NETWORK_STATUS_GET = ACTION_STATUS_GET

    const val ACTION_TIME_STATUS_GET = ACTION_STATUS_GET
    const val ACTION_TIME_CONFIG_APPLY = ACTION_CONFIG_APPLY
    const val ACTION_TIME_PHONE_SYNC = "phone.sync"
    const val ACTION_TIME_NTP_SYNC = "ntp.sync"
    const val ACTION_TIME_RTC_SET = "rtc.set"

    const val ACTION_FIRMWARE_STATUS_GET = ACTION_STATUS_GET
    const val ACTION_FIRMWARE_OTA_STATUS = "ota.status"
    const val ACTION_FIRMWARE_OTA_START = "ota.start"
    const val ACTION_FIRMWARE_OTA_CLEAR = "ota.clear"

    const val ACTION_LIGHT_STATUS_GET = ACTION_STATUS_GET
    const val ACTION_LIGHT_MANUAL_SET = "manual.set"
    const val ACTION_LIGHT_CHANNEL_REGIME_SET = "channel.regime.set"
    const val ACTION_LIGHT_PROGRAM_APPLY = "program.apply"
    const val ACTION_LIGHT_PROGRAM_DELETE = "program.delete"
    const val ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET =
        "temperature-protection.status.get"
    const val ACTION_LIGHT_TEMPERATURE_PROTECTION_SET = "temperature-protection.set"

    const val ACTION_COOLING_STATUS_GET = ACTION_STATUS_GET
    const val ACTION_COOLING_CONFIG_APPLY = ACTION_CONFIG_APPLY

    const val ACTION_TIMER_STATUS_GET = ACTION_STATUS_GET
    const val ACTION_TIMER_CONFIG_APPLY = ACTION_CONFIG_APPLY
    const val ACTION_TIMER_CHANNEL_SET = "channel.set"

    const val ACTION_DOSING_STATUS_GET = ACTION_STATUS_GET
    const val ACTION_DOSING_CONFIG_APPLY = ACTION_CONFIG_APPLY
    const val ACTION_DOSING_PRIME_START = "prime.start"
    const val ACTION_DOSING_PRIME_STOP = "prime.stop"
    const val ACTION_DOSING_CALIBRATION_START = "calibration.start"
    const val ACTION_DOSING_CALIBRATION_FINISH = "calibration.finish"
    const val ACTION_DOSING_CALIBRATION_CONFIRM = "calibration.confirm"
    const val ACTION_DOSING_CALIBRATION_CANCEL = "calibration.cancel"
    const val ACTION_DOSING_DOSE_NOW = "dose.now"
    const val ACTION_DOSING_DOSE_STOP = "dose.stop"
    const val ACTION_DOSING_RESERVOIR_REFILL = "reservoir.refill"

    object Field {
        const val ID = "id"
        const val TYPE = "type"
        const val MODULE = "module"
        const val ACTION = "action"
        const val DATA = "data"
        const val ERROR = "error"
        const val OK = "ok"
        const val STATUS = "status"
        const val META = "meta"
        const val SECURITY = "security"

        const val SCHEMA = "schema"
        const val SCHEMA_VERSION = "schemaVersion"
        const val PROTOCOL_VERSION = "protocolVersion"
        const val FIRMWARE_VERSION = "firmwareVersion"
        const val DEVICE_UID = "deviceUid"
        const val SESSION_ID = "sessionId"
        const val SERVER_NONCE = "serverNonce"
        const val CLIENT_NONCE = "clientNonce"
        const val AUTH_SCHEME = "authScheme"
        const val CLIENT_PROOF = "clientProof"
        const val SERVER_PROOF = "serverProof"
        const val MAX_MESSAGE_BYTES = "maxMessageBytes"
        const val SEQUENCE = "seq"
        const val MAC = "mac"
        const val CODE = "code"
        const val MESSAGE = "message"
        const val ERROR_FIELD = "field"
    }

    object Limit {
        const val MESSAGE_BYTES = 8_192
        const val DATA_BYTES = 4_096
        const val ID_CHARS = 96
        const val TYPE_CHARS = 16
        const val MODULE_CHARS = 32
        const val ACTION_CHARS = 64
        const val DEVICE_UID_CHARS = 96
        const val SESSION_ID_CHARS = 96
        const val FIRMWARE_VERSION_CHARS = 64
        const val ERROR_CODE_CHARS = 64
        const val ERROR_FIELD_CHARS = 64
        const val ERROR_MESSAGE_CHARS = 256
        const val NONCE_HEX_CHARS = 64
        const val MAC_HEX_CHARS = 64
        const val MAX_SEQUENCE = 9_007_199_254_740_991L
    }

    private val publicCommands = emptySet<String>()

    private val authenticatedCommands = setOf(
        commandKey(MODULE_DEVICE, ACTION_DEVICE_IDENTITY_GET),
        commandKey(MODULE_DEVICE, ACTION_DEVICE_STATUS_GET),
        commandKey(MODULE_DEVICE, ACTION_DEVICE_CAPABILITIES_GET),
        commandKey(MODULE_DEVICE, ACTION_DEVICE_NAME_SET),
        commandKey(MODULE_SECURITY, ACTION_SECURITY_STATUS_GET),
        commandKey(MODULE_SECURITY, ACTION_SECURITY_PAIR),
        commandKey(MODULE_SECURITY, ACTION_SECURITY_UNPAIR),
        commandKey(MODULE_SECURITY, ACTION_SECURITY_RESET),
        commandKey(MODULE_NETWORK, ACTION_NETWORK_STATUS_GET),
        commandKey(MODULE_TIME, ACTION_TIME_STATUS_GET),
        commandKey(MODULE_TIME, ACTION_TIME_CONFIG_APPLY),
        commandKey(MODULE_TIME, ACTION_TIME_PHONE_SYNC),
        commandKey(MODULE_TIME, ACTION_TIME_NTP_SYNC),
        commandKey(MODULE_TIME, ACTION_TIME_RTC_SET),
        commandKey(MODULE_FIRMWARE, ACTION_FIRMWARE_OTA_STATUS),
        commandKey(MODULE_FIRMWARE, ACTION_FIRMWARE_OTA_START),
        commandKey(MODULE_FIRMWARE, ACTION_FIRMWARE_OTA_CLEAR),
        commandKey(MODULE_FIRMWARE, ACTION_FIRMWARE_STATUS_GET),
        commandKey(MODULE_LIGHT, ACTION_LIGHT_STATUS_GET),
        commandKey(MODULE_LIGHT, ACTION_LIGHT_MANUAL_SET),
        commandKey(MODULE_LIGHT, ACTION_LIGHT_CHANNEL_REGIME_SET),
        commandKey(MODULE_LIGHT, ACTION_LIGHT_PROGRAM_APPLY),
        commandKey(MODULE_LIGHT, ACTION_LIGHT_PROGRAM_DELETE),
        commandKey(MODULE_LIGHT, ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET),
        commandKey(MODULE_LIGHT, ACTION_LIGHT_TEMPERATURE_PROTECTION_SET),
        commandKey(MODULE_COOLING, ACTION_COOLING_CONFIG_APPLY),
        commandKey(MODULE_COOLING, ACTION_COOLING_STATUS_GET),
        commandKey(MODULE_TIMER, ACTION_TIMER_STATUS_GET),
        commandKey(MODULE_TIMER, ACTION_TIMER_CONFIG_APPLY),
        commandKey(MODULE_TIMER, ACTION_TIMER_CHANNEL_SET),
        commandKey(MODULE_DOSING, ACTION_DOSING_CONFIG_APPLY),
        commandKey(MODULE_DOSING, ACTION_DOSING_STATUS_GET),
        commandKey(MODULE_DOSING, ACTION_DOSING_PRIME_START),
        commandKey(MODULE_DOSING, ACTION_DOSING_PRIME_STOP),
        commandKey(MODULE_DOSING, ACTION_DOSING_CALIBRATION_START),
        commandKey(MODULE_DOSING, ACTION_DOSING_CALIBRATION_FINISH),
        commandKey(MODULE_DOSING, ACTION_DOSING_CALIBRATION_CONFIRM),
        commandKey(MODULE_DOSING, ACTION_DOSING_CALIBRATION_CANCEL),
        commandKey(MODULE_DOSING, ACTION_DOSING_DOSE_NOW),
        commandKey(MODULE_DOSING, ACTION_DOSING_DOSE_STOP),
        commandKey(MODULE_DOSING, ACTION_DOSING_RESERVOIR_REFILL)
    )

    fun isRegisteredCommand(module: String, action: String): Boolean =
        isPublicCommand(module, action) || isAuthenticatedCommand(module, action)

    fun isPublicCommand(module: String, action: String): Boolean =
        commandKey(module, action) in publicCommands

    fun isAuthenticatedCommand(module: String, action: String): Boolean =
        commandKey(module, action) in authenticatedCommands

    fun publicCommandKeys(): Set<String> = publicCommands.toSet()

    fun authenticatedCommandKeys(): Set<String> = authenticatedCommands.toSet()

    private fun commandKey(module: String, action: String): String =
        "${module.trim()}.${action.trim()}"
}
