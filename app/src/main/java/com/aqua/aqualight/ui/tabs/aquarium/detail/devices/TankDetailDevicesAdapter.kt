package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemTankDetailDeviceGenericCardBinding
import com.aqua.aqualight.databinding.ItemTankDetailDeviceLightCardBinding
import com.aqua.aqualight.databinding.ItemTankLightChannelRowBinding

class TankDetailDevicesAdapter(
    private val onDeviceClick: (TankAssignedDeviceUi) -> Unit,
    private val onDeviceLongClick: (TankAssignedDeviceUi) -> Unit
) : ListAdapter<TankAssignedDeviceUi, RecyclerView.ViewHolder>(
    DiffCallback
) {

    override fun getItemViewType(
        position: Int
    ): Int {
        return when (getItem(position)) {
            is TankAssignedDeviceUi.Light -> VIEW_TYPE_LIGHT
            is TankAssignedDeviceUi.Generic -> VIEW_TYPE_GENERIC
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater =
        LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_LIGHT -> {
                LightViewHolder(
                    binding = ItemTankDetailDeviceLightCardBinding.inflate(
                        inflater,
                        parent,
                        false
                    ),
                    onDeviceClick = onDeviceClick,
                    onDeviceLongClick = onDeviceLongClick
                )
            } else -> {
                GenericViewHolder(
                    binding = ItemTankDetailDeviceGenericCardBinding.inflate(
                        inflater,
                        parent,
                        false
                    ),
                    onDeviceClick = onDeviceClick,
                    onDeviceLongClick = onDeviceLongClick
                )
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (holder) {
            is LightViewHolder -> {
                holder.bind(
                    item = getItem(position) as TankAssignedDeviceUi.Light
                )
            }

            is GenericViewHolder -> {
                holder.bind(
                    item = getItem(position) as TankAssignedDeviceUi.Generic
                )
            }
        }
    }

    private class LightViewHolder(
        private val binding: ItemTankDetailDeviceLightCardBinding,
        private val onDeviceClick: (TankAssignedDeviceUi) -> Unit,
        private val onDeviceLongClick: (TankAssignedDeviceUi) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: TankAssignedDeviceUi.Light
        ) {
            binding.tvDeviceName.text =
            item.title

            binding.tvDeviceType.text =
            item.subtitle

            binding.ivDeviceIcon.setImageResource(
                item.iconRes
            )

            binding.ivDeviceIcon.imageTintList =
            null

            binding.ivDeviceIcon.clearColorFilter()

            binding.tvConnectionStatus.text =
            if (item.isOnline) {
                "Online"
            } else {
                "Offline"
            }

            binding.tvConnectionStatus.setTextColor(
                if (item.isOnline) {
                    Color.parseColor("#5FD6B4")
                } else {
                    Color.parseColor("#D85C5C")
                }
            )

            binding.tvProgramLabel.text =
            item.modeLabel

            binding.tvProgramName.text =
            item.programName

            binding.tvStartTime.text =
            item.startTimeText

            binding.tvEndTime.text =
            item.endTimeText

            binding.tvOutputPercent.text =
            "${item.outputPercent}%"

            binding.tvOutputPercent.setTextColor(
                item.accentColorInt
            )

            binding.ivRampDown.imageTintList =
            android.content.res.ColorStateList.valueOf(
                item.accentColorInt
            )

            binding.viewTimelineActive.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                item.accentColorInt
            )

            bindTimelineProgress(
                progressPercent = item.timelineProgressPercent
            )

            bindChannels(
                channels = item.channels
            )

            binding.root.setOnClickListener {
                onDeviceClick(item)
            }

            binding.root.setOnLongClickListener {
                onDeviceLongClick(item)
                true
            }
        }

        private fun bindTimelineProgress(
            progressPercent: Int
        ) {
            binding.tvLightTimeline.doOnLayout {
                timeline ->
                val activeWidth =
                timeline.width * progressPercent.coerceIn(
                    0,
                    100
                ) / 100

                val params =
                binding.viewTimelineActive.layoutParams

                params.width =
                activeWidth

                binding.viewTimelineActive.layoutParams =
                params
            }
        }

        private fun bindChannels(
            channels: List<TankLightChannelUi>
        ) {
            binding.channelContainer.removeAllViews()

            channels.forEach {
                channel ->
                val channelBinding =
                ItemTankLightChannelRowBinding.inflate(
                    LayoutInflater.from(binding.root.context),
                    binding.channelContainer,
                    false
                )

                val safePercent =
                channel.currentPercent.coerceIn(
                    0,
                    100
                )

                channelBinding.tvChannelLabel.text =
                channel.label

                channelBinding.tvChannelValue.text =
                "$safePercent%"

                channelBinding.progressChannel.bind(
                    progressPercent = safePercent,
                    fillColor = channelFillColor(
                        key = channel.key
                    ),
                    trackColor = channelTrackColor(
                        key = channel.key
                    )
                )

                binding.channelContainer.addView(
                    channelBinding.root
                )
            }
        }

        private companion object {

            private fun channelFillColor(
                key: TankLightChannelKey
            ): Int {
                return when (key) {
                    TankLightChannelKey.WHITE -> Color.parseColor("#DDE2E8")
                    TankLightChannelKey.RED -> Color.parseColor("#D86E72")
                    TankLightChannelKey.GREEN -> Color.parseColor("#72C37F")
                    TankLightChannelKey.BLUE -> Color.parseColor("#6FA0E0")
                    TankLightChannelKey.INTENSITY -> Color.parseColor("#8EB8FF")
                    TankLightChannelKey.UV -> Color.parseColor("#A37CFF")
                }
            }

            private fun channelTrackColor(
                key: TankLightChannelKey
            ): Int {
                return when (key) {
                    TankLightChannelKey.WHITE -> Color.parseColor("#5E6874")
                    TankLightChannelKey.RED -> Color.parseColor("#673338")
                    TankLightChannelKey.GREEN -> Color.parseColor("#365F43")
                    TankLightChannelKey.BLUE -> Color.parseColor("#334F7C")
                    TankLightChannelKey.INTENSITY -> Color.parseColor("#334E78")
                    TankLightChannelKey.UV -> Color.parseColor("#463675")
                }
            }
        }
    }

    private class GenericViewHolder(
        private val binding: ItemTankDetailDeviceGenericCardBinding,
        private val onDeviceClick: (TankAssignedDeviceUi) -> Unit,
        private val onDeviceLongClick: (TankAssignedDeviceUi) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: TankAssignedDeviceUi.Generic
        ) {
            binding.tvDeviceName.text =
            item.title

            binding.tvDeviceType.text =
            item.subtitle

            binding.ivDeviceIcon.setImageResource(
                item.iconRes
            )

            binding.ivDeviceIcon.imageTintList =
            null

            binding.ivDeviceIcon.clearColorFilter()

            binding.tvConnectionStatus.text =
            if (item.isOnline) {
                "Online"
            } else {
                "Offline"
            }

            binding.tvConnectionStatus.setTextColor(
                if (item.isOnline) {
                    Color.parseColor("#5FD6B4")
                } else {
                    Color.parseColor("#D85C5C")
                }
            )

            binding.root.setOnClickListener {
                onDeviceClick(item)
            }

            binding.root.setOnLongClickListener {
                onDeviceLongClick(item)
                true
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TankAssignedDeviceUi>() {

        override fun areItemsTheSame(
            oldItem: TankAssignedDeviceUi,
            newItem: TankAssignedDeviceUi
        ): Boolean {
            return oldItem.deviceId == newItem.deviceId
        }

        override fun areContentsTheSame(
            oldItem: TankAssignedDeviceUi,
            newItem: TankAssignedDeviceUi
        ): Boolean {
            return oldItem == newItem
        }
    }

    private companion object {

        const val VIEW_TYPE_LIGHT =
        1

        const val VIEW_TYPE_GENERIC =
        2
    }
}