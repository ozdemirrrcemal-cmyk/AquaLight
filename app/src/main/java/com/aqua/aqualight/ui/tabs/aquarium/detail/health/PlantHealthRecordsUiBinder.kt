package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.PlantObservationSnapshot
import com.aqua.aqualight.databinding.FragmentPlantHealthDetailBinding
import com.aqua.aqualight.databinding.ItemPlantObservationBinding

internal fun FragmentPlantHealthDetailBinding.renderPlantRecords(
    state: PlantObservationsState,
    onOpen: (Long) -> Unit
) {
    val ready = state as? PlantObservationsState.Ready
    val records = ready?.records.orEmpty()
    val latest = records.firstOrNull()
    tvLoadError.isVisible = state == PlantObservationsState.Failed
    btnRetry.isVisible = state == PlantObservationsState.Failed
    cardLatestObservation.isVisible = ready != null
    cardLatestObservation.isClickable = latest != null
    cardLatestObservation.isFocusable = latest != null
    cardLatestObservation.setOnClickListener { latest?.let { onOpen(it.id) } }
    tvObservationStatus.isVisible = ready != null
    tvObservationStatus.text = if (ready != null && records.isNotEmpty()) {
        root.context.getString(R.string.plant_health_record_count, records.size)
    } else root.context.getString(R.string.plant_health_status_no_observation)
    tvLatestObservationTitle.text = latest?.let { root.context.plantObservationSigns(it) }
        ?: root.context.getString(R.string.plant_health_no_observation_title)
    tvLatestObservationBody.text = latest?.let { root.context.plantObservationDate(it) }
        ?: root.context.getString(R.string.plant_health_no_observation_body)
    cardNoObservations.isVisible = ready != null && records.isEmpty()
    btnOpenHistory.isEnabled = ready != null && records.isNotEmpty()
    observationListContainer.renderRecordPreview(records.take(PREVIEW_LIMIT), onOpen)
    val notes = records.filter { it.note.isNotBlank() }
    cardNoNotes.isVisible = ready != null && notes.isEmpty()
    noteListContainer.renderRecordPreview(notes.take(PREVIEW_LIMIT), onOpen)
}

private fun LinearLayout.renderRecordPreview(records: List<PlantObservationSnapshot>, onOpen: (Long) -> Unit) {
    removeAllViews()
    records.forEach { record ->
        val item = ItemPlantObservationBinding.inflate(LayoutInflater.from(context), this, false)
        item.tvDate.text = context.plantObservationDate(record)
        item.tvSymptoms.text = context.plantObservationSigns(record)
        item.tvNote.text = record.note
        item.tvNote.isVisible = record.note.isNotBlank()
        item.root.setOnClickListener { onOpen(record.id) }
        addView(item.root)
    }
}

private const val PREVIEW_LIMIT = 5
