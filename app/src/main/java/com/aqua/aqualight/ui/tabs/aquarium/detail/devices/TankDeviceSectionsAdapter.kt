package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemTankDeviceSectionsHeaderBinding

/**
 * Scrollable tank sections followed by the existing virtualized device cards.
 * This adapter does not own device control or group persistence.
 */
internal class TankDeviceSectionsAdapter(
    private val onAddDevice: () -> Unit,
    private val onCreateControlGroup: () -> Unit
) : RecyclerView.Adapter<TankDeviceSectionsAdapter.HeaderViewHolder>() {

    private var canAddDevice = false
    private var showEmptyState = false

    fun render(canAddDevice: Boolean, showEmptyState: Boolean) {
        if (this.canAddDevice == canAddDevice && this.showEmptyState == showEmptyState) {
            return
        }
        this.canAddDevice = canAddDevice
        this.showEmptyState = showEmptyState
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding = ItemTankDeviceSectionsHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        binding.btnAddDevice.setOnClickListener {
            if (canAddDevice) onAddDevice()
        }
        binding.btnCreateControlGroup.setOnClickListener {
            onCreateControlGroup()
        }
        return HeaderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.binding.btnAddDevice.isEnabled = canAddDevice
        holder.binding.cardDevicesEmpty.isVisible = showEmptyState
    }

    override fun getItemCount(): Int = 1

    internal class HeaderViewHolder(
        val binding: ItemTankDeviceSectionsHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root)
}
