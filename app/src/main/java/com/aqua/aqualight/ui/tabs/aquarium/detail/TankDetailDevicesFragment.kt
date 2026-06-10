package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.databinding.FragmentTankDetailDevicesBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select.TankDeviceSelectFragment
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TankDetailDevicesFragment :
    Fragment(R.layout.fragment_tank_detail_devices) {

    private var _binding: FragmentTankDetailDevicesBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private lateinit var devicesStore: DevicesDataStoreManager

    private var tankId: Long = 0L
    private var tankName: String = ""
    private var latestStatuses: Map<Long, DeviceStatusState> = emptyMap()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentTankDetailDevicesBinding.bind(view)

        devicesStore = DevicesDataStoreManager.create(
            requireContext()
        )

        DevicePresenceMonitor.start(
            context = requireContext()
        )

        setupClickListeners()
        observeTankName()
        observeTankDevices()
    }

    private fun setupClickListeners() {
        binding.btnAddDevice.setOnClickListener {
            openTankDeviceSelectScreen()
        }
    }

    private fun observeTankName() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            tankName = tanks.firstOrNull { tank ->
                tank.id == tankId
            }?.name.orEmpty()
        }
    }

    private fun observeTankDevices() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                combine(
                    devicesStore.devicesForTankFlow(tankId),
                    DevicePresenceMonitor.statuses
                ) { devices, statuses ->
                    devices to statuses
                }.collect { pair ->
                    latestStatuses = pair.second

                    renderDevices(
                        devices = pair.first
                    )
                }
            }
        }
    }

    private fun renderDevices(
        devices: List<DevicesDataStoreManager.DeviceInfoUi>
    ) {
        if (_binding == null) {
            return
        }

        binding.tankDevicesContainer.removeAllViews()

        binding.cardDevicesEmpty.isVisible = devices.isEmpty()
        binding.tankDevicesContainer.isVisible = devices.isNotEmpty()

        devices.forEach { device ->
            binding.tankDevicesContainer.addView(
                createAssignedDeviceCard(
                    device = device
                )
            )
        }
    }

    private fun openTankDeviceSelectScreen() {
        val args = Bundle().apply {
            putLong(
                TankDeviceSelectFragment.ARG_TANK_ID,
                tankId
            )
        }

        findNavController().navigate(
            R.id.action_tankDetailFragment_to_tankDeviceSelectFragment,
            args
        )
    }

    private fun createAssignedDeviceCard(
        device: DevicesDataStoreManager.DeviceInfoUi
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = 18.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#10233A"))
            cardElevation = 0f
            useCompatPadding = false

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            params.bottomMargin = 12.dp()
            layoutParams = params
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                14.dp(),
                12.dp(),
                10.dp(),
                12.dp()
            )
        }

        val iconBox = ImageView(requireContext()).apply {
            setImageResource(
                DeviceIconMapper.iconFor(
                    device.deviceType
                )
            )

            setBackgroundResource(
                R.drawable.bg_material_icon_box
            )

            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = getDeviceTitle(
                device = device
            )

            layoutParams = LinearLayout.LayoutParams(
                46.dp(),
                46.dp()
            )

            setPadding(
                6.dp(),
                6.dp(),
                6.dp(),
                6.dp()
            )
        }

        val textBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            params.marginStart = 12.dp()
            params.marginEnd = 8.dp()
            layoutParams = params
        }

        val titleText = TextView(requireContext()).apply {
            text = getDeviceTitle(
                device = device
            )

            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val typeText = TextView(requireContext()).apply {
            text = getDeviceTypeText(
                device = device
            )

            textSize = 12f
            setTextColor(Color.parseColor("#8FA4BE"))
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            params.topMargin = 6.dp()
            layoutParams = params
        }

        val online = isDeviceOnline(
            device = device
        )

        val statusText = TextView(requireContext()).apply {
            text = if (online) {
                "Online"
            } else {
                "Offline"
            }

            textSize = 12f

            setTextColor(
                if (online) {
                    Color.parseColor("#5FD6B4")
                } else {
                    Color.parseColor("#D85C5C")
                }
            )

            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            params.topMargin = 7.dp()
            layoutParams = params
        }

        textBox.addView(titleText)
        textBox.addView(typeText)
        textBox.addView(statusText)

        val removeButton = ImageView(requireContext()).apply {
            setImageResource(
                R.drawable.ic_close_20
            )

            setColorFilter(
                Color.parseColor("#A7B4C5")
            )

            setBackgroundResource(
                R.drawable.bg_device_remove_icon_circle
            )

            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Remove device"

            layoutParams = LinearLayout.LayoutParams(
                34.dp(),
                34.dp()
            )

            setPadding(
                8.dp(),
                8.dp(),
                8.dp(),
                8.dp()
            )

            setOnClickListener {
                showRemoveDeviceConfirmationDialog(
                    device = device
                )
            }
        }

        row.addView(iconBox)
        row.addView(textBox)
        row.addView(removeButton)

        card.addView(row)

        return card
    }

    private fun showRemoveDeviceConfirmationDialog(
        device: DevicesDataStoreManager.DeviceInfoUi
    ) {
        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = "Remove Device?",
            message = "\"${getDeviceTitle(device)}\" will be removed from this tank. The device will stay saved in Devices.",
            confirmTextResId = R.string.confirm,
            cancelTextResId = R.string.cancel,
            onConfirm = {
                removeDeviceFromCurrentTank(
                    device = device
                )
            }
        )
    }

    private fun removeDeviceFromCurrentTank(
        device: DevicesDataStoreManager.DeviceInfoUi
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            devicesStore.removeDeviceFromTank(
                deviceId = device.id
            )
        }
    }

    private fun getDeviceTitle(
        device: DevicesDataStoreManager.DeviceInfoUi
    ): String {
        val definition = AquaDeviceCatalog.findByType(
            type = device.deviceType
        )

        return definition?.displayName
            ?: device.name.ifBlank {
                device.productModel.ifBlank {
                    "Device"
                }
            }
    }

    private fun getDeviceTypeText(
        device: DevicesDataStoreManager.DeviceInfoUi
    ): String {
        val definition = AquaDeviceCatalog.findByType(
            type = device.deviceType
        )

        return definition?.family?.displayName
            ?: device.productFamily.ifBlank {
                device.aquaName.ifBlank {
                    "Device"
                }
            }
    }

    private fun isDeviceOnline(
        device: DevicesDataStoreManager.DeviceInfoUi
    ): Boolean {
        val statusState = latestStatuses[device.id]

        return statusState?.isOnline == true
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        binding.tankDevicesContainer.removeAllViews()
        _binding = null

        super.onDestroyView()
    }

    companion object {

        private const val ARG_TANK_ID = "tankId"

        fun newInstance(
            tankId: Long
        ): TankDetailDevicesFragment {
            return TankDetailDevicesFragment().apply {
                arguments = Bundle().apply {
                    putLong(
                        ARG_TANK_ID,
                        tankId
                    )
                }
            }
        }
    }
}