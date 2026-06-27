package com.aqua.aqualight.data.devices.contract

/**
 * Android mirror of the firmware WebSocket runtime contract.
 *
 * WebSocket is the only runtime transport for auth, commands, events, status,
 * OTA control and module settings.
 */
object AqlWsContract {
    const val SCHEMA = "aql.ws.v1"
    const val DEFAULT_PATH = "/aql/v1/ws"
    const val DEFAULT_PROTOCOL = "aql.ws.v1"

    const val TYPE_HELLO = "hello"
    const val TYPE_AUTH = "auth"
    const val TYPE_PING = "ping"
    const val TYPE_COMMAND = "cmd"
    const val TYPE_RESPONSE = "res"
    const val TYPE_EVENT = "evt"
    const val TYPE_ERROR = "err"

    const val MODULE_DEVICE = "device"
    const val MODULE_SECURITY = "security"
    const val MODULE_TIME = "time"

    const val ACTION_DEVICE_IDENTITY_GET = "identity.get"
    const val ACTION_DEVICE_STATUS_GET = "status.get"
    const val ACTION_DEVICE_CAPABILITIES_GET = "capabilities.get"

    const val ACTION_SECURITY_STATUS_GET = "status.get"
    const val ACTION_SECURITY_PAIR = "pair"
    const val ACTION_SECURITY_UNPAIR = "unpair"
    const val ACTION_SECURITY_RESET = "reset"


    const val ACTION_TIME_STATUS_GET = "status.get"
    const val ACTION_TIME_CONFIG_APPLY = "config.apply"
    const val ACTION_TIME_PHONE_SYNC = "phone.sync"
    const val ACTION_TIME_NTP_SYNC = "ntp.sync"
    const val ACTION_TIME_RTC_SET = "rtc.set"
}
