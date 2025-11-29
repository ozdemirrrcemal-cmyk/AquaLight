package com.aqua.aqualight.ui.tabs.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
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
    private val onClick: (DeviceCardUi) -> Unit
) : ListAdapter<DeviceCardUi, DevicesListAdapter.DeviceViewHolder>(DiffCallback) {

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

            // AquaName'den icon seç
            val iconRes = resolveIconForAquaName(item.aquaName)
            binding.ivDeviceIcon.setImageResource(iconRes)

            // Bağlı / değil rengi
            val statusColorRes = if (item.isOnline) {
                R.color.dialog_icon_success      // yeşil
            } else {
                R.color.settings_text_secondary  // gri
            }
            ImageViewCompat.setTint(
                binding.ivStatus,
                ContextCompat.getColor(ctx, statusColorRes)
            )

            // Kart tıklama
            binding.root.setOnClickListener {
                onClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<DeviceCardUi>() {
            override fun areItemsTheSame(oldItem: DeviceCardUi, newItem: DeviceCardUi): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DeviceCardUi, newItem: DeviceCardUi): Boolean =
                oldItem == newItem
        }

        private fun resolveIconForAquaName(aquaName: String): Int {
            val key = aquaName.lowercase(Locale.ROOT)

            return when {
                key.contains("doser") -> R.drawable.ic_device_doser
                key.contains("light") || key.contains("wrgb") ->
                    R.drawable.ic_device_light
                key.contains("hub") -> R.drawable.ic_device_wifi_hub
                key.contains("timer") -> R.drawable.ic_device_timer
                key.contains("temp") || key.contains("temperature") ->
                    R.drawable.ic_device_temperature
                key.contains("co2") -> R.drawable.ic_device_co2
                key.contains("dr") -> R.drawable.ic_device_dr_aqua
                else -> R.drawable.ic_device_dr_aqua  // default
            }
        }
    }
}