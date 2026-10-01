package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisDetailMeasurementBinding

internal class TankHealthAnalysisDetailMetricRenderer(
    private val inflater: LayoutInflater,
    private val container: LinearLayout,
    private val onMetricClick: (TankHealthWaterMetricId) -> Unit
) {

    fun render(record: WaterAnalysisSnapshot) {
        container.removeAllViews()
        record.temperatureCelsius?.let { temperature ->
            addMetric(
                record = record,
                metricId = TankHealthWaterMetricId.Temperature,
                valueText = WaterAnalysisPresentation.temperatureValueText(
                    container.context,
                    temperature
                ),
                metaText = temperatureMeta(record)
            )
        }
        record.measurements.forEach { measurement ->
            addMetric(
                record = record,
                metricId = TankHealthWaterMetricId.Parameter(
                    measurement.parameter.toUiParameterId()
                ),
                valueText = WaterAnalysisPresentation.measurementValueText(
                    container.context,
                    measurement
                ),
                metaText = WaterAnalysisPresentation.measurementMetaText(
                    container.context,
                    measurement
                )
            )
        }
    }

    private fun addMetric(
        record: WaterAnalysisSnapshot,
        metricId: TankHealthWaterMetricId,
        valueText: String,
        metaText: String
    ) {
        val item = ItemTankHealthAnalysisDetailMeasurementBinding.inflate(
            inflater,
            container,
            false
        )
        val summary = TankHealthWaterMetricDetailPresentation.summary(record, metricId)
        item.tvParameterName.setText(
            TankHealthWaterMetricDetailPresentation.metricNameRes(metricId)
        )
        item.tvMeasurementValue.text = valueText
        item.tvCompatibilityStatus.setText(
            TankHealthWaterMetricAssessment.statusRes(summary.status)
        )
        item.tvCompatibilityStatus.setTextColor(
            ContextCompat.getColor(
                container.context,
                TankHealthWaterMetricAssessment.statusColorRes(summary.status)
            )
        )
        item.tvMeasurementMeta.text = metaText
        item.tvMeasurementMeta.isVisible = metaText.isNotBlank()

        val opensDetail = summary.status != TankHealthWaterCompatibilityStatus.SUITABLE
        item.root.isClickable = opensDetail
        item.root.isFocusable = opensDetail
        item.root.setOnClickListener(
            if (opensDetail) {
                View.OnClickListener { onMetricClick(metricId) }
            } else {
                null
            }
        )
        container.addView(item.root)
    }

    private fun temperatureMeta(record: WaterAnalysisSnapshot): String =
        when (record.temperatureSource) {
            WaterTemperatureSource.SENSOR ->
                container.context.getString(R.string.water_measurement_method_sensor)
            WaterTemperatureSource.MANUAL ->
                container.context.getString(R.string.water_measurement_method_manual)
            null -> ""
        }
}
