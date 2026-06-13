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
import com.aqua.aqualight.databinding.ItemDeviceAddCandidateBinding
import com.aqua.aqualight.databinding.ItemDeviceAddSectionHeaderBinding
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper
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
                val binding = ItemDeviceAddCandidateBinding.inflate(
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
        private val binding: ItemDeviceAddCandidateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: DeviceAddCandidate
        ) {
            binding.ivDeviceIcon.setImageResource(
                DeviceIconMapper.iconFor(item.category)
            )

            binding.ivDeviceIcon.contentDescription = item.displayName

            binding.tvDeviceName.text = item.displayName.ifBlank {
                item.familyName.ifBlank {
                    "Device"
                }
            }

            binding.tvDeviceSerial.text = visibleMeta(
                item = item
            )

            binding.tvCandidateAction.text = item.actionText.ifBlank {
                when (item.source) {
                    DeviceAddSource.LOCAL_NETWORK -> "Add"
                    DeviceAddSource.SETUP_AP -> "Set up"
                }
            }

            binding.rowCandidate.setOnClickListener {
                onCandidateClick(item)
            }
        }

        private fun visibleMeta(
            item: DeviceAddCandidate
        ): String {
            return when (item.source) {
                DeviceAddSource.SETUP_AP -> {
                    val setupSsid = item.setupSsid
                        .orEmpty()
                        .trim()

                    if (setupSsid.isNotBlank()) {
                        "$setupSsid • Setup mode"
                    } else {
                        val setupId = item.setupShortId
                            .orEmpty()
                            .trim()
                            .uppercase(Locale.US)

                        if (setupId.isBlank()) {
                            "Setup mode"
                        } else {
                            "Setup ID: $setupId"
                        }
                    }
                }

                DeviceAddSource.LOCAL_NETWORK -> {
                    val localDevice = item.localDevice
                    val identifier = if (localDevice == null) {
                        "Device code unavailable"
                    } else {
                        DeviceSerialFormatter.buildCommercialIdentifier(
                            setupCode = item.setupCode,
                            serialNumber = localDevice.serialNumber,
                            shortId = localDevice.shortId,
                            deviceUid = localDevice.deviceUid,
                            macAddress = localDevice.macAddress,
                            firmwareSerial = localDevice.firmwareSerial,
                            fallbackNumericId = localDevice.id
                        )
                    }

                    val ip = localDevice
                        ?.ip
                        .orEmpty()
                        .trim()

                    if (ip.isBlank()) {
                        "Device code: $identifier"
                    } else {
                        "Device code: $identifier • $ip"
                    }
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
