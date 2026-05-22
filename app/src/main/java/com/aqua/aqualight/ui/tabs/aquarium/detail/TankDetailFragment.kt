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
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel

class TankDetailFragment : Fragment(R.layout.fragment_tank_detail) {

    private var _binding: FragmentTankDetailBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var selectedTab: TankDetailTab = TankDetailTab.DEVICES
    private var currentTank: SavedAquariumTank? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankDetailBinding.bind(view)

        selectedTab = savedInstanceState
            ?.getString(KEY_SELECTED_TAB)
            ?.let { tabName ->
                runCatching {
                    TankDetailTab.valueOf(tabName)
                }.getOrNull()
            } ?: selectedTab

        setupClickListeners()
        setupSystemBackButton()
        observeCareProfileActions()
        observeTank()
        selectTab(selectedTab)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            if (handleLifeFlowBack()) {
                return@setOnClickListener
            }

            if (handlePlantFlowBack()) {
                return@setOnClickListener
            }

            findNavController().navigateUp()
        }

        binding.btnEdit.setOnClickListener {
            openTankSettings()
        }

        binding.tabDevices.setOnClickListener {
            selectTab(TankDetailTab.DEVICES)
        }

        binding.tabActivity.setOnClickListener {
            selectTab(TankDetailTab.ACTIVITY)
        }

        binding.tabTank.setOnClickListener {
            selectTab(TankDetailTab.TANK)
        }

        binding.tabPlants.setOnClickListener {
            selectTab(TankDetailTab.PLANTS)
        }

        binding.tabTankLife.setOnClickListener {
            selectTab(TankDetailTab.TANK_LIFE)
        }
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

            savedStateHandle.remove<String>(
                KEY_CARE_PROFILE_ACTION
            )

            binding.root.post {
                when (action) {
                    CARE_PROFILE_ACTION_PLANTS -> {
                        selectTab(TankDetailTab.PLANTS)
                        openPlantTagFlow()
                    }

                    CARE_PROFILE_ACTION_LIVESTOCK -> {
                        selectTab(TankDetailTab.TANK_LIFE)

                        val hasLivestock = currentTank
                            ?.livestock
                            ?.isNotEmpty() == true

                        if (!hasLivestock) {
                            openLivestockFormFlow()
                        }
                    }
                }
            }
        }
    }

    private fun openTankSettings() {
        findNavController().navigate(
            R.id.action_tankDetailFragment_to_tankSettingsFragment,
            Bundle().apply {
                putLong("tankId", tankId)
            }
        )
    }

    private fun setupSystemBackButton() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (handleLifeFlowBack()) {
                        return
                    }

                    if (handlePlantFlowBack()) {
                        return
                    }

                    findNavController().navigateUp()
                }
            }
        )
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

            bindTank(tank)
        }
    }

    private fun bindTank(
        tank: SavedAquariumTank
    ) {
        currentTank = tank

        binding.tvTankTitle.text = tank.name

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

    private fun selectTab(
        tab: TankDetailTab
    ) {
        selectedTab = tab

        resetTabs()

        when (tab) {
            TankDetailTab.DEVICES -> {
                activateTab(binding.tabDevices)
                moveTabUnderline(binding.tabDevices)

                binding.contentScrollView.isVisible = false
                binding.devicesFragmentContainer.isVisible = true
                binding.tvEmptyTab.isVisible = false

                showDevicesFragmentIfNeeded()
            }

            TankDetailTab.ACTIVITY -> {
                activateTab(binding.tabActivity)
                moveTabUnderline(binding.tabActivity)

                binding.contentScrollView.isVisible = false
                binding.activityFragmentContainer.isVisible = true
                binding.tvEmptyTab.isVisible = false

                showActivityFragmentIfNeeded()
            }

            TankDetailTab.TANK -> {
                activateTab(binding.tabTank)
                moveTabUnderline(binding.tabTank)

                binding.contentScrollView.isVisible = false
                binding.tankFragmentContainer.isVisible = true
                binding.tvEmptyTab.isVisible = false

                showTankFragmentIfNeeded()
            }

            TankDetailTab.PLANTS -> {
                activateTab(binding.tabPlants)
                moveTabUnderline(binding.tabPlants)

                binding.contentScrollView.isVisible = false
                binding.plantsFragmentContainer.isVisible = true
                binding.tvEmptyTab.isVisible = false

                showPlantsFragmentIfNeeded()
            }

            TankDetailTab.TANK_LIFE -> {
                activateTab(binding.tabTankLife)
                moveTabUnderline(binding.tabTankLife)

                binding.contentScrollView.isVisible = false
                binding.tankLifeFragmentContainer.isVisible = true
                binding.tvEmptyTab.isVisible = false

                showTankLifeFragmentIfNeeded()
            }
        }
    }

    private fun showDevicesFragmentIfNeeded() {
        binding.devicesFragmentContainer.isVisible = true

        val existingFragment = getDevicesFragment()

        if (existingFragment != null) {
            return
        }

        childFragmentManager.commit {
            replace(
                R.id.devicesFragmentContainer,
                TankDetailDevicesFragment.newInstance(tankId),
                TAG_DEVICES_FRAGMENT
            )
        }
    }

    private fun getDevicesFragment(): TankDetailDevicesFragment? {
        return childFragmentManager.findFragmentByTag(
            TAG_DEVICES_FRAGMENT
        ) as? TankDetailDevicesFragment
    }

    private fun showActivityFragmentIfNeeded() {
        binding.activityFragmentContainer.isVisible = true

        val existingFragment = getActivityFragment()

        if (existingFragment != null) {
            return
        }

        childFragmentManager.commit {
            replace(
                R.id.activityFragmentContainer,
                TankDetailActivityFragment.newInstance(tankId),
                TAG_ACTIVITY_FRAGMENT
            )
        }
    }

    private fun getActivityFragment(): TankDetailActivityFragment? {
        return childFragmentManager.findFragmentByTag(
            TAG_ACTIVITY_FRAGMENT
        ) as? TankDetailActivityFragment
    }

    private fun showTankFragmentIfNeeded() {
        binding.tankFragmentContainer.isVisible = true

        val existingFragment = getTankFragment()

        if (existingFragment != null) {
            return
        }

        childFragmentManager.commit {
            replace(
                R.id.tankFragmentContainer,
                TankDetailTankFragment.newInstance(tankId),
                TAG_TANK_FRAGMENT
            )
        }
    }

    private fun getTankFragment(): TankDetailTankFragment? {
        return childFragmentManager.findFragmentByTag(
            TAG_TANK_FRAGMENT
        ) as? TankDetailTankFragment
    }

    private fun showPlantsFragmentIfNeeded() {
        binding.plantsFragmentContainer.isVisible = true

        val existingFragment = getPlantsFragment()

        if (existingFragment != null) {
            return
        }

        childFragmentManager.commit {
            replace(
                R.id.plantsFragmentContainer,
                TankDetailPlantsFragment.newInstance(tankId),
                TAG_PLANTS_FRAGMENT
            )
        }
    }

    private fun getPlantsFragment(): TankDetailPlantsFragment? {
        return childFragmentManager.findFragmentByTag(
            TAG_PLANTS_FRAGMENT
        ) as? TankDetailPlantsFragment
    }

    private fun showTankLifeFragmentIfNeeded() {
        binding.tankLifeFragmentContainer.isVisible = true

        val existingFragment = getTankLifeFragment()

        if (existingFragment != null) {
            return
        }

        childFragmentManager.commit {
            replace(
                R.id.tankLifeFragmentContainer,
                TankDetailLifeFragment.newInstance(tankId),
                TAG_TANK_LIFE_FRAGMENT
            )
        }
    }

    private fun getTankLifeFragment(): TankDetailLifeFragment? {
        return childFragmentManager.findFragmentByTag(
            TAG_TANK_LIFE_FRAGMENT
        ) as? TankDetailLifeFragment
    }

    fun openLivestockFormFlow(
        livestock: SavedAquariumLivestock? = null
    ) {
        binding.lifeFlowContainer.isVisible = true

        childFragmentManager.commit {
            replace(
                R.id.lifeFlowContainer,
                TankDetailLivestockFormFragment.newInstance(
                    tankId = tankId,
                    livestock = livestock
                ),
                TAG_LIVESTOCK_FORM_FRAGMENT
            )
        }
    }

    fun closeLivestockFormFlow() {
        val fragment = childFragmentManager.findFragmentById(
            R.id.lifeFlowContainer
        )

        if (fragment != null) {
            childFragmentManager.commit {
                remove(fragment)
            }
        }

        binding.lifeFlowContainer.isVisible = false
    }

    fun openPlantTagFlow() {
        binding.plantFlowContainer.isVisible = true

        childFragmentManager.commit {
            replace(
                R.id.plantFlowContainer,
                TankDetailPlantTagFragment.newInstance(tankId),
                TAG_PLANT_TAG_FRAGMENT
            )
        }
    }

    fun openPlantPickerFlow() {
        childFragmentManager.commit {
            setReorderingAllowed(true)
            add(
                R.id.plantFlowContainer,
                PlantPickerFragment(),
                TAG_PLANT_PICKER_FRAGMENT
            )
            addToBackStack(TAG_PLANT_PICKER_FRAGMENT)
        }
    }

    fun closePlantTagFlow() {
        childFragmentManager.popBackStack(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        val currentPlantFragment = childFragmentManager.findFragmentById(
            R.id.plantFlowContainer
        )

        if (currentPlantFragment != null) {
            childFragmentManager.commit {
                remove(currentPlantFragment)
            }
        }

        binding.plantFlowContainer.isVisible = false
    }

    private fun handlePlantFlowBack(): Boolean {
        if (!binding.plantFlowContainer.isVisible) {
            return false
        }

        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
        } else {
            closePlantTagFlow()
        }

        return true
    }

    private fun handleLifeFlowBack(): Boolean {
        if (!binding.lifeFlowContainer.isVisible) {
            return false
        }

        closeLivestockFormFlow()
        return true
    }

    private fun activateTab(
        tabView: TextView
    ) {
        tabView.setTextColor(Color.WHITE)
        tabView.setTypeface(null, Typeface.BOLD)
    }

    private fun moveTabUnderline(
        tabView: TextView
    ) {
        binding.tabsContainer.post {
            val underlineWidth = (tabView.width * 0.58f)
                .toInt()
                .coerceIn(
                    34.dp(),
                    68.dp()
                )

            val params = binding.tabUnderline.layoutParams
            params.width = underlineWidth
            binding.tabUnderline.layoutParams = params

            val targetX = tabView.x + ((tabView.width - underlineWidth) / 2f)

            binding.tabUnderline.animate()
                .translationX(targetX)
                .setDuration(180)
                .start()
        }
    }

    private fun resetTabs() {
        val inactiveColor = Color.parseColor("#8FA4BE")

        listOf(
            binding.tabDevices,
            binding.tabActivity,
            binding.tabTank,
            binding.tabPlants,
            binding.tabTankLife
        ).forEach { tab ->
            tab.setTextColor(inactiveColor)
            tab.setTypeface(null, Typeface.NORMAL)
        }

        binding.contentScrollView.isVisible = true
        binding.tankFragmentContainer.isVisible = false
        binding.devicesFragmentContainer.isVisible = false
        binding.activityFragmentContainer.isVisible = false
        binding.plantsFragmentContainer.isVisible = false
        binding.tankLifeFragmentContainer.isVisible = false
        binding.tvEmptyTab.isVisible = false
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(
            KEY_SELECTED_TAB,
            selectedTab.name
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class TankDetailTab {
        DEVICES,
        ACTIVITY,
        TANK,
        PLANTS,
        TANK_LIFE
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"
        private const val KEY_SELECTED_TAB = "selectedTab"

        private const val TAG_DEVICES_FRAGMENT = "TankDetailDevicesFragment"
        private const val TAG_ACTIVITY_FRAGMENT = "TankDetailActivityFragment"
        private const val TAG_TANK_FRAGMENT = "TankDetailTankFragment"
        private const val TAG_PLANTS_FRAGMENT = "TankDetailPlantsFragment"
        private const val TAG_TANK_LIFE_FRAGMENT = "TankDetailLifeFragment"

        private const val TAG_LIVESTOCK_FORM_FRAGMENT = "TankDetailLivestockFormFragment"
        private const val TAG_PLANT_TAG_FRAGMENT = "TankDetailPlantTagFragment"
        private const val TAG_PLANT_PICKER_FRAGMENT = "PlantPickerFragment"

        const val KEY_CARE_PROFILE_ACTION = "care_profile_action"
        const val CARE_PROFILE_ACTION_PLANTS = "plants"
        const val CARE_PROFILE_ACTION_LIVESTOCK = "livestock"
    }
}