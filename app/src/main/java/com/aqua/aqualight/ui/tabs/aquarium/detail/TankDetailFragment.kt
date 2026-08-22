package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.databinding.FragmentTankDetailBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.tabs.AquaSwipeTabHost
import com.aqua.aqualight.ui.common.tabs.AquaSwipeTabSpec
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.AquariumTabArgs
import com.aqua.aqualight.ui.tabs.aquarium.navigation.TankDetailTabArgs
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteTarget
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel

class TankDetailFragment :
    Fragment(R.layout.fragment_tank_detail),
    TankDetailDevicesFragment.Host {

    private val args: TankDetailFragmentArgs by navArgs()

    private var _binding: FragmentTankDetailBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var selectedTab: TankDetailTab = TankDetailTab.DEVICES
    private var currentTank: AquariumTankSnapshot? = null
    private var tabHost: AquaSwipeTabHost<TankDetailTab>? = null

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
        setupSystemBackButton()
        setupTankTabPager(
            initialTab = selectedTab
        )
        observeCareProfileActions()
        observeTank()
    }

    private fun setupHeader(
        title: String
    ) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = title,
                onBackClick = {
                    findNavController().navigateUp()
                },
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_edit_24,
                        contentDescription = getString(R.string.aquarium_content_desc_edit_tank),
                        onClick = {
                            openTankSettings()
                        }
                    )
                )
            )
        )
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

    private fun setupTankTabPager(
        initialTab: TankDetailTab
    ) {
        tabHost = AquaSwipeTabHost(
            fragment = this,
            tabLayout = binding.tankTabs,
            viewPager = binding.tankDetailPager,
            tabs = TAB_ORDER,
            onTabSelected = { tab ->
                selectedTab = tab
                saveSelectedTabState(
                    tab = tab
                )
            }
        ).also { host ->
            host.attach(
                adapter = TankDetailPagerAdapter(
                    fragment = this,
                    tankId = tankId
                ),
                initialTab = initialTab,
                offscreenPageLimit = TANK_PAGER_OFFSCREEN_LIMIT
            )
        }
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

    private fun selectTab(
        tab: TankDetailTab,
        smoothScroll: Boolean = true
    ) {
        tabHost?.select(
            tab = tab,
            smoothScroll = smoothScroll
        ) ?: run {
            selectedTab = tab
            saveSelectedTabState(
                tab = tab
            )
        }
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
        route: DeviceRoute
    ) {
        selectedTab = TankDetailTab.DEVICES
        saveSelectedTabState(
            tab = TankDetailTab.DEVICES
        )

        openDeviceRoute(route)
    }

    private fun openDeviceRoute(
        route: DeviceRoute
    ) {
        val directions = when (route.target) {
            DeviceRouteTarget.LIGHT_ROOT ->
                TankDetailFragmentDirections.actionTankDetailFragmentToDeviceLightRootFragment(
                    deviceUid = route.deviceUid,
                    deviceTitle = route.title.ifBlank { getString(route.titleRes) }
                )

            DeviceRouteTarget.DOSING_ROOT ->
                TankDetailFragmentDirections.actionTankDetailFragmentToDeviceDosingRootFragment(
                    deviceUid = route.deviceUid,
                    deviceTitle = route.title.ifBlank { getString(route.titleRes) },
                    presentationPrepared = route.presentationPrepared
                )

            DeviceRouteTarget.TIMER_ROOT ->
                TankDetailFragmentDirections.actionTankDetailFragmentToDeviceTimerRootFragment(
                    deviceUid = route.deviceUid,
                    deviceTitle = route.title.ifBlank { getString(route.titleRes) }
                )

            DeviceRouteTarget.COOLING_ROOT ->
                TankDetailFragmentDirections.actionTankDetailFragmentToDeviceCoolingRootFragment(
                    deviceUid = route.deviceUid,
                    deviceTitle = route.title.ifBlank { getString(route.titleRes) }
                )

            DeviceRouteTarget.UNSUPPORTED ->
                TankDetailFragmentDirections.actionTankDetailFragmentToUnsupportedDeviceFragment(
                    deviceTitle = route.title.ifBlank { getString(route.titleRes) },
                    message = route.messageRes.takeIf { it != 0 }
                        ?.let { getString(it) }
                        .orEmpty(),
                    deviceUid = route.deviceUid
                )
        }

        navigateFromTankDetail(directions)
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
        tank: AquariumTankSnapshot
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
        tabHost?.detach()
        tabHost = null

        _binding =
            null

        super.onDestroyView()
    }

    private class TankDetailPagerAdapter(
        fragment: Fragment,
        private val tankId: Long
    ) : FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int {
            return TAB_ORDER.size
        }

        override fun createFragment(
            position: Int
        ): Fragment {
            return when (TAB_ORDER[position]) {
                TankDetailTab.DEVICES ->
                    TankDetailDevicesFragment.newInstance(
                        tankId
                    )

                TankDetailTab.ACTIVITY ->
                    TankDetailActivityFragment.newInstance(
                        tankId
                    )

                TankDetailTab.TANK ->
                    TankDetailTankFragment.newInstance(
                        tankId
                    )

                TankDetailTab.PLANTS ->
                    TankDetailPlantsFragment.newInstance(
                        tankId
                    )

                TankDetailTab.TANK_LIFE ->
                    TankDetailLifeFragment.newInstance(
                        tankId
                    )
            }
        }

        override fun getItemId(
            position: Int
        ): Long {
            return TAB_ORDER[position].stableId
        }

        override fun containsItem(
            itemId: Long
        ): Boolean {
            return TAB_ORDER.any { tab ->
                tab.stableId == itemId
            }
        }
    }

    private enum class TankDetailTab(
        @StringRes override val titleRes: Int,
        override val stableId: Long
    ) : AquaSwipeTabSpec {
        DEVICES(
            R.string.aquarium_detail_tab_devices,
            1L
        ),
        ACTIVITY(
            R.string.aquarium_detail_tab_activity,
            2L
        ),
        TANK(
            R.string.aquarium_detail_tab_tank,
            3L
        ),
        PLANTS(
            R.string.aquarium_detail_tab_plants,
            4L
        ),
        TANK_LIFE(
            R.string.aquarium_tank_life_title,
            5L
        )
    }

    companion object {
        const val KEY_SELECTED_TAB = "tank_detail_selected_tab"
        const val KEY_RETURN_TAB = "tank_detail_return_tab"

        private const val TANK_PAGER_OFFSCREEN_LIMIT = 1

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
