package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.databinding.FragmentTankDetailBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankAssignedDeviceUi
import com.aqua.aqualight.ui.tabs.aquarium.navigation.AquariumChildTabHost
import com.aqua.aqualight.ui.tabs.aquarium.navigation.AquariumTabArgs
import com.aqua.aqualight.ui.tabs.aquarium.navigation.TankDetailTabArgs
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class TankDetailFragment :
    Fragment(R.layout.fragment_tank_detail),
    TankDetailDevicesFragment.Host {

    private val args: TankDetailFragmentArgs by navArgs()

    private var _binding: FragmentTankDetailBinding? = null
    private val binding get() = _binding!!

    private val tankDetailViewModel: TankDetailViewModel by viewModels()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var selectedTab: TankDetailTab = TankDetailTab.DEVICES
    private var currentTank: SavedAquariumTank? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        tankId = args.tankId
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
            FragmentTankDetailBinding.bind(view)

        selectedTab =
            restoreSelectedTab(
                savedInstanceState = savedInstanceState
            )

        setupHeader(
            title = getString(R.string.screen_title_aquarium)
        )
        setupClickListeners()
        setupSystemBackButton()
        setupSwipeBetweenTabs()
        observeDeviceOpenState()
        observeCareProfileActions()
        observeTank()
        selectTab(
            tab = selectedTab
        )
    }

    private fun observeDeviceOpenState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                launch {
                    tankDetailViewModel.isOpeningDevice.collect { isOpening ->
                        showGlobalLoading(
                            show = isOpening
                        )
                    }
                }

                launch {
                    tankDetailViewModel.events.collect { event ->
                        handleDeviceOpenEvent(
                            event = event
                        )
                    }
                }
            }
        }
    }

    private fun handleDeviceOpenEvent(
        event: TankDetailViewModel.TankDetailEvent
    ) {
        if (!isAdded || _binding == null) {
            return
        }

        when (event) {
            is TankDetailViewModel.TankDetailEvent.NavigateToDeviceRouter -> {
                navigateFromTankDetail(
                    TankDetailFragmentDirections.actionTankDetailFragmentToDeviceRouterFragment(
                        deviceId = event.deviceId,
                        deviceIp = event.deviceIp,
                        deviceTitle = event.deviceTitle
                    )
                )
            }

            TankDetailViewModel.TankDetailEvent.ShowOffline -> {
                showDeviceOfflineDialog()
            }

            TankDetailViewModel.TankDetailEvent.ShowNotFound -> {
                showDeviceInfoDialog(
                    title = getString(R.string.aquarium_device_not_found_title),
                    message = getString(R.string.aquarium_device_not_found_message)
                )
            }

            TankDetailViewModel.TankDetailEvent.ShowUnsupported -> {
                showDeviceInfoDialog(
                    title = getString(R.string.aquarium_unsupported_device_title),
                    message = getString(R.string.aquarium_unsupported_device_message)
                )
            }

            TankDetailViewModel.TankDetailEvent.ShowOpenFailed -> {
                showDeviceInfoDialog(
                    title = getString(R.string.aquarium_device_open_failed_title),
                    message = getString(R.string.aquarium_device_open_failed_message)
                )
            }
        }
    }

    private fun setupHeader(
        title: String
    ) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = title,
                onBackClick = {
                    findNavController().navigateUp()
                },
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_edit_24,
                        contentDescription = "Edit tank",
                        onClick = {
                            openTankSettings()
                        }
                    )
                )
            )
        )
    }

    private fun restoreSelectedTab(
        savedInstanceState: Bundle?
    ): TankDetailTab {
        val savedStateHandle = findNavController()
            .currentBackStackEntry
            ?.savedStateHandle

        val returnTab = savedStateHandle
            ?.remove<String>(KEY_RETURN_TAB)
            ?.let { tabName ->
                tabFromStoredValue(
                    value = tabName
                )
            }

        if (returnTab != null) {
            return returnTab
        }

        val navSavedTab = savedStateHandle
            ?.get<String>(KEY_SELECTED_TAB)
            ?.let { tabName ->
                tabFromStoredValue(
                    value = tabName
                )
            }

        if (navSavedTab != null) {
            return navSavedTab
        }

        val instanceSavedTab = savedInstanceState
            ?.getString(KEY_SELECTED_TAB)
            ?.let { tabName ->
                tabFromStoredValue(
                    value = tabName
                )
            }

        if (instanceSavedTab != null) {
            return instanceSavedTab
        }

        return tabFromStartArgument(
            startTab = args.startTab
        )
    }

    private fun tabFromStartArgument(
        startTab: String
    ): TankDetailTab {
        return tabFromStoredValue(
            value = startTab
        ) ?: TankDetailTab.DEVICES
    }

    private fun tabFromStoredValue(
        value: String
    ): TankDetailTab? {
        return runCatching {
            TankDetailTab.valueOf(value)
        }.getOrNull() ?: when (value) {
            TankDetailTabArgs.ACTIVITY -> TankDetailTab.ACTIVITY
            TankDetailTabArgs.TANK -> TankDetailTab.TANK
            TankDetailTabArgs.PLANTS -> TankDetailTab.PLANTS
            TankDetailTabArgs.TANK_LIFE -> TankDetailTab.TANK_LIFE
            TankDetailTabArgs.DEVICES -> TankDetailTab.DEVICES
            else -> null
        }
    }

    private fun saveSelectedTabState(
        tab: TankDetailTab
    ) {
        findNavController()
            .currentBackStackEntry
            ?.savedStateHandle
            ?.set(
                KEY_SELECTED_TAB,
                tab.name
            )
    }

    private fun setupClickListeners() {
        binding.tabDevices.setOnClickListener {
            selectTab(
                TankDetailTab.DEVICES
            )
        }

        binding.tabActivity.setOnClickListener {
            selectTab(
                TankDetailTab.ACTIVITY
            )
        }

        binding.tabTank.setOnClickListener {
            selectTab(
                TankDetailTab.TANK
            )
        }

        binding.tabPlants.setOnClickListener {
            selectTab(
                TankDetailTab.PLANTS
            )
        }

        binding.tabTankLife.setOnClickListener {
            selectTab(
                TankDetailTab.TANK_LIFE
            )
        }
    }

    private fun setupSystemBackButton() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    findNavController().navigateUp()
                }
            }
        )
    }

    private fun setupSwipeBetweenTabs() {
        listOf(
            binding.devicesFragmentContainer,
            binding.activityFragmentContainer,
            binding.tankFragmentContainer,
            binding.plantsFragmentContainer,
            binding.tankLifeFragmentContainer
        ).forEach { container ->
            container.setOnSwipeLeftListener {
                moveTabBy(
                    offset = 1
                )
            }

            container.setOnSwipeRightListener {
                moveTabBy(
                    offset = -1
                )
            }
        }
    }

    private fun showGlobalLoading(
        show: Boolean
    ) {
        (activity as? BaseActivity)?.showLoading(
            show
        )
    }

    private fun moveTabBy(
        offset: Int
    ) {
        val currentIndex =
            TAB_ORDER.indexOf(
                selectedTab
            )

        if (currentIndex == -1) {
            return
        }

        val targetIndex =
            (currentIndex + offset).coerceIn(
                0,
                TAB_ORDER.lastIndex
            )

        if (targetIndex == currentIndex) {
            return
        }

        selectTab(
            TAB_ORDER[targetIndex]
        )
    }

    private fun observeCareProfileActions() {
        val savedStateHandle =
            findNavController()
                .currentBackStackEntry
                ?.savedStateHandle
                ?: return

        savedStateHandle.getLiveData<String>(
            KEY_CARE_PROFILE_ACTION
        ).observe(viewLifecycleOwner) { action ->
            if (action.isBlank()) {
                return@observe
            }

            savedStateHandle.remove<String>(
                KEY_CARE_PROFILE_ACTION
            )

            binding.root.post {
                when (action) {
                    CARE_PROFILE_ACTION_PLANTS -> {
                        selectTab(
                            TankDetailTab.PLANTS
                        )

                        openPlantTagScreen()
                    }

                    CARE_PROFILE_ACTION_LIVESTOCK -> {
                        selectTab(
                            TankDetailTab.TANK_LIFE
                        )

                        openLivestockFormIfNeeded()
                    }
                }
            }
        }
    }

    private fun openLivestockFormIfNeeded() {
        val hasLivestock =
            currentTank
                ?.livestock
                ?.isNotEmpty() == true

        if (!hasLivestock) {
            openLivestockFormScreen()
        }
    }

    private fun openTankSettings() {
        navigateFromTankDetail(
            TankDetailFragmentDirections.actionTankDetailFragmentToTankSettingsFragment(
                tankId = tankId,
                startTab = AquariumTabArgs.BASIC
            )
        )
    }

    private fun openLivestockFormScreen(
        livestockId: Long = 0L
    ) {
        selectedTab =
            TankDetailTab.TANK_LIFE

        saveSelectedTabState(
            tab = TankDetailTab.TANK_LIFE
        )

        navigateFromTankDetail(
            TankDetailFragmentDirections.actionTankDetailFragmentToTankDetailLivestockFormFragment(
                tankId = tankId,
                livestockId = livestockId
            )
        )
    }

    private fun openPlantTagScreen() {
        selectedTab =
            TankDetailTab.PLANTS

        saveSelectedTabState(
            tab = TankDetailTab.PLANTS
        )

        navigateFromTankDetail(
            TankDetailFragmentDirections.actionTankDetailFragmentToTankDetailPlantTagFragment(
                tankId = tankId
            )
        )
    }

    override fun onTankDetailAddDeviceClicked(
        tankId: Long
    ) {
        if (tankId != this.tankId) {
            return
        }

        navigateFromTankDetail(
            TankDetailFragmentDirections.actionTankDetailFragmentToTankDeviceSelectFragment(
                tankId = this.tankId
            )
        )
    }

    override fun onTankDetailDeviceClicked(
        device: TankAssignedDeviceUi
    ) {
        tankDetailViewModel.openDevice(
            deviceId = device.deviceId,
            deviceTitle = device.title
        )
    }

    private fun showDeviceOfflineDialog() {
        showDeviceInfoDialog(
            title = getString(R.string.aquarium_device_offline_title),
            message = getString(
                R.string.device_offline_message
            )
        )
    }

    private fun showDeviceInfoDialog(
        title: String,
        message: String
    ) {
        if (!isAdded || _binding == null) {
            return
        }

        DialogManager.showInfoDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = title,
            message = message
        )
    }

    private fun navigateFromTankDetail(
        directions: NavDirections
    ) {
        val navController =
            findNavController()

        if (navController.currentDestination?.id != R.id.tankDetailFragment) {
            return
        }

        saveSelectedTabState(
            tab = selectedTab
        )

        navController.navigate(
            directions
        )
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            maintenanceViewModel.setTanks(tanks)

            val tank =
                tanks.firstOrNull { tank ->
                    tank.id == tankId
                }

            if (tank == null) {
                findNavController().navigateUp()
                return@observe
            }

            bindTank(
                tank = tank
            )
        }
    }

    private fun bindTank(
        tank: SavedAquariumTank
    ) {
        currentTank =
            tank

        setupHeader(
            title = tank.name
        )

        if (!tank.photoUri.isNullOrBlank()) {
            binding.imgTankPhoto.load(Uri.parse(tank.photoUri)) {
                placeholder(R.drawable.nature_aquarium)
                error(R.drawable.nature_aquarium)
                crossfade(true)
            }
        } else {
            binding.imgTankPhoto.setImageResource(
                R.drawable.nature_aquarium
            )
        }

        binding.markerContainer.removeAllViews()
        binding.markerContainer.isVisible =
            false
    }

    private fun selectTab(
        tab: TankDetailTab
    ) {
        selectedTab =
            tab

        saveSelectedTabState(
            tab = tab
        )

        resetTabs()
        activateTab(
            tabViewFor(
                tab = tab
            )
        )
        moveTabUnderline(
            tabViewFor(
                tab = tab
            )
        )
        showContentForTab(
            tab = tab
        )

        binding.contentScrollView.post {
            binding.contentScrollView.scrollTo(
                0,
                0
            )
        }
    }

    private fun showContentForTab(
        tab: TankDetailTab
    ) {
        binding.contentScrollView.isVisible =
            false

        binding.tvEmptyTab.isVisible =
            false

        when (tab) {
            TankDetailTab.DEVICES -> {
                binding.devicesFragmentContainer.isVisible =
                    true

                showFragmentIfNeeded(
                    containerId = R.id.devicesFragmentContainer,
                    tag = TAG_DEVICES_FRAGMENT
                ) {
                    TankDetailDevicesFragment.newInstance(
                        tankId
                    )
                }
            }

            TankDetailTab.ACTIVITY -> {
                binding.activityFragmentContainer.isVisible =
                    true

                showFragmentIfNeeded(
                    containerId = R.id.activityFragmentContainer,
                    tag = TAG_ACTIVITY_FRAGMENT
                ) {
                    TankDetailActivityFragment.newInstance(
                        tankId
                    )
                }
            }

            TankDetailTab.TANK -> {
                binding.tankFragmentContainer.isVisible =
                    true

                showFragmentIfNeeded(
                    containerId = R.id.tankFragmentContainer,
                    tag = TAG_TANK_FRAGMENT
                ) {
                    TankDetailTankFragment.newInstance(
                        tankId
                    )
                }
            }

            TankDetailTab.PLANTS -> {
                binding.plantsFragmentContainer.isVisible =
                    true

                showFragmentIfNeeded(
                    containerId = R.id.plantsFragmentContainer,
                    tag = TAG_PLANTS_FRAGMENT
                ) {
                    TankDetailPlantsFragment.newInstance(
                        tankId
                    )
                }
            }

            TankDetailTab.TANK_LIFE -> {
                binding.tankLifeFragmentContainer.isVisible =
                    true

                showFragmentIfNeeded(
                    containerId = R.id.tankLifeFragmentContainer,
                    tag = TAG_TANK_LIFE_FRAGMENT
                ) {
                    TankDetailLifeFragment.newInstance(
                        tankId
                    )
                }
            }
        }
    }

    private fun showFragmentIfNeeded(
        containerId: Int,
        tag: String,
        fragmentFactory: () -> Fragment
    ) {
        AquariumChildTabHost.showOnce(
            fragmentManager = childFragmentManager,
            containerId = containerId,
            tag = tag,
            fragmentFactory = fragmentFactory
        )
    }

    private fun tabViewFor(
        tab: TankDetailTab
    ): TextView {
        return when (tab) {
            TankDetailTab.DEVICES -> binding.tabDevices
            TankDetailTab.ACTIVITY -> binding.tabActivity
            TankDetailTab.TANK -> binding.tabTank
            TankDetailTab.PLANTS -> binding.tabPlants
            TankDetailTab.TANK_LIFE -> binding.tabTankLife
        }
    }

    private fun activateTab(
        tabView: TextView
    ) {
        tabView.setTextColor(
            Color.WHITE
        )

        tabView.setTypeface(
            null,
            Typeface.BOLD
        )
    }

    private fun moveTabUnderline(
        tabView: TextView
    ) {
        binding.tabsContainer.post {
            val underlineWidth =
                (tabView.width * 0.58f)
                    .toInt()
                    .coerceIn(
                        34.dp(),
                        68.dp()
                    )

            val params =
                binding.tabUnderline.layoutParams

            params.width =
                underlineWidth

            binding.tabUnderline.layoutParams =
                params

            val targetX =
                tabView.x + ((tabView.width - underlineWidth) / 2f)

            binding.tabUnderline.animate()
                .translationX(targetX)
                .setDuration(180)
                .start()
        }
    }

    private fun resetTabs() {
        val inactiveColor =
            Color.parseColor("#8FA4BE")

        TankDetailTab.values().forEach { tab ->
            tabViewFor(
                tab = tab
            ).apply {
                setTextColor(
                    inactiveColor
                )

                setTypeface(
                    null,
                    Typeface.NORMAL
                )
            }
        }

        hideAllTabContainers()
    }

    private fun hideAllTabContainers() {
        binding.contentScrollView.isVisible =
            true

        binding.devicesFragmentContainer.isVisible =
            false

        binding.activityFragmentContainer.isVisible =
            false

        binding.tankFragmentContainer.isVisible =
            false

        binding.plantsFragmentContainer.isVisible =
            false

        binding.tankLifeFragmentContainer.isVisible =
            false

        binding.tvEmptyTab.isVisible =
            false
    }

    private fun Int.dp(): Int {
        return (
            this * resources.displayMetrics.density
        ).toInt()
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        super.onSaveInstanceState(
            outState
        )

        outState.putString(
            KEY_SELECTED_TAB,
            selectedTab.name
        )
    }

    override fun onDestroyView() {
        showGlobalLoading(
            show = false
        )

        _binding =
            null

        super.onDestroyView()
    }

    private enum class TankDetailTab {
        DEVICES,
        ACTIVITY,
        TANK,
        PLANTS,
        TANK_LIFE
    }

    companion object {
        const val KEY_SELECTED_TAB = "tank_detail_selected_tab"
        const val KEY_RETURN_TAB = "tank_detail_return_tab"

        private const val TAG_DEVICES_FRAGMENT = "TankDetailDevicesFragment"
        private const val TAG_ACTIVITY_FRAGMENT = "TankDetailActivityFragment"
        private const val TAG_TANK_FRAGMENT = "TankDetailTankFragment"
        private const val TAG_PLANTS_FRAGMENT = "TankDetailPlantsFragment"
        private const val TAG_TANK_LIFE_FRAGMENT = "TankDetailLifeFragment"

        private val TAB_ORDER = listOf(
            TankDetailTab.DEVICES,
            TankDetailTab.ACTIVITY,
            TankDetailTab.TANK,
            TankDetailTab.PLANTS,
            TankDetailTab.TANK_LIFE
        )

        const val KEY_CARE_PROFILE_ACTION = "care_profile_action"
        const val CARE_PROFILE_ACTION_PLANTS = "plants"
        const val CARE_PROFILE_ACTION_LIVESTOCK = "livestock"
    }
}
