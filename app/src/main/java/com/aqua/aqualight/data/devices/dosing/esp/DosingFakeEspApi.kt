package com.aqua.aqualight.data.devices.dosing.esp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object DosingFakeEspApi {

    private const val TAG = "DOSING_FAKE_ESP"

    private var postCount: Int =
        0

    private val state: JSONObject =
        JSONObject(
            """
            {
              "IP": "FAKE_ESP",
              "Eeprom": {
                "CheckI2C": "Found"
              },
              "LPWMChanelTimer": {
                "Count": 4,
                "Data": {
                  "0": {
                    "Name": "Potassium",
                    "Regime": "Auto",
                    "Color": 16711680,
                    "GPIO_PWM": "G16|4|16",
                    "YE": 1000,
                    "Dimension": "ml",
                    "Rest": 500,
                    "VNow": 0,
                    "VMin": 0,
                    "VMax": 1,
                    "Invert": 0
                  },
                  "1": {
                    "Name": "Nitrogen",
                    "Regime": "Auto",
                    "Color": 65280,
                    "GPIO_PWM": "G17|5|16",
                    "YE": 900,
                    "Dimension": "ml",
                    "Rest": 500,
                    "VNow": 0,
                    "VMin": 0,
                    "VMax": 1,
                    "Invert": 0
                  },
                  "2": {
                    "Name": "Iron",
                    "Regime": "Auto",
                    "Color": 255,
                    "GPIO_PWM": "G18|6|16",
                    "YE": 850,
                    "Dimension": "ml",
                    "Rest": 500,
                    "VNow": 0,
                    "VMin": 0,
                    "VMax": 1,
                    "Invert": 0
                  },
                  "3": {
                    "Name": "Micro",
                    "Regime": "Auto",
                    "Color": 16776960,
                    "GPIO_PWM": "G19|7|16",
                    "YE": 800,
                    "Dimension": "ml",
                    "Rest": 500,
                    "VNow": 0,
                    "VMin": 0,
                    "VMax": 1,
                    "Invert": 0
                  }
                }
              },
              "LTimer": {
                "Count": 0,
                "Data": {}
              }
            }
            """.trimIndent()
        )

    suspend fun getJson(
        payload: JSONObject
    ): JSONObject {
        return withContext(
            Dispatchers.IO
        ) {
            Log.d(
                TAG,
                "FAKE GET PAYLOAD=${payload.toString(2)}"
            )

            val response =
                createResponse()

            Log.d(
                TAG,
                "FAKE GET RESPONSE=${response.toString(2)}"
            )

            response
        }
    }

    suspend fun postJson(
        payload: JSONObject
    ): JSONObject {
        return withContext(
            Dispatchers.IO
        ) {
            postCount++

            Log.d(
                TAG,
                "FAKE POST PAYLOAD=${payload.toString(2)}"
            )

            applyPayload(
                payload = payload
            )

            val response =
                createResponse()

            Log.d(
                TAG,
                "FAKE CURRENT LTIMER=${
                    state.optJSONObject("LTimer")?.toString(2)
                }"
            )

            response
        }
    }

    private fun applyPayload(
        payload: JSONObject
    ) {
        applyLTimer(
            payload = payload
        )

        applyLpwmChannelTimer(
            payload = payload
        )

        val main =
            payload.optJSONObject(
                "Main"
            )

        if (main != null) {
            Log.d(
                TAG,
                "FAKE MAIN=${main.toString(2)}"
            )
        }
    }

    private fun applyLTimer(
        payload: JSONObject
    ) {
        val incomingLTimer =
            payload.optJSONObject(
                "LTimer"
            ) ?: return

        val incomingData =
            incomingLTimer.optJSONObject(
                "Data"
            ) ?: return

        val stateLTimer =
            state.optJSONObject(
                "LTimer"
            ) ?: JSONObject().also { json ->
                state.put(
                    "LTimer",
                    json
                )
            }

        val stateData =
            stateLTimer.optJSONObject(
                "Data"
            ) ?: JSONObject().also { json ->
                stateLTimer.put(
                    "Data",
                    json
                )
            }

        if (incomingLTimer.has("Count")) {
            val newData =
                JSONObject()

            val keys =
                incomingData.keys()

            while (keys.hasNext()) {
                val key =
                    keys.next()

                val item =
                    incomingData.optJSONObject(
                        key
                    ) ?: continue

                newData.put(
                    key,
                    item
                )
            }

            stateLTimer.put(
                "Count",
                incomingLTimer.optInt(
                    "Count",
                    newData.length()
                )
            )

            stateLTimer.put(
                "Data",
                newData
            )

            return
        }

        val keys =
            incomingData.keys()

        while (keys.hasNext()) {
            val key =
                keys.next()

            val patch =
                incomingData.optJSONObject(
                    key
                ) ?: continue

            val current =
                stateData.optJSONObject(
                    key
                ) ?: JSONObject().also { json ->
                    stateData.put(
                        key,
                        json
                    )
                }

            mergeObject(
                target = current,
                patch = patch
            )
        }

        stateLTimer.put(
            "Count",
            calculateTimerCount(
                data = stateData
            )
        )
    }

    private fun applyLpwmChannelTimer(
        payload: JSONObject
    ) {
        val incomingLpwm =
            payload.optJSONObject(
                "LPWMChanelTimer"
            ) ?: return

        val incomingData =
            incomingLpwm.optJSONObject(
                "Data"
            ) ?: return

        val stateLpwm =
            state.optJSONObject(
                "LPWMChanelTimer"
            ) ?: JSONObject().also { json ->
                state.put(
                    "LPWMChanelTimer",
                    json
                )
            }

        val stateData =
            stateLpwm.optJSONObject(
                "Data"
            ) ?: JSONObject().also { json ->
                stateLpwm.put(
                    "Data",
                    json
                )
            }

        val keys =
            incomingData.keys()

        while (keys.hasNext()) {
            val key =
                keys.next()

            val patch =
                incomingData.optJSONObject(
                    key
                ) ?: continue

            val current =
                stateData.optJSONObject(
                    key
                ) ?: JSONObject().also { json ->
                    stateData.put(
                        key,
                        json
                    )
                }

            mergeObject(
                target = current,
                patch = patch
            )
        }
    }

    private fun mergeObject(
        target: JSONObject,
        patch: JSONObject
    ) {
        val keys =
            patch.keys()

        while (keys.hasNext()) {
            val key =
                keys.next()

            val value =
                patch.opt(
                    key
                )

            if (
                value is JSONObject &&
                target.opt(
                    key
                ) is JSONObject
            ) {
                mergeObject(
                    target = target.getJSONObject(
                        key
                    ),
                    patch = value
                )
            } else {
                target.put(
                    key,
                    value
                )
            }
        }
    }

    private fun calculateTimerCount(
        data: JSONObject
    ): Int {
        var maxIndex =
            -1

        val keys =
            data.keys()

        while (keys.hasNext()) {
            val key =
                keys.next()

            val index =
                key.toIntOrNull() ?: continue

            if (index > maxIndex) {
                maxIndex =
                    index
            }
        }

        return maxIndex + 1
    }

    private fun createResponse(): JSONObject {
        val response =
            JSONObject(
                state.toString()
            )

        response.put(
            "sRet",
            JSONObject().apply {
                put(
                    "iPostCount",
                    postCount
                )
            }
        )

        return response
    }
}