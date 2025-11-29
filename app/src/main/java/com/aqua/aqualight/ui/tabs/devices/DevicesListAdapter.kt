package com.aqua.aqualight.ui.tabs.devices

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceCardBinding
import java.util.Locale

data class DeviceCardUi(
    val id: Long,
    val aquaName: String,
    val name: String,
    val isOnline: Boolean
)

class DevicesListAdapter(
    private val onSelectionModeStart: () -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<DeviceCardUi, DevicesListAdapter.DeviceViewHolder>(DiffCallback) {

    private val selectedIds = mutableSetOf<Long>()
    private var selectionMode = false

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun getSelectedIds(): List<Long> = selectedIds.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemDeviceCardBinding.inflate(inflater, parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DeviceCardUi) {
            val ctx = binding.root.context

            // Cihaz adı
            binding.tvDeviceName.text = item.name

            // Icon
            val iconRes = resolveIconForAquaName(item.aquaName)
            binding.ivDeviceIcon.setImageResource(iconRes)

            // Online/offline rengi
            val statusColor = ContextCompat.getColor(
                ctx,
                if (item.isOnline) R.color.dialog_icon_success else R.color.settings_text_secondary
            )
            binding.ivStatus.setColorFilter(statusColor, PorterDuff.Mode.SRC_IN)

            // --- SEÇİLİ GÖRÜNÜMÜ ---
            val isSelected = selectedIds.contains(item.id)
            binding.cardRoot.strokeWidth = if (isSelected) 4 else 0
            binding.cardRoot.strokeColor =
                ContextCompat.getColor(ctx, R.color.aqua_button_blue)

            // KISA TIK — sadece selection mode açıkken toggle
            binding.root.setOnClickListener {
                if (selectionMode) toggleSelection(item.id)
            }

            // UZUN BASMA — selection mode başlatır + toggle yapar
            binding.root.setOnLongClickListener {
                if (!selectionMode) {
                    selectionMode = true
                    onSelectionModeStart()
                }
                toggleSelection(item.id)
                true
            }
        }

        private fun toggleSelection(id: Long) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id)
            } else {
                selectedIds.add(id)
            }
            onSelectionChanged(selectedIds.size)
            notifyItemChanged(bindingAdapterPosition)
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<DeviceCardUi>() {
            override fun areItemsTheSame(a: DeviceCardUi, b: DeviceCardUi) = a.id == b.id
            override fun areContentsTheSame(a: DeviceCardUi, b: DeviceCardUi) = a == b
        }

        fun resolveIconForAquaName(aquaName: String): Int {
            val key = aquaName.lowercase(Locale.ROOT)
            return when {
                key.contains("doser")       -> R.drawable.ic_device_doser
                key.contains("light")       -> R.drawable.ic_device_light
                key.contains("wrgb")        -> R.drawable.ic_device_light
                key.contains("hub")         -> R.drawable.ic_device_wifi_hub
                key.contains("timer")       -> R.drawable.ic_device_timer
                key.contains("temp")        -> R.drawable.ic_device_temperature
                key.contains("co2")         -> R.drawable.ic_device_co2
                key.contains("dr")          -> R.drawable.ic_device_dr_aqua
                else                        -> R.drawable.ic_device_dr_aqua
            }
        }
    }
}