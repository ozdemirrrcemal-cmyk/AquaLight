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
    private val onDeviceClick: (DeviceCardUi) -> Unit
) : ListAdapter<DeviceCardUi, DevicesListAdapter.DeviceViewHolder>(DiffCallback) {

    // Seçili cihaz ID listesi
    private val selectedIds = mutableSetOf<Long>()

    var isSelectionMode = false
        private set

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DeviceViewHolder {

        val inflater = LayoutInflater.from(parent.context)

        val binding = ItemDeviceCardBinding.inflate(
            inflater,
            parent,
            false
        )

        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: DeviceViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DeviceCardUi) {

            val ctx = binding.root.context
            val isSelected = selectedIds.contains(item.id)

            // -------------------------------------------------
            // DEVICE NAME
            // -------------------------------------------------

            binding.tvDeviceName.text =
                if (item.aquaName.isNotBlank()) {
                    item.aquaName
                } else {
                    item.name
                }

            // -------------------------------------------------
            // DEVICE ICON
            // -------------------------------------------------

            val iconRes = resolveIconForAquaName(item.aquaName)

            binding.ivDeviceIcon.setImageResource(iconRes)

            // -------------------------------------------------
            // ONLINE STATUS
            // -------------------------------------------------

            val statusColorRes =
                if (item.isOnline) {
                    R.color.dialog_icon_success
                } else {
                    R.color.settings_text_secondary
                }

            binding.ivStatus.setColorFilter(
                ContextCompat.getColor(ctx, statusColorRes),
                PorterDuff.Mode.SRC_IN
            )

            // -------------------------------------------------
            // SELECTION UI
            // -------------------------------------------------

            if (isSelected) {

                binding.root.alpha = 1f

                binding.card.strokeWidth = 4

                binding.card.strokeColor =
                    ContextCompat.getColor(
                        ctx,
                        R.color.dialog_icon_success
                    )

            } else {

                binding.root.alpha = 0.88f

                binding.card.strokeWidth = 0

                binding.card.strokeColor =
                    ContextCompat.getColor(
                        ctx,
                        android.R.color.transparent
                    )
            }

            // -------------------------------------------------
            // CLICK
            // -------------------------------------------------

            binding.root.setOnClickListener {

                if (isSelectionMode) {
                    toggleSelection(item)
                } else {
                    onDeviceClick(item)
                }
            }

            // -------------------------------------------------
            // LONG CLICK
            // -------------------------------------------------

            binding.root.setOnLongClickListener {

                val firstSelection = selectedIds.isEmpty()

                toggleSelection(item)

                if (firstSelection) {
                    isSelectionMode = true
                    onSelectionModeStart()
                }

                true
            }
        }
    }

    // -------------------------------------------------
    // SELECTION
    // -------------------------------------------------

    private fun toggleSelection(item: DeviceCardUi) {

        if (selectedIds.contains(item.id)) {
            selectedIds.remove(item.id)
        } else {
            selectedIds.add(item.id)
        }

        onSelectionChanged(selectedIds.size)

        notifyDataSetChanged()
    }

    fun getSelectedIds(): Set<Long> {
        return selectedIds.toSet()
    }

    fun exitSelectionMode() {

        selectedIds.clear()

        isSelectionMode = false

        notifyDataSetChanged()
    }

    companion object {

        private val DiffCallback =
            object : DiffUtil.ItemCallback<DeviceCardUi>() {

                override fun areItemsTheSame(
                    oldItem: DeviceCardUi,
                    newItem: DeviceCardUi
                ): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(
                    oldItem: DeviceCardUi,
                    newItem: DeviceCardUi
                ): Boolean {
                    return oldItem == newItem
                }
            }

        // -------------------------------------------------
        // ICON RESOLVER
        // -------------------------------------------------

        private fun resolveIconForAquaName(
            aquaName: String
        ): Int {

            val key = aquaName
                .trim()
                .lowercase(Locale.ROOT)

            return when {

                // -------------------------------------------------
                // DOSER / DOSING
                // -------------------------------------------------

                key.contains("doser") ||
                key.contains("dosing") ||
                key.contains("dose") ||
                key.contains("pump") ||
                key.contains("liquid") ->
                    R.drawable.ic_device_doser

                // -------------------------------------------------
                // WRGB / LIGHT
                // -------------------------------------------------

                key.contains("light") ||
                key.contains("wrgb") ||
                key.contains("rgb") ||
                key.contains("led") ||
                key.contains("lamp") ||
                key.contains("shade") ->
                    R.drawable.ic_device_light

                // -------------------------------------------------
                // WIFI HUB / CONTROLLER
                // -------------------------------------------------

                key.contains("hub") ||
                key.contains("gateway") ||
                key.contains("wifi") ||
                key.contains("bridge") ||
                key.contains("controller") ->
                    R.drawable.ic_device_wifi_hub

                // -------------------------------------------------
                // TIMER / SMART PLUG
                // -------------------------------------------------

                key.contains("timer") ||
                key.contains("socket") ||
                key.contains("plug") ||
                key.contains("smartplug") ||
                key.contains("switch") ->
                    R.drawable.ic_device_timer

                // -------------------------------------------------
                // TEMPERATURE / COOLING
                // -------------------------------------------------

                key.contains("temp") ||
                key.contains("temperature") ||
                key.contains("fan") ||
                key.contains("cooler") ||
                key.contains("heater") ->
                    R.drawable.ic_device_temperature

                // -------------------------------------------------
                // CO2
                // -------------------------------------------------

                key.contains("co2") ||
                key.contains("regulator") ||
                key.contains("solenoid") ->
                    R.drawable.ic_device_co2

                // -------------------------------------------------
                // MAIN DEVICE / AQUASTER
                // -------------------------------------------------

                key.contains("aqua") ||
                key.contains("aquaster") ||
                key.contains("master") ||
                key.contains("main") ->
                    R.drawable.ic_device_aqua_ster

                // -------------------------------------------------
                // DEFAULT
                // -------------------------------------------------

                else ->
                    R.drawable.ic_device_aqua_ster
            }
        }
    }
}