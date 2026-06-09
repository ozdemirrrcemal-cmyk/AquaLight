package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.LightChannelSemantic
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.databinding.FragmentTankDetailDevicesBinding
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankAssignedDeviceUi
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesAdapter
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankLightChannelKey
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankLightChannelUi
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select.TankDeviceSelectFragment
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.interpolator.LightCurveInterpolator
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramTimeMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper
import java.util.Calendar
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TankDetailDevicesFragment :
    Fragment(R.layout.fragment_tank_detail_devices) {

    private var _binding: FragmentTankDetailDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var devicesStore: DevicesDataStoreManager
    private lateinit var lightProgramsStore: LightProgramsDataStoreManager
    private lateinit var adapter: TankDetailDevicesAdapter

    private var tankId: Long =
        0L

    private var latestDevices: List<DevicesDataStoreManager.DeviceInfoUi> =
        emptyList()

    private var latestStatuses: Map<Long, DeviceStatusState> =
        emptyMap()

    private var latestPrograms: List<SavedLightProgram> =
        emptyList()

    private val latestLightStates =
        mutableMapOf<Long, LightDeviceLiveState>()

    private val lightLiveJobs =
        mutableMapOf<Long, Job>()

    private val liveRefreshOwnerKey =
        "TankDetailDevicesFragment_${System.identityHashCode(this)}"

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        tankId =
            requireArguments().getLong(
                ARG_TANK_ID
            )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentTankDetailDevicesBinding.bind(view)

        devicesStore =
            DevicesDataStoreManager.create(
                requireContext()
            )

        lightProgramsStore =
            LightProgramsDataStoreManager(
                requireContext()
            )

        DevicePresenceMonitor.start(
            context = requireContext()
        )

        setupRecyclerView()
        setupClickListeners()
        observeTankDevices()
    }

    private fun setupRecyclerView() {
        adapter =
            TankDetailDevicesAdapter(
                onDeviceClick = { device ->
                    handleDeviceClick(
                        device = device
                    )
                },
                onDeviceLongClick = { device ->
                    handleDeviceLongClick(
                        device = device
                    )
                }
            )

        binding.rvTankDevices.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        binding.rvTankDevices.adapter =
            adapter
    }

    private fun setupClickListeners() {
        binding.btnAddDevice.setOnClickListener {
            openTankDeviceSelectScreen()
        }
    }

    private fun observeTankDevices() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                combine(
                    devicesStore.devicesForTankFlow(
                        tankId
                    ),
                    DevicePresenceMonitor.statuses,
                    lightProgramsStore.programsFlow
                ) { devices, statuses, programs ->
                    Triple(
                        devices,
                        statuses,
                        programs
                    )
                }.collect { result ->

                    latestDevices =
                        result.first

                    latestStatuses =
                        result.second

                    latestPrograms =
                        result.third

                    updateLightLiveObservers(
                        devices = latestDevices
                    )

                    renderDevices()
                }
            }
        }
    }

    private fun updateLightLiveObservers(
        devices: List<DevicesDataStoreManager.DeviceInfoUi>
    ) {
        val lightDeviceIds =
            devices
                .filter { device ->
                    device.isLightDevice()
                }
                .map { device ->
                    device.id
                }
                .toSet()

        val removedIds =
            lightLiveJobs.keys - lightDeviceIds

        removedIds.forEach { deviceId ->
            lightLiveJobs.remove(
                deviceId
            )?.cancel()

            latestLightStates.remove(
                deviceId
            )

            LightDeviceLiveRefreshManager.stop(
                deviceId = deviceId,
                ownerKey = liveRefreshOwnerKey
            )
        }

        lightDeviceIds.forEach { deviceId ->
            if (lightLiveJobs.containsKey(deviceId)) {
                return@forEach
            }

            LightDeviceLiveRefreshManager.start(
                context = requireContext(),
                deviceId = deviceId,
                ownerKey = liveRefreshOwnerKey
            )

            LightDeviceLiveRefreshManager.refreshNow(
                context = requireContext(),
                deviceId = deviceId
            )

            lightLiveJobs[deviceId] =
                viewLifecycleOwner.lifecycleScope.launch {
                    LightDeviceLiveRefreshManager.observe(
                        deviceId = deviceId
                    ).collect { liveState ->
                        latestLightStates[deviceId] =
                            liveState

                        renderDevices()
                    }
                }
        }
    }

    private fun renderDevices() {
        if (_binding == null) {
            return
        }

        val now =
            System.currentTimeMillis()

        val items =
            latestDevices.map { device ->
                device.toAssignedDeviceUi(
                    statuses = latestStatuses,
                    now = now
                )
            }

        adapter.submitList(
            items
        )

        binding.cardDevicesEmpty.isVisible =
            items.isEmpty()

        binding.rvTankDevices.isVisible =
            items.isNotEmpty()
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.toAssignedDeviceUi(
        statuses: Map<Long, DeviceStatusState>,
        now: Long
    ): TankAssignedDeviceUi {
        val title =
            getDeviceTitle(
                device = this
            )

        val subtitle =
            getDeviceTypeText(
                device = this
            )

        val online =
            isDeviceOnline(
                device = this,
                statuses = statuses,
                now = now
            )

        val iconRes =
            DeviceIconMapper.iconFor(
                deviceType
            )

        return if (isLightDevice()) {
            toLightDeviceUi(
                title = title,
                subtitle = subtitle,
                iconRes = iconRes,
                online = online
            )
        } else {
            TankAssignedDeviceUi.Generic(
                deviceId = id,
                title = title,
                subtitle = subtitle,
                iconRes = iconRes,
                isOnline = online
            )
        }
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.toLightDeviceUi(
        title: String,
        subtitle: String,
        iconRes: Int,
        online: Boolean
    ): TankAssignedDeviceUi.Light {
        val liveState =
            latestLightStates[id] ?: LightDeviceLiveState.initial(
                deviceId = id
            )

        val activePrograms =
            latestPrograms
                .filter { program ->
                    program.deviceId == id && program.isActive
                }
                .sortedBy { program ->
                    program.draft.start.totalMinutes
                }

        val deviceTime =
            liveState.deviceTime

        val currentMinute =
            deviceTime?.curvePoint?.totalMinutes ?: currentPhoneMinute()

        val todayPrograms =
            activePrograms
                .filter { program ->
                    if (deviceTime == null) {
                        true
                    } else {
                        isScheduledToday(
                            program = program,
                            weekDay = deviceTime.weekDay
                        )
                    }
                }
                .sortedBy { program ->
                    program.draft.start.totalMinutes
                }

        val runningProgram =
            todayPrograms.firstOrNull { program ->
                isProgramRunningAt(
                    program = program,
                    minute = currentMinute
                )
            }

        val nextProgram =
            todayPrograms.firstOrNull { program ->
                program.draft.start.totalMinutes > currentMinute
            }

        val displayProgram =
            runningProgram
                ?: nextProgram
                ?: todayPrograms.firstOrNull()
                ?: activePrograms.firstOrNull()

        val outputPercent =
            if (liveState.hasLiveChannels) {
                liveState.actualOutputPercent
            } else if (runningProgram != null) {
                calculateCurrentOutputPercent(
                    program = runningProgram,
                    currentMinute = currentMinute
                )
            } else {
                0
            }

        return TankAssignedDeviceUi.Light(
            deviceId = id,
            title = title,
            subtitle = subtitle,
            iconRes = iconRes,
            isOnline = online,
            programName = displayProgram?.name ?: "No active program",
            startTimeText = displayProgram?.draft?.start?.label ?: "--:--",
            endTimeText = displayProgram?.let { program ->
                LightProgramTimeMath.endLabel(
                    program.draft.end
                )
            } ?: "--:--",
            outputPercent = outputPercent.coerceIn(
                0,
                100
            ),
            timelineProgressPercent = calculateTimelineProgressPercent(
                program = displayProgram,
                currentMinute = currentMinute
            ),
            channels = buildLightChannels(
                device = this,
                liveState = liveState,
                runningProgram = runningProgram,
                displayProgram = displayProgram,
                currentMinute = currentMinute
            )
        )
    }

    private fun buildLightChannels(
        device: DevicesDataStoreManager.DeviceInfoUi,
        liveState: LightDeviceLiveState,
        runningProgram: SavedLightProgram?,
        displayProgram: SavedLightProgram?,
        currentMinute: Int
    ): List<TankLightChannelUi> {
        return device.supportedLightChannels().map { channel ->
            val currentPercent =
                currentLightChannelPercent(
                    channel = channel,
                    liveState = liveState,
                    runningProgram = runningProgram,
                    currentMinute = currentMinute
                )

            val targetPercent =
                targetLightChannelPercent(
                    channel = channel,
                    program = displayProgram
                )

            TankLightChannelUi(
                key = channel.key,
                label = channel.label,
                currentPercent = currentPercent,
                targetPercent = targetPercent,
                colorInt = channel.colorInt
            )
        }
    }

    private fun currentLightChannelPercent(
        channel: LightChannelConfig,
        liveState: LightDeviceLiveState,
        runningProgram: SavedLightProgram?,
        currentMinute: Int
    ): Int {
        if (liveState.hasLiveChannels) {
            return when (channel.semantic) {
                LightChannelSemantic.RED,
                LightChannelSemantic.GREEN,
                LightChannelSemantic.BLUE,
                LightChannelSemantic.WHITE -> {
                    liveState.channelFor(
                        semantic = channel.semantic
                    )?.valuePercent?.coerceIn(
                        0,
                        100
                    ) ?: 0
                }

                LightChannelSemantic.UNKNOWN -> {
                    liveState.actualOutputPercent.coerceIn(
                        0,
                        100
                    )
                }
            }
        }

        if (runningProgram == null) {
            return 0
        }

        return calculateChannelOutputPercent(
            program = runningProgram,
            currentMinute = currentMinute,
            semantic = channel.semantic
        )
    }

    private fun targetLightChannelPercent(
        channel: LightChannelConfig,
        program: SavedLightProgram?
    ): Int {
        val values =
            program?.draft?.channelValues ?: return 0

        return when (channel.semantic) {
            LightChannelSemantic.RED -> values.red
            LightChannelSemantic.GREEN -> values.green
            LightChannelSemantic.BLUE -> values.blue
            LightChannelSemantic.WHITE -> values.white
            LightChannelSemantic.UNKNOWN -> {
                maxOf(
                    values.red,
                    values.green,
                    values.blue,
                    values.white
                )
            }
        }.coerceIn(
            0,
            100
        )
    }

    private fun calculateChannelOutputPercent(
        program: SavedLightProgram,
        currentMinute: Int,
        semantic: LightChannelSemantic
    ): Int {
        if (!isProgramRunningAt(program, currentMinute)) {
            return 0
        }

        val peakPercent =
            targetLightChannelPercent(
                channel = LightChannelConfig(
                    key = TankLightChannelKey.INTENSITY,
                    label = "Intensity",
                    semantic = semantic,
                    colorInt = Color.parseColor("#8EB8FF")
                ),
                program = program
            )

        if (peakPercent <= 0) {
            return 0
        }

        val points =
            LightCurveInterpolator.buildCurvePoints(
                startMinute = program.draft.start.totalMinutes,
                peakStartMinute = program.draft.peakStart.totalMinutes,
                peakEndMinute = program.draft.peakEnd.totalMinutes,
                endMinute = LightProgramTimeMath.endMinutes(
                    program.draft.end
                ),
                peakPercent = peakPercent,
                transitionMode = program.draft.transitionMode
            ).sortedBy { point ->
                point.x
            }

        if (points.isEmpty()) {
            return 0
        }

        val current =
            currentMinute.toDouble()

        val previous =
            points.lastOrNull { point ->
                point.x.toDouble() <= current
            }

        val next =
            points.firstOrNull { point ->
                point.x.toDouble() >= current
            }

        val value =
            when {
                previous == null -> {
                    points.first().y
                }

                next == null -> {
                    points.last().y
                }

                previous.x == next.x -> {
                    previous.y
                }

                else -> {
                    val previousX =
                        previous.x.toDouble()

                    val nextX =
                        next.x.toDouble()

                    val previousY =
                        previous.y.toDouble()

                    val nextY =
                        next.y.toDouble()

                    val progress =
                        (current - previousX) / (nextX - previousX)

                    previousY + ((nextY - previousY) * progress)
                }
            }

        return value
            .roundToInt()
            .coerceIn(
                0,
                100
            )
    }

    private fun calculateCurrentOutputPercent(
        program: SavedLightProgram,
        currentMinute: Int
    ): Int {
        val peakPercent =
            maxOf(
                program.draft.channelValues.red,
                program.draft.channelValues.green,
                program.draft.channelValues.blue,
                program.draft.channelValues.white
            ).coerceIn(
                0,
                100
            )

        if (peakPercent <= 0) {
            return 0
        }

        return calculateChannelOutputPercent(
            program = program,
            currentMinute = currentMinute,
            semantic = LightChannelSemantic.UNKNOWN
        ).takeIf { value ->
            value > 0
        } ?: calculateOutputFallback(
            program = program,
            currentMinute = currentMinute,
            peakPercent = peakPercent
        )
    }

    private fun calculateOutputFallback(
        program: SavedLightProgram,
        currentMinute: Int,
        peakPercent: Int
    ): Int {
        if (!isProgramRunningAt(program, currentMinute)) {
            return 0
        }

        val points =
            LightCurveInterpolator.buildCurvePoints(
                startMinute = program.draft.start.totalMinutes,
                peakStartMinute = program.draft.peakStart.totalMinutes,
                peakEndMinute = program.draft.peakEnd.totalMinutes,
                endMinute = LightProgramTimeMath.endMinutes(
                    program.draft.end
                ),
                peakPercent = peakPercent,
                transitionMode = program.draft.transitionMode
            ).sortedBy { point ->
                point.x
            }

        if (points.isEmpty()) {
            return 0
        }

        val current =
            currentMinute.toDouble()

        val previous =
            points.lastOrNull { point ->
                point.x.toDouble() <= current
            }

        val next =
            points.firstOrNull { point ->
                point.x.toDouble() >= current
            }

        val value =
            when {
                previous == null -> points.first().y
                next == null -> points.last().y
                previous.x == next.x -> previous.y
                else -> {
                    val progress =
                        (current - previous.x) / (next.x - previous.x)

                    previous.y + ((next.y - previous.y) * progress)
                }
            }

        return value.roundToInt().coerceIn(
            0,
            100
        )
    }

    private fun calculateTimelineProgressPercent(
        program: SavedLightProgram?,
        currentMinute: Int
    ): Int {
        if (program == null) {
            return 0
        }

        val start =
            program.draft.start.totalMinutes

        val end =
            LightProgramTimeMath.endMinutes(
                program.draft.end
            )

        if (end <= start) {
            return 0
        }

        return when {
            currentMinute <= start -> 0
            currentMinute >= end -> 100
            else -> {
                (((currentMinute - start).toFloat() / (end - start).toFloat()) * 100f)
                    .roundToInt()
                    .coerceIn(
                        0,
                        100
                    )
            }
        }
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.supportedLightChannels(): List<LightChannelConfig> {
        val rawText =
            lightSearchText()

        return when {
            rawText.contains("wrgb") -> {
                listOf(
                    LightChannelConfig(
                        key = TankLightChannelKey.WHITE,
                        label = "White",
                        semantic = LightChannelSemantic.WHITE,
                        colorInt = Color.parseColor("#D8DDE4")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.RED,
                        label = "Red",
                        semantic = LightChannelSemantic.RED,
                        colorInt = Color.parseColor("#D16D6D")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.GREEN,
                        label = "Green",
                        semantic = LightChannelSemantic.GREEN,
                        colorInt = Color.parseColor("#72B77D")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.BLUE,
                        label = "Blue",
                        semantic = LightChannelSemantic.BLUE,
                        colorInt = Color.parseColor("#6D97D1")
                    )
                )
            }

            rawText.contains("rgb") -> {
                listOf(
                    LightChannelConfig(
                        key = TankLightChannelKey.RED,
                        label = "Red",
                        semantic = LightChannelSemantic.RED,
                        colorInt = Color.parseColor("#D16D6D")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.GREEN,
                        label = "Green",
                        semantic = LightChannelSemantic.GREEN,
                        colorInt = Color.parseColor("#72B77D")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.BLUE,
                        label = "Blue",
                        semantic = LightChannelSemantic.BLUE,
                        colorInt = Color.parseColor("#6D97D1")
                    )
                )
            }

            else -> {
                listOf(
                    LightChannelConfig(
                        key = TankLightChannelKey.INTENSITY,
                        label = "Intensity",
                        semantic = LightChannelSemantic.UNKNOWN,
                        colorInt = Color.parseColor("#8EB8FF")
                    )
                )
            }
        }
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.isLightDevice(): Boolean {
        val rawText =
            lightSearchText()

        return rawText.contains(
            "light"
        ) || rawText.contains(
            "wrgb"
        ) || rawText.contains(
            "rgb"
        )
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.lightSearchText(): String {
        val definition =
            AquaDeviceCatalog.findByType(
                type = deviceType
            )

        return listOf(
            deviceType.storageKey,
            definition?.displayName.orEmpty(),
            definition?.family?.displayName.orEmpty(),
            name,
            productModel,
            productFamily,
            aquaName
        )
            .joinToString(
                separator = " "
            )
            .lowercase()
    }

    private fun getDeviceTitle(
        device: DevicesDataStoreManager.DeviceInfoUi
    ): String {
        val definition =
            AquaDeviceCatalog.findByType(
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
        val definition =
            AquaDeviceCatalog.findByType(
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
        device: DevicesDataStoreManager.DeviceInfoUi,
        statuses: Map<Long, DeviceStatusState>,
        now: Long
    ): Boolean {
        val statusState =
            statuses[device.id]

        return statusState?.isOnline ?: (
            device.lastSeenMillis > 0L &&
                now - device.lastSeenMillis <= ONLINE_TIMEOUT_MS
            )
    }

    private fun isProgramRunningAt(
        program: SavedLightProgram,
        minute: Int
    ): Boolean {
        val start =
            program.draft.start.totalMinutes

        val end =
            LightProgramTimeMath.endMinutes(
                program.draft.end
            )

        return minute >= start && minute < end
    }

    private fun isScheduledToday(
        program: SavedLightProgram,
        weekDay: Int
    ): Boolean {
        val selectedDays =
            program.draft.selectedDays

        if (selectedDays.isEmpty()) {
            return true
        }

        return selectedDays.contains(
            appDayFromDeviceWeekDay(
                weekDay = weekDay
            )
        )
    }

    private fun appDayFromDeviceWeekDay(
        weekDay: Int
    ): Int {
        return when (weekDay) {
            in 1..7 -> weekDay
            else -> todayAppDay()
        }
    }

    private fun todayAppDay(): Int {
        val dayOfWeek =
            Calendar.getInstance()
                .get(Calendar.DAY_OF_WEEK)

        return if (dayOfWeek == Calendar.SUNDAY) {
            7
        } else {
            dayOfWeek - 1
        }
    }

    private fun currentPhoneMinute(): Int {
        val calendar =
            Calendar.getInstance()

        return calendar.get(Calendar.HOUR_OF_DAY) * 60 +
            calendar.get(Calendar.MINUTE)
    }

    private fun handleDeviceClick(
        device: TankAssignedDeviceUi
    ) {
        // Sonraki adımda cihaz detayına/router'a açacağız.
    }

    private fun handleDeviceLongClick(
        device: TankAssignedDeviceUi
    ) {
        // Sonraki adımda Remove / Settings bottom sheet burada açılacak.
    }

    private fun openTankDeviceSelectScreen() {
        val args =
            Bundle().apply {
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

    override fun onDestroyView() {
        lightLiveJobs.forEach { entry ->
            entry.value.cancel()

            LightDeviceLiveRefreshManager.stop(
                deviceId = entry.key,
                ownerKey = liveRefreshOwnerKey
            )
        }

        lightLiveJobs.clear()
        latestLightStates.clear()

        binding.rvTankDevices.adapter =
            null

        _binding =
            null

        super.onDestroyView()
    }

    private data class LightChannelConfig(
        val key: TankLightChannelKey,
        val label: String,
        val semantic: LightChannelSemantic,
        val colorInt: Int
    )

    companion object {

        private const val ARG_TANK_ID =
            "tankId"

        private const val ONLINE_TIMEOUT_MS =
            90_000L

        fun newInstance(
            tankId: Long
        ): TankDetailDevicesFragment {
            return TankDetailDevicesFragment().apply {
                arguments =
                    Bundle().apply {
                        putLong(
                            ARG_TANK_ID,
                            tankId
                        )
                    }
            }
        }
    }
}