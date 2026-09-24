package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.AquariumTabArgs
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
    private lateinit var tabCoordinator: TankDetailTabCoordinator
    private var pendingPagerSavedState: Bundle? = null
    private var isTabPagerAttached: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tankId = args.tankId
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankDetailBinding.bind(view)

        tabCoordinator = TankDetailTabCoordinator(findNavController())
        pendingPagerSavedState = savedInstanceState ?: pendingPagerSavedState

        renderHeader(getString(R.string.screen_title_aquarium))

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    findNavController().navigateUp()
                }
            }
        )

        observeCareProfileActions()
        observeTank()
    }

    override fun onResume() {
        super.onResume()
        attachTabPagerIfNeeded()
    }

    private fun attachTabPagerIfNeeded() {
        if (isTabPagerAttached || _binding == null) {
            return
        }

        tabCoordinator.attach(
            fragment = this,
            binding = binding,
            tankId = tankId,
            startTab = args.startTab,
            savedInstanceState = pendingPagerSavedState
        )
        pendingPagerSavedState = null
        isTabPagerAttached = true
    }

    private fun renderHeader(title: String) {
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
                        contentDescription = getString(
                            R.string.aquarium_content_desc_edit_tank
                        ),
                        onClick = {
                            navigateFromTankDetail(
                                TankDetailFragmentDirections
                                    .actionTankDetailFragmentToTankSettingsFragment(
                                        tankId = tankId,
                                        startTab = AquariumTabArgs.BASIC
                                    )
                            )
                        }
                    )
                )
            )
        )
    }

    private fun observeCareProfileActions() {
        val savedStateHandle = findNavController()
            .currentBackStackEntry
            ?.savedStateHandle
            ?: return

        savedStateHandle.getLiveData<String>(
            KEY_CARE_PROFILE_ACTION
        ).observe(viewLifecycleOwner) { action ->
            if (action.isBlank()) {
                return@observe
            }

            savedStateHandle.remove<String>(KEY_CARE_PROFILE_ACTION)

            binding.root.post {
                when (TankDetailCareProfileActionHandler.resolve(action)) {
                    TankDetailCareProfileTarget.PLANTS -> {
                        tabCoordinator.select(TankDetailTab.PLANTS)
                        navigateFromTankDetail(
                            TankDetailFragmentDirections
                                .actionTankDetailFragmentToTankDetailPlantTagFragment(
                                    tankId = tankId
                                )
                        )
                    }

                    TankDetailCareProfileTarget.LIVESTOCK -> {
                        tabCoordinator.select(TankDetailTab.TANK_LIFE)
                        navigateFromTankDetail(
                            TankDetailFragmentDirections
                                .actionTankDetailFragmentToTankLivestockPickerFragment(
                                    tankId = tankId
                                )
                        )
                    }

                    null -> Unit
                }
            }
        }
    }

    override fun onTankDetailAddDeviceClicked(tankId: Long) {
        if (tankId != this.tankId) {
            return
        }

        navigateFromTankDetail(
            TankDetailFragmentDirections.actionTankDetailFragmentToTankDeviceSelectFragment(
                tankId = this.tankId
            )
        )
    }

    override fun onTankDetailDeviceClicked(route: DeviceRoute): Boolean {
        tabCoordinator.select(TankDetailTab.DEVICES)

        val directions = when (route.target) {
            DeviceRouteTarget.LIGHT_ROOT ->
                TankDetailFragmentDirections.actionTankDetailFragmentToDeviceLightRootFragment(
                    deviceUid = route.deviceUid
                )

            DeviceRouteTarget.DOSING_ROOT ->
                TankDetailFragmentDirections.actionTankDetailFragmentToDeviceDosingRootFragment(
                    deviceUid = route.deviceUid
                )

            DeviceRouteTarget.TIMER_ROOT ->
                TankDetailFragmentDirections.actionTankDetailFragmentToDeviceTimerRootFragment(
                    deviceUid = route.deviceUid
                )

            DeviceRouteTarget.COOLING_ROOT ->
                TankDetailFragmentDirections.actionTankDetailFragmentToDeviceCoolingRootFragment(
                    deviceUid = route.deviceUid
                )

            DeviceRouteTarget.UNSUPPORTED ->
                TankDetailFragmentDirections.actionTankDetailFragmentToUnsupportedDeviceFragment(
                    deviceTitle = route.unsupportedTitle.ifBlank {
                        getString(R.string.device_menu_default_title)
                    },
                    message = route.messageRes.takeIf { it != 0 }
                        ?.let { getString(it) }
                        .orEmpty(),
                    deviceUid = route.deviceUid
                )
        }

        return navigateFromTankDetail(directions)
    }

    private fun navigateFromTankDetail(directions: NavDirections): Boolean {
        val navController = findNavController()

        if (
            navController.currentDestination?.id != R.id.tankDetailFragment ||
            parentFragmentManager.isStateSaved
        ) {
            return false
        }

        tabCoordinator.persistSelection()
        navController.navigate(directions)
        return true
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            maintenanceViewModel.setTanks(tanks)

            val tank = tanks.firstOrNull { tank ->
                tank.id == tankId
            }

            if (tank == null) {
                findNavController().navigateUp()
                return@observe
            }

            renderHeader(tank.name)

            if (!tank.photoUri.isNullOrBlank()) {
                binding.imgTankPhoto.load(Uri.parse(tank.photoUri)) {
                    placeholder(R.drawable.nature_aquarium)
                    error(R.drawable.nature_aquarium)
                    crossfade(true)
                }
            } else {
                binding.imgTankPhoto.setImageResource(R.drawable.nature_aquarium)
            }

            binding.markerContainer.removeAllViews()
            binding.markerContainer.isVisible = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (::tabCoordinator.isInitialized && isTabPagerAttached) {
            tabCoordinator.saveInstanceState(outState)
        } else {
            pendingPagerSavedState
                ?.getString(KEY_SELECTED_TAB)
                ?.let { selectedTab ->
                    outState.putString(KEY_SELECTED_TAB, selectedTab)
                }
        }
    }

    override fun onDestroyView() {
        if (::tabCoordinator.isInitialized) {
            tabCoordinator.detach()
        }
        isTabPagerAttached = false

        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val KEY_SELECTED_TAB = "tank_detail_selected_tab"
        const val KEY_RETURN_TAB = "tank_detail_return_tab"
        const val KEY_CARE_PROFILE_ACTION = "care_profile_action"
        const val CARE_PROFILE_ACTION_PLANTS = "plants"
        const val CARE_PROFILE_ACTION_LIVESTOCK = "livestock"
    }
}
