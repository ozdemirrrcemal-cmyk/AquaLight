package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import android.text.format.DateFormat
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.PlantObservationSnapshot
import java.util.Date

internal fun Context.plantObservationDate(record: PlantObservationSnapshot): String {
    val date = Date(record.createdAtMillis)
    return getString(
        R.string.plant_health_record_date,
        DateFormat.getDateFormat(this).format(date), DateFormat.getTimeFormat(this).format(date)
    )
}

internal fun Context.plantObservationSigns(record: PlantObservationSnapshot): String =
    record.symptomKeys.joinToString(getString(R.string.plant_health_sign_separator)) { key ->
        getString(PlantSymptomOptions.items.first { it.key == key }.labelRes)
    }
