package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.application.aquarium.health.PlantObservationSnapshot
import com.aqua.aqualight.databinding.ItemPlantObservationBinding

internal class PlantObservationAdapter(private val onOpen: (Long) -> Unit) :
    ListAdapter<PlantObservationSnapshot, PlantObservationAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemPlantObservationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemPlantObservationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(record: PlantObservationSnapshot) {
            binding.tvDate.text = binding.root.context.plantObservationDate(record)
            binding.tvSymptoms.text = binding.root.context.plantObservationSigns(record)
            binding.tvNote.text = record.note
            binding.tvNote.isVisible = record.note.isNotBlank()
            binding.root.setOnClickListener { onOpen(record.id) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<PlantObservationSnapshot>() {
        override fun areItemsTheSame(old: PlantObservationSnapshot, new: PlantObservationSnapshot) = old.id == new.id
        override fun areContentsTheSame(old: PlantObservationSnapshot, new: PlantObservationSnapshot) = old == new
    }
}
