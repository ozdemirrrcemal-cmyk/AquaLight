package com.aqua.aqualight.ui.tabs.devices.detail.timer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TimerDeviceRepository {

    enum class OutletRegime(
        val displayName: String
    ) {
        AUTO("Auto"),
        ON("On"),
        OFF("Off");

        companion object {
            fun fromRaw(
                value: String
            ): OutletRegime {
                return when (value.trim().lowercase()) {
                    "auto", "0" -> AUTO
                    "on", "1" -> ON
                    "off", "2" -> OFF
                    else -> OFF
                }
            }
        }
    }

    data class TimerOutletData(
        val index: Int,
        val name: String,
        val gpioPwm: String,
        val regime: OutletRegime,
        val vNow: Float?,
        val dimension: String,
        val ye: Float,
        val rest: Float?
    ) {
        fun isCurrentlyOn(): Boolean {
            if (vNow != null) {
                return vNow >= 0.5f
            }

            return regime == OutletRegime.ON
        }
    }

    data class TimerRuleData(
        val index: Int,
        val enabled: Boolean,
        val name: String,
        val gpioPwm: String,
        val ye: Float,
        val weekDays: List<Boolean>,
        val timeStart: String,
        val intervalOn: String,
        val intervalOff: String,
        val count: Int,
        val status: String
    ) {
        fun isUsable(): Boolean {
            return enabled &&
                gpioPwm.isNotBlank() &&
                gpioPwm != "-" &&
                count > 0
        }

        fun compactScheduleText(): String {
            if (!enabled) {
                return "Disabled"
            }

            if (timeStart.isBlank() || intervalOn.isBlank()) {
                return "No schedule"
            }

            return "$timeStart · ${compactDuration(intervalOn)}"
        }

        private fun compactDuration(
            value: String
        ): String {
            val parts = value.split(":")
                .mapNotNull { part ->
                    part.toIntOrNull()
                }

            if (parts.isEmpty()) {
                return value
            }

            val hours: Int
            val minutes: Int
            val seconds: Int

            when (parts.size) {
                3 -> {
                    hours = parts[0]
                    minutes = parts[1]
                    seconds = parts[2]
                }

                2 -> {
                    hours = 0
                    minutes = parts[0]
                    seconds = parts[1]
                }

                else -> {
                    hours = 0
                    minutes = 0
                    seconds = parts[0]
                }
            }

            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "$minutes min"
                seconds > 0 -> "$seconds sec"
                else -> "0 sec"
            }
        }
    }

    data class TimerDashboardData(
        val ip: String?,
        val outlets: List<TimerOutletData>,
        val timerRules: List<TimerRuleData>
    ) {
        fun ruleForOutlet(
            outlet: TimerOutletData
        ): TimerRuleData? {
            val outletGpio = outlet.gpioPwm.trim()

            if (outletGpio.isBlank() || outletGpio == "-") {
                return null
            }

            return timerRules.firstOrNull { rule ->
                rule.gpioPwm.trim().equals(
                    outletGpio,
                    ignoreCase = true
                )
            }
        }

        fun activeOutletCount(): Int {
            return outlets.count { outlet ->
                outlet.isCurrentlyOn()
            }
        }

        fun nextRule(): TimerRuleData? {
            return timerRules.firstOrNull { rule ->
                rule.isUsable()
            }
        }

        fun nextEventText(): String {
            val nextRule = nextRule() ?: return "--"

            val outlet = outlets.firstOrNull { item ->
                item.gpioPwm.trim().equals(
                    nextRule.gpioPwm.trim(),
                    ignoreCase = true
                )
            }

            val outletName = outlet?.name ?: nextRule.name

            return "${nextRule.timeStart} · $outletName"
        }
    }

    suspend fun fetchTimerDashboardData(
        ipAddress: String
    ): TimerDashboardData = withContext(Dispatchers.IO) {
        val requestJson = JSONObject().apply {
            put(
                "LPWMChanelTimer",
                JSONObject().apply {
                    put("Count", 0)
                    put(
                        "Data",
                        JSONObject().apply {
                            put(
                                "All",
                                JSONObject().apply {
                                    put("Name", 0)
                                    put("GPIO_PWM", 0)
                                    put("Regime", 0)
                                    put("VNow", 0)
                                    put("Dimension", 0)
                                    put("YE", 0)
                                    put("Rest", 0)
                                }
                            )
                        }
                    )
                }
            )

            put(
                "LTimer",
                JSONObject().apply {
                    put("Count", 0)
                    put(
                        "Data",
                        JSONObject().apply {
                            put(
                                "All",
                                JSONObject().apply {
                                    put("Enabled", 0)
                                    put("Name", 0)
                                    put("GPIO_PWM", 0)
                                    put("YE", 0)
                                    put("WDay", 0)
                                    put("TimeStart", 0)
                                    put("IntervalOn", 0)
                                    put("IntervalOff", 0)
                                    put("Count", 0)
                                    put("Status", 0)
                                }
                            )
                        }
                    )
                }
            )
        }

        val encodedJson = URLEncoder.encode(
            requestJson.toString(),
            StandardCharsets.UTF_8.name()
        )

        val url = URL(
            "http://$ipAddress/get?Json=$encodedJson&sRet=0"
        )

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        try {
            val code = connection.responseCode

            if (code !in 200..299) {
                throw IllegalStateException(
                    "Device returned HTTP $code"
                )
            }

            val response = BufferedReader(
                InputStreamReader(connection.inputStream)
            ).use { reader ->
                reader.readText()
            }

            parseTimerDashboardResponse(
                response = response
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTimerDashboardResponse(
        response: String
    ): TimerDashboardData {
        val root = JSONObject(response)

        return TimerDashboardData(
            ip = root.optString("IP")
                .takeIf { value ->
                    value.isNotBlank()
                },
            outlets = parseTimerOutlets(
                root = root
            ),
            timerRules = parseTimerRules(
                root = root
            )
        )
    }

    private fun parseTimerOutlets(
        root: JSONObject
    ): List<TimerOutletData> {
        val data = root.optJSONObject("LPWMChanelTimer")
            ?.optJSONObject("Data")
            ?: return emptyList()

        return data.keys()
            .asSequence()
            .mapNotNull { key ->
                val index = key.toIntOrNull() ?: return@mapNotNull null
                val outletJson = data.optJSONObject(key) ?: return@mapNotNull null

                val rawVNow = outletJson.optDouble(
                    "VNow",
                    Double.NaN
                )

                val rawRest = outletJson.optDouble(
                    "Rest",
                    Double.NaN
                )

                TimerOutletData(
                    index = index,
                    name = outletJson.optString(
                        "Name",
                        "Outlet ${index + 1}"
                    ).ifBlank {
                        "Outlet ${index + 1}"
                    },
                    gpioPwm = outletJson.optString(
                        "GPIO_PWM",
                        "-"
                    ).trim(),
                    regime = OutletRegime.fromRaw(
                        value = outletJson.optString(
                            "Regime",
                            "Off"
                        )
                    ),
                    vNow = if (rawVNow.isNaN() || rawVNow < 0.0) {
                        null
                    } else {
                        rawVNow.toFloat()
                    },
                    dimension = outletJson.optString(
                        "Dimension",
                        ""
                    ).trim(),
                    ye = outletJson.optFloatCompat(
                        name = "YE",
                        defaultValue = -1f
                    ),
                    rest = if (rawRest.isNaN() || rawRest < 0.0) {
                        null
                    } else {
                        rawRest.toFloat()
                    }
                )
            }
            .sortedBy { outlet ->
                outlet.index
            }
            .toList()
    }

    private fun parseTimerRules(
        root: JSONObject
    ): List<TimerRuleData> {
        val data = root.optJSONObject("LTimer")
            ?.optJSONObject("Data")
            ?: return emptyList()

        return data.keys()
            .asSequence()
            .mapNotNull { key ->
                val index = key.toIntOrNull() ?: return@mapNotNull null
                val timerJson = data.optJSONObject(key) ?: return@mapNotNull null

                TimerRuleData(
                    index = index,
                    enabled = timerJson.optBooleanCompat(
                        name = "Enabled"
                    ),
                    name = timerJson.optString(
                        "Name",
                        "Timer ${index + 1}"
                    ).ifBlank {
                        "Timer ${index + 1}"
                    },
                    gpioPwm = timerJson.optString(
                        "GPIO_PWM",
                        "-"
                    ).trim(),
                    ye = timerJson.optFloatCompat(
                        name = "YE",
                        defaultValue = -1f
                    ),
                    weekDays = parseBooleanArray(
                        array = timerJson.optJSONArray("WDay")
                    ),
                    timeStart = timerJson.optString(
                        "TimeStart",
                        ""
                    ).trim(),
                    intervalOn = timerJson.optString(
                        "IntervalOn",
                        ""
                    ).trim(),
                    intervalOff = timerJson.optString(
                        "IntervalOff",
                        ""
                    ).trim(),
                    count = timerJson.optInt(
                        "Count",
                        0
                    ),
                    status = timerJson.optString(
                        "Status",
                        ""
                    ).trim()
                )
            }
            .sortedBy { rule ->
                rule.index
            }
            .toList()
    }

    private fun parseBooleanArray(
        array: JSONArray?
    ): List<Boolean> {
        if (array == null) {
            return emptyList()
        }

        return List(
            size = array.length()
        ) { index ->
            val value = array.opt(index)

            when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value == "1" ||
                    value.equals(
                        "true",
                        ignoreCase = true
                    ) ||
                    value.equals(
                        "on",
                        ignoreCase = true
                    )

                else -> false
            }
        }
    }

    private fun JSONObject.optFloatCompat(
        name: String,
        defaultValue: Float
    ): Float {
        val value = opt(name)

        return when (value) {
            is Number -> value.toFloat()
            is String -> value.replace(
                ",",
                "."
            ).toFloatOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    private fun JSONObject.optBooleanCompat(
        name: String,
        defaultValue: Boolean = false
    ): Boolean {
        val value = opt(name)

        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value == "1" ||
                value.equals(
                    "true",
                    ignoreCase = true
                ) ||
                value.equals(
                    "on",
                    ignoreCase = true
                ) ||
                value.equals(
                    "enabled",
                    ignoreCase = true
                )

            else -> defaultValue
        }
    }
}