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
    val ip: String,
    val serial: String,
    val isOnline: Boolean
)

class DevicesListAdapter(
    private val onSelectionModeStart: () -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val onDeviceClick: (DeviceCardUi) -> Unit   // 🔹 YENİ
) : ListAdapter<DeviceCardUi, DevicesListAdapter.DeviceViewHolder>(DiffCallback) {

    // Seçili kartların ID’leri
    private val selectedIds = mutableSetOf<Long>()

    var isSelectionMode = false
        private set

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
            val isSelected = selectedIds.contains(item.id)

            // Cihaz adı
            binding.tvDeviceName.text =
    if (item.aquaName.isNotBlank())
        item.aquaName
    else
        item.name

            // Icon
            val iconRes = resolveIconForAquaName(item.aquaName)
            binding.ivDeviceIcon.setImageResource(iconRes)

            // Online status rengi
            val statusColorRes = if (item.isOnline)
                R.color.dialog_icon_success
            else
                R.color.settings_text_secondary

            binding.ivStatus.setColorFilter(
                ContextCompat.getColor(ctx, statusColorRes),
                PorterDuff.Mode.SRC_IN
            )

            // SEÇİLİ KART GÖRSELİ
            if (isSelected) {
                binding.root.alpha = 1f
                binding.card.strokeWidth = 4
                binding.card.strokeColor =
                    ContextCompat.getColor(ctx, R.color.dialog_icon_success)
            } else {
                binding.root.alpha = 0.85f
                binding.card.strokeWidth = 0
            }

            // -----------------------
            // TEK TIK
            // -----------------------
            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    // Seçim modundaysa toggle
                    toggleSelection(item)
                } else {
                    // Normal modda → cihaz tıklandı bilgisini dışarı gönder
                    onDeviceClick(item)
                }
            }

            // -----------------------
            // UZUN BAS — seçim başlat + toggle
            // -----------------------
            binding.root.setOnLongClickListener {
                val firstTime = selectedIds.isEmpty()

                toggleSelection(item)

                if (firstTime) {
                    isSelectionMode = true
                    onSelectionModeStart()
                }

                true
            }
        }
    }

    // ---------------------------
    // Yardımcı Fonksiyonlar
    // ---------------------------

    private fun toggleSelection(item: DeviceCardUi) {
        if (selectedIds.contains(item.id))
            selectedIds.remove(item.id)
        else
            selectedIds.add(item.id)

        onSelectionChanged(selectedIds.size)

        notifyItemChanged(currentList.indexOf(item))
    }

    fun getSelectedIds(): Set<Long> = selectedIds.toSet()

    fun exitSelectionMode() {
        selectedIds.clear()
        isSelectionMode = false
        notifyDataSetChanged()
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
                key.contains("light") || key.contains("wrgb") -> R.drawable.ic_device_lights
                key.contains("hub") -> R.drawable.ic_device_wifi_hub
                key.contains("timer") -> R.drawable.ic_device_timer
                key.contains("temp") -> R.drawable.ic_device_temperature
                key.contains("co2") -> R.drawable.ic_device_co2
                key.contains("dr") -> R.drawable.ic_device_dr_aqua
                else -> R.drawable.ic_device_dr_aqua
            }
        }
    }
}