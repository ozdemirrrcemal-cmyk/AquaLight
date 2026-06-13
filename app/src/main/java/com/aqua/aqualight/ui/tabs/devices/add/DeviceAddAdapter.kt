package com.aqua.aqualight.ui.tabs.devices.add

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.DeviceSerialFormatter
import com.aqua.aqualight.data.devices.add.DeviceAddCandidate
import com.aqua.aqualight.data.devices.add.DeviceAddSource
import com.aqua.aqualight.databinding.ItemDeviceAddSectionHeaderBinding
import com.aqua.aqualight.databinding.ItemDeviceCompactCardBinding
import com.aqua.aqualight.ui.common.devicecard.DeviceCardIconMapper
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardBinder
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import java.util.Locale

class DeviceAddAdapter(
    private val onCandidateClick: (DeviceAddCandidate) -> Unit
) : ListAdapter<DeviceAddAdapter.DeviceAddListItem, RecyclerView.ViewHolder>(DiffCallback) {

    fun submitCandidates(
        candidates: List<DeviceAddCandidate>
    ) {
        val localNetworkCandidates = candidates
            .filter { candidate ->
                candidate.isLocalNetworkCandidate
            }
            .sortedWith(
                compareBy<DeviceAddCandidate> { candidate ->
                    candidate.displayName.lowercase(Locale.US)
                }.thenBy { candidate ->
                    candidate.key
                }
            )

        val setupCandidates = candidates
            .filter { candidate ->
                candidate.isSetupCandidate
            }
            .sortedWith(
                compareBy<DeviceAddCandidate> { candidate ->
                    candidate.displayName.lowercase(Locale.US)
                }.thenBy { candidate ->
                    candidate.key
                }
            )

        submitList(
            buildList {
                if (localNetworkCandidates.isNotEmpty()) {
                    add(
                        DeviceAddListItem.SectionHeader(
                            stableKey = "section:on_your_network",
                            titleRes = R.string.device_add_section_on_network_title,
                            messageRes = R.string.device_add_section_on_network_message
                        )
                    )

                    localNetworkCandidates.forEach { candidate ->
                        add(
                            DeviceAddListItem.Candidate(
                                candidate = candidate
                            )
                        )
                    }
                }

                if (setupCandidates.isNotEmpty()) {
                    add(
                        DeviceAddListItem.SectionHeader(
                            stableKey = "section:setup_mode",
                            titleRes = R.string.device_add_section_setup_title,
                            messageRes = R.string.device_add_section_setup_message
                        )
                    )

                    setupCandidates.forEach { candidate ->
                        add(
                            DeviceAddListItem.Candidate(
                                candidate = candidate
                            )
                        )
                    }
                }
            }
        )
    }

    override fun getItemViewType(
        position: Int
    ): Int {
        return when (getItem(position)) {
            is DeviceAddListItem.SectionHeader -> VIEW_TYPE_HEADER
            is DeviceAddListItem.Candidate -> VIEW_TYPE_CANDIDATE
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(
            parent.context
        )

        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemDeviceAddSectionHeaderBinding.inflate(
                    inflater,
                    parent,
                    false
                )

                SectionHeaderViewHolder(
                    binding = binding
                )
            }

            else -> {
                val binding = ItemDeviceCompactCardBinding.inflate(
                    inflater,
                    parent,
                    false
                )

                DeviceAddViewHolder(
                    binding = binding
                )
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (val item = getItem(position)) {
            is DeviceAddListItem.SectionHeader -> {
                (holder as SectionHeaderViewHolder).bind(
                    item = item
                )
            }

            is DeviceAddListItem.Candidate -> {
                (holder as DeviceAddViewHolder).bind(
                    item = item.candidate
                )
            }
        }
    }

    inner class SectionHeaderViewHolder(
        private val binding: ItemDeviceAddSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: DeviceAddListItem.SectionHeader
        ) {
            binding.tvSectionTitle.setText(
                item.titleRes
            )

            binding.tvSectionMessage.setText(
                item.messageRes
            )
        }
    }

    inner class DeviceAddViewHolder(
        private val binding: ItemDeviceCompactCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: DeviceAddCandidate
        ) {
            DeviceCompactCardBinder.bind(
                binding = binding,
                item = item.toCompactCard()
            )

            binding.root.setOnClickListener {
                onCandidateClick(item)
            }

            binding.tvCardAction.setOnClickListener {
                onCandidateClick(item)
            }
        }

        private fun DeviceAddCandidate.toCompactCard(): DeviceCompactCardUi {
            return DeviceCompactCardUi(
                deviceId = localDevice?.id ?: 0L,
                displayName = displayName.ifBlank {
                    familyName.ifBlank {
                        "Device"
                    }
                },
                serialText = commercialSerial(),
                iconRes = DeviceCardIconMapper.iconFor(
                    category = category
                ),
                isOnline = source == DeviceAddSource.LOCAL_NETWORK,
                showConnectionStatus = false,
                actionText = actionText.ifBlank {
                    when (source) {
                        DeviceAddSource.LOCAL_NETWORK -> "Add"
                        DeviceAddSource.SETUP_AP -> "Set up"
                    }
                },
                showAction = true
            )
        }

        private fun DeviceAddCandidate.commercialSerial(): String {
            return when (source) {
                DeviceAddSource.SETUP_AP -> {
                    DeviceSerialFormatter.buildCommercialIdentifier(
                        setupCode = setupCode,
                        shortId = setupShortId
                    )
                }

                DeviceAddSource.LOCAL_NETWORK -> {
                    val device = localDevice

                    DeviceSerialFormatter.buildCommercialIdentifier(
                        setupCode = setupCode,
                        serialNumber = device?.serialNumber,
                        shortId = device?.shortId,
                        deviceUid = device?.deviceUid,
                        macAddress = device?.macAddress,
                        firmwareSerial = device?.firmwareSerial,
                        fallbackNumericId = device?.id
                    )
                }
            }
        }
    }

    sealed class DeviceAddListItem {
        abstract val stableKey: String

        data class SectionHeader(
            override val stableKey: String,
            val titleRes: Int,
            val messageRes: Int
        ) : DeviceAddListItem()

        data class Candidate(
            val candidate: DeviceAddCandidate
        ) : DeviceAddListItem() {
            override val stableKey: String = "candidate:${candidate.key}"
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 1
        const val VIEW_TYPE_CANDIDATE = 2

        val DiffCallback = object : DiffUtil.ItemCallback<DeviceAddListItem>() {
            override fun areItemsTheSame(
                oldItem: DeviceAddListItem,
                newItem: DeviceAddListItem
            ): Boolean {
                return oldItem.stableKey == newItem.stableKey
            }

            override fun areContentsTheSame(
                oldItem: DeviceAddListItem,
                newItem: DeviceAddListItem
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
