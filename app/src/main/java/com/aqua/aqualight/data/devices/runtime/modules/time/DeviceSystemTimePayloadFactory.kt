package com.aqua.aqualight.data.devices.runtime.modules.time

import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object DeviceSystemTimePayloadFactory {

    fun currentTimeZoneSnapshot(): DeviceTimeZoneSnapshot {
        val timeZone = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val offsetMinutes = timeZone.getOffset(now) / 60_000
        val timezoneId = timeZone.id.ifBlank { "UTC" }

        return DeviceTimeZoneSnapshot(
            timezoneId = timezoneId,
            posixTimeZone = DevicePosixTimeZoneMapper.posixFor(
                timezoneId = timezoneId,
                utcOffsetMinutes = offsetMinutes
            ),
            utcOffsetMinutes = offsetMinutes
        )
    }

    fun phoneSyncNow(
        save: Boolean = true
    ): DevicePhoneSyncPayload {
        val zone = currentTimeZoneSnapshot()

        return DevicePhoneSyncPayload(
            epochMillis = System.currentTimeMillis(),
            timezoneId = zone.timezoneId,
            posixTimeZone = zone.posixTimeZone,
            utcOffsetMinutes = zone.utcOffsetMinutes,
            ntpEnabled = true,
            gadgetSyncEnabled = true,
            save = save
        )
    }

    fun configFromSystem(
        save: Boolean = true
    ): DeviceTimeConfigApplyPayload {
        val zone = currentTimeZoneSnapshot()

        return DeviceTimeConfigApplyPayload(
            timezoneId = zone.timezoneId,
            posixTimeZone = zone.posixTimeZone,
            utcOffsetMinutes = zone.utcOffsetMinutes,
            ntpEnabled = true,
            gadgetSyncEnabled = true,
            save = save
        )
    }
}

object DevicePosixTimeZoneMapper {

    private val europeCentral = setOf(
        "Europe/Berlin",
        "Europe/Paris",
        "Europe/Rome",
        "Europe/Madrid",
        "Europe/Amsterdam",
        "Europe/Brussels",
        "Europe/Vienna",
        "Europe/Warsaw",
        "Europe/Stockholm",
        "Europe/Oslo",
        "Europe/Copenhagen",
        "Europe/Prague",
        "Europe/Zurich",
        "Europe/Luxembourg",
        "Europe/Ljubljana",
        "Europe/Bratislava",
        "Europe/Budapest"
    )

    private val europeEastern = setOf(
        "Europe/Athens",
        "Europe/Helsinki",
        "Europe/Bucharest",
        "Europe/Sofia",
        "Europe/Riga",
        "Europe/Tallinn",
        "Europe/Vilnius",
        "Europe/Kyiv",
        "Europe/Kiev",
        "Europe/Chisinau"
    )

    fun posixFor(
        timezoneId: String,
        utcOffsetMinutes: Int
    ): String {
        val normalized = timezoneId.trim()

        if (normalized in europeCentral) {
            return "CET-1CEST,M3.5.0,M10.5.0/3"
        }

        if (normalized in europeEastern) {
            return "EET-2EEST,M3.5.0/3,M10.5.0/4"
        }

        return when (normalized) {
            "UTC", "Etc/UTC", "GMT", "Etc/GMT" -> "UTC0"

            "Europe/Istanbul", "Asia/Istanbul" -> "TRT-3"
            "Europe/London", "Europe/Belfast" -> "GMT0BST,M3.5.0/1,M10.5.0"
            "Europe/Dublin" -> "IST-1GMT0,M10.5.0,M3.5.0/1"

            "America/New_York", "US/Eastern" -> "EST5EDT,M3.2.0,M11.1.0"
            "America/Chicago", "US/Central" -> "CST6CDT,M3.2.0,M11.1.0"
            "America/Denver", "US/Mountain" -> "MST7MDT,M3.2.0,M11.1.0"
            "America/Los_Angeles", "US/Pacific" -> "PST8PDT,M3.2.0,M11.1.0"
            "America/Phoenix" -> "MST7"
            "America/Anchorage" -> "AKST9AKDT,M3.2.0,M11.1.0"
            "Pacific/Honolulu" -> "HST10"

            "Asia/Tokyo" -> "JST-9"
            "Asia/Seoul" -> "KST-9"
            "Asia/Shanghai", "Asia/Hong_Kong", "Asia/Singapore" -> "CST-8"
            "Asia/Dubai" -> "GST-4"
            "Asia/Kolkata", "Asia/Calcutta" -> "IST-5:30"
            "Asia/Bangkok" -> "ICT-7"
            "Asia/Jakarta" -> "WIB-7"
            "Asia/Manila" -> "PST-8"

            "Australia/Sydney", "Australia/Melbourne" -> "AEST-10AEDT,M10.1.0,M4.1.0/3"
            "Australia/Brisbane" -> "AEST-10"
            "Australia/Perth" -> "AWST-8"
            "Pacific/Auckland" -> "NZST-12NZDT,M9.5.0,M4.1.0/3"

            else -> fixedOffsetPosix(utcOffsetMinutes)
        }
    }

    private fun fixedOffsetPosix(
        utcOffsetMinutes: Int
    ): String {
        val safeMinutes = utcOffsetMinutes.coerceIn(-14 * 60, 14 * 60)
        val absolute = abs(safeMinutes)
        val hours = absolute / 60
        val minutes = absolute % 60

        val sign = when {
            safeMinutes > 0 -> "-"
            safeMinutes < 0 -> "+"
            else -> ""
        }

        return buildString {
            append("UTC")
            append(sign)
            append(hours)
            if (minutes > 0) {
                append(":")
                append(String.format(Locale.US, "%02d", minutes))
            }
        }
    }
}
