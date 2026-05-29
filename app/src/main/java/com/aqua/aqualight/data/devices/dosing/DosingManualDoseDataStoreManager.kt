package com.aqua.aqualight.data.devices.dosing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Context.dosingManualDoseDataStore by preferencesDataStore(
    name = "dosing_manual_dose_records"
)

class DosingManualDoseDataStoreManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val recordsKey =
        stringPreferencesKey(
            name = "records_json"
        )

    fun observeTodayManualDoseTotals(
        deviceId: Long
    ): Flow<Map<Int, Float>> {
        return appContext.dosingManualDoseDataStore.data.map { preferences ->
            val todayKey =
                createDateKey(
                    timestampMillis = System.currentTimeMillis()
                )

            parseRecords(
                json = preferences[recordsKey].orEmpty()
            )
                .filter { record ->
                    record.deviceId == deviceId &&
                        record.dateKey == todayKey &&
                        record.channelIndex in 0..3 &&
                        record.doseMl > 0f
                }
                .groupBy { record ->
                    record.channelIndex
                }
                .mapValues { entry ->
                    entry.value.sumOf { record ->
                        record.doseMl.toDouble()
                    }.toFloat()
                }
        }
    }

    suspend fun addManualDoseRecord(
        deviceId: Long,
        channelIndex: Int,
        doseMl: Float
    ) {
        val safeDoseMl =
            doseMl.coerceAtLeast(
                minimumValue = 0f
            )

        if (safeDoseMl <= 0f) {
            return
        }

        val nowMillis =
            System.currentTimeMillis()

        val newRecord =
            ManualDoseRecord(
                id = UUID.randomUUID().toString(),
                deviceId = deviceId,
                channelIndex = channelIndex.coerceIn(
                    minimumValue = 0,
                    maximumValue = 3
                ),
                doseMl = safeDoseMl,
                dateKey = createDateKey(
                    timestampMillis = nowMillis
                ),
                timestampMillis = nowMillis
            )

        appContext.dosingManualDoseDataStore.edit { preferences ->
            val existingRecords =
                parseRecords(
                    json = preferences[recordsKey].orEmpty()
                )

            val keepAfterMillis =
                nowMillis - RECORD_RETENTION_MS

            val nextRecords =
                (existingRecords + newRecord)
                    .filter { record ->
                        record.timestampMillis >= keepAfterMillis
                    }
                    .takeLast(
                        n = MAX_RECORD_COUNT
                    )

            preferences[recordsKey] =
                encodeRecords(
                    records = nextRecords
                )
        }
    }
	
	suspend fun clearChannelManualDoseRecords(
    deviceId: Long,
    channelIndex: Int
) {
    val safeChannelIndex =
        channelIndex.coerceIn(
            minimumValue = 0,
            maximumValue = 3
        )

    appContext.dosingManualDoseDataStore.edit { preferences ->
        val remainingRecords =
            parseRecords(
                json = preferences[recordsKey].orEmpty()
            ).filterNot { record ->
                record.deviceId == deviceId &&
                    record.channelIndex == safeChannelIndex
            }

        preferences[recordsKey] =
            encodeRecords(
                records = remainingRecords
            )
    }
}

    private fun parseRecords(
        json: String
    ): List<ManualDoseRecord> {
        if (json.isBlank()) {
            return emptyList()
        }

        return runCatching {
            val array =
                JSONArray(
                    json
                )

            buildList {
                for (index in 0 until array.length()) {
                    val item =
                        array.optJSONObject(
                            index
                        ) ?: continue

                    val record =
                        ManualDoseRecord(
                            id = item.optString(
                                "id",
                                UUID.randomUUID().toString()
                            ),
                            deviceId = item.optLong(
                                "deviceId",
                                -1L
                            ),
                            channelIndex = item.optInt(
                                "channelIndex",
                                -1
                            ),
                            doseMl = item.optDouble(
                                "doseMl",
                                0.0
                            ).toFloat(),
                            dateKey = item.optString(
                                "dateKey",
                                ""
                            ),
                            timestampMillis = item.optLong(
                                "timestampMillis",
                                0L
                            )
                        )

                    if (
                        record.deviceId > 0L &&
                        record.channelIndex in 0..3 &&
                        record.doseMl > 0f &&
                        record.dateKey.isNotBlank() &&
                        record.timestampMillis > 0L
                    ) {
                        add(
                            record
                        )
                    }
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    private fun encodeRecords(
        records: List<ManualDoseRecord>
    ): String {
        val array =
            JSONArray()

        records.forEach { record ->
            val item =
                JSONObject().apply {
                    put(
                        "id",
                        record.id
                    )

                    put(
                        "deviceId",
                        record.deviceId
                    )

                    put(
                        "channelIndex",
                        record.channelIndex
                    )

                    put(
                        "doseMl",
                        record.doseMl.toDouble()
                    )

                    put(
                        "dateKey",
                        record.dateKey
                    )

                    put(
                        "timestampMillis",
                        record.timestampMillis
                    )
                }

            array.put(
                item
            )
        }

        return array.toString()
    }

    private fun createDateKey(
        timestampMillis: Long
    ): String {
        return SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(
            Date(timestampMillis)
        )
    }

    private data class ManualDoseRecord(
        val id: String,
        val deviceId: Long,
        val channelIndex: Int,
        val doseMl: Float,
        val dateKey: String,
        val timestampMillis: Long
    )

    private companion object {
        private const val MAX_RECORD_COUNT = 300

        private const val RECORD_RETENTION_MS =
            60L * 24L * 60L * 60L * 1000L
    }
}