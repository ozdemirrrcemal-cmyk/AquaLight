package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryCatalog
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

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
        ?.let {
            tabName ->
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

        binding.cardTankValue.setOnClickListener {
            toggleTankVolumeUnit()
        }

        binding.cardTankDays.setOnClickListener {
            openTankSettings()
        }

        binding.cardTankSize.setOnClickListener {
            openTankSettings()
        }

        binding.cardTankType.setOnClickListener {
            openTankSettings()
        }

        binding.cardTankSetup.setOnClickListener {
            openTankSettings()
        }

        binding.cardTankStyle.setOnClickListener {
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
        ).observe(viewLifecycleOwner) {
            action ->
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

                        val hasLivestock = currentTank?.livestock?.isNotEmpty() == true

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

    private fun openTankSettingsDetails() {
        selectedTab = TankDetailTab.TANK

        findNavController().navigate(
            R.id.action_tankDetailFragment_to_tankSettingsFragment,
            Bundle().apply {
                putLong("tankId", tankId)
                putString("startTab", "details")
            }
        )
    }

    private fun toggleTankVolumeUnit() {
        val tank = currentTank ?: return

        val currentUnit = tank.volumeUnit.ifBlank {
            "L"
        }

        val newUnit = if (currentUnit.equals("gal", ignoreCase = true)) {
            "L"
        } else {
            "gal"
        }

        binding.tvTankVolumeValue.text = getTankVolumeText(
            tank = tank,
            volumeUnit = newUnit
        )

        viewLifecycleOwner.lifecycleScope.launch {
            aquariumTankViewModel.updateTankVolumeUnit(
                tankId = tankId,
                volumeUnit = newUnit
            )
        }
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
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) {
            tanks ->

            maintenanceViewModel.setTanks(tanks)

            val tank = tanks.firstOrNull {
                tank ->
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

        if (selectedTab == TankDetailTab.TANK) {
            renderTankSection(tank)
        }

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

                binding.tankSection.isVisible = true
                binding.tvEmptyTab.isVisible = false

                currentTank?.let {
                    tank ->
                    renderTankSection(tank)
                }
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

    private fun renderTankSection(
        tank: SavedAquariumTank
    ) {
        binding.tvTankDaysValue.text = getTankDaysText(tank.setupDateMillis)

        binding.tvTankVolumeValue.text = getTankVolumeText(
            tank = tank,
            volumeUnit = tank.volumeUnit
        )

        binding.tvTankSizeValue.text = getTankSizeText(tank)

        binding.tvTankTypeValue.text = tank.tankType.ifBlank {
            "-"
        }

        binding.tvTankSetupDateValue.text = getTankSetupDateText(
            tank.setupDateMillis
        )

        binding.tvTankStyleValue.text = tank.tankStyle.ifBlank {
            "-"
        }

        renderTankComponents(tank)
    }

    private fun renderTankComponents(
        tank: SavedAquariumTank
    ) {
        binding.tankBioComponentsContainer.removeAllViews()
        binding.tankHardwareComponentsContainer.removeAllViews()

        MaterialCategoryCatalog.bioCategories.forEach {
            category ->
            val selectedMaterials = tank.materials.filter {
                material ->
                material.categoryKey == category.key
            }

            binding.tankBioComponentsContainer.addView(
                createTankComponentCard(
                    shortCode = category.shortCode,
                    title = category.title,
                    materials = selectedMaterials
                )
            )
        }

        MaterialCategoryCatalog.hardwareCategories.forEach {
            category ->
            val selectedMaterials = tank.materials.filter {
                material ->
                material.categoryKey == category.key
            }

            binding.tankHardwareComponentsContainer.addView(
                createTankComponentCard(
                    shortCode = category.shortCode,
                    title = category.title,
                    materials = selectedMaterials
                )
            )
        }
    }

    private fun createTankComponentCard(
        shortCode: String,
        title: String,
        materials: List<SavedAquariumMaterial>
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = 16.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#10233A"))
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 10.dp()
            layoutParams = params

            setOnClickListener {
                openTankSettingsDetails()
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                14.dp(),
                12.dp(),
                14.dp(),
                12.dp()
            )
        }

        val iconBox = TextView(requireContext()).apply {
            text = shortCode.uppercase(Locale.getDefault())
            gravity = Gravity.CENTER
            textSize = if (shortCode.length > 2) 10f else 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_material_icon_box)
            includeFontPadding = false

            layoutParams = LinearLayout.LayoutParams(
                42.dp(),
                42.dp()
            )
        }

        val textBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginStart = 14.dp()
            layoutParams = params
        }

        val titleText = TextView(requireContext()).apply {
            text = title
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val summaryText = TextView(requireContext()).apply {
            text = getComponentSummary(materials)
            textSize = 12f
            setTextColor(Color.parseColor("#8FA4BE"))
            setLineSpacing(
                2.dp().toFloat(),
                1.0f
            )
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 6.dp()
            layoutParams = params
        }

        textBox.addView(titleText)
        textBox.addView(summaryText)

        row.addView(iconBox)
        row.addView(textBox)

        card.addView(row)

        return card
    }

    private fun getComponentSummary(
        materials: List<SavedAquariumMaterial>
    ): String {
        if (materials.isEmpty()) {
            return "Not selected"
        }

        if (materials.size == 1) {
            return materials.first().name
        }

        return "${materials.first().name} +${materials.size - 1} more"
    }

    private fun getTankDaysText(
        setupDateMillis: Long?
    ): String {
        if (setupDateMillis == null) {
            return "-"
        }

        val day = TimeUnit.MILLISECONDS
        .toDays(System.currentTimeMillis() - setupDateMillis)
        .coerceAtLeast(0)

        return "$day days"
    }

    private fun getTankVolumeText(
        tank: SavedAquariumTank,
        volumeUnit: String
    ): String {
        val liter = (tank.widthCm * tank.lengthCm * tank.heightCm) / 1000.0

        return if (volumeUnit.equals("gal", ignoreCase = true)) {
            val gallon = liter * 0.264172
            "${gallon.roundToInt()} gal"
        } else {
            "${liter.roundToInt()} L"
        }
    }

    private fun getTankSizeText(
        tank: SavedAquariumTank
    ): String {
        return "${tank.widthCm}×${tank.lengthCm}×${tank.heightCm}"
    }

    private fun getTankSetupDateText(
        setupDateMillis: Long?
    ): String {
        if (setupDateMillis == null) {
            return "-"
        }

        val formatter = SimpleDateFormat(
            "dd MMM yy",
            Locale.getDefault()
        )

        return formatter.format(Date(setupDateMillis))
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
                "TANK_DETAIL_LIVESTOCK_FORM_FRAGMENT"
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
                "TANK_DETAIL_PLANT_TAG_FRAGMENT"
            )
        }
    }

    fun openPlantPickerFlow() {
        childFragmentManager.commit {
            setReorderingAllowed(true)
            add(
                R.id.plantFlowContainer,
                PlantPickerFragment(),
                "PLANT_PICKER_FRAGMENT"
            )
            addToBackStack("PLANT_PICKER_FRAGMENT")
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
    binding.devicesFragmentContainer.isVisible = false
    binding.activityFragmentContainer.isVisible = false
    binding.plantsFragmentContainer.isVisible = false
    binding.tankLifeFragmentContainer.isVisible = false

    binding.tankSection.isVisible = false
    binding.tvEmptyTab.isVisible = false
}

private fun showEmptySection() {
    binding.contentScrollView.isVisible = true
    binding.devicesFragmentContainer.isVisible = false
    binding.activityFragmentContainer.isVisible = false
    binding.plantsFragmentContainer.isVisible = false
    binding.tankLifeFragmentContainer.isVisible = false

    binding.tankSection.isVisible = false
    binding.tvEmptyTab.isVisible = true
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

        private const val TAG_PLANTS_FRAGMENT = "TankDetailPlantsFragment"
        
        private const val TAG_TANK_LIFE_FRAGMENT = "TankDetailLifeFragment"

        const val KEY_CARE_PROFILE_ACTION = "care_profile_action"
        const val CARE_PROFILE_ACTION_PLANTS = "plants"
        const val CARE_PROFILE_ACTION_LIVESTOCK = "livestock"
    }
}