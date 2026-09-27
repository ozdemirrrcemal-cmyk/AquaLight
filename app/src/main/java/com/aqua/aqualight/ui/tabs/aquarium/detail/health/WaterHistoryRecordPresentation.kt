package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.i18n.LocaleFormatter

internal object WaterHistoryRecordPresentation {
    fun from(context: Context, snapshot: WaterAnalysisSnapshot): TankHealthAnalysisHistoryRecord {
        val metrics = WaterAnalysisHistoryPreview.from(snapshot).map { preview ->
            when (preview) {
                is WaterAnalysisHistoryPreviewValue.Measurement -> {
                    val measurement = preview.source
                    val name = context.getString(
                        WaterAnalysisPresentation.parameterNameRes(measurement.parameter)
                    )
                    val symbol = WaterAnalysisPresentation.measurementSymbolRes(measurement)
                        ?.let { symbolRes -> context.getString(symbolRes) }
                    TankHealthHistoryMetric(
                        labelText = symbol?.let { "$name ($it)" } ?: name,
                        valueText = WaterAnalysisPresentation.measurementValueText(
                            context,
                            measurement
                        )
                    )
                }

                is WaterAnalysisHistoryPreviewValue.Temperature -> TankHealthHistoryMetric(
                    labelText = context.getString(R.string.tank_health_metric_temperature),
                    valueText = WaterAnalysisPresentation.temperatureValueText(
                        context,
                        preview.celsius
                    )
                )
            }
        }
        return TankHealthAnalysisHistoryRecord(
            analysisId = snapshot.id,
            dateText = LocaleFormatter.formatDate(context, snapshot.measuredAtMillis),
            timeText = LocaleFormatter.formatTime(context, snapshot.measuredAtMillis),
            metrics = metrics
        )
    }

}
