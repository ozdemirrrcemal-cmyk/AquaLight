package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.aqua.aqualight.ui.tabs.aquarium.model.LivestockCategories
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumPlant
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

        binding.btnAddDevice.setOnClickListener {
            showAddDeviceBottomSheet()
        }

        binding.btnAddPlant.setOnClickListener {
            openPlantTagFlow()
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

        binding.btnAddLife.setOnClickListener {
            openLivestockFormFlow()
        }

        binding.btnEmptyAddLife.setOnClickListener {
            openLivestockFormFlow()
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

        if (selectedTab == TankDetailTab.PLANTS) {
            renderPlantsSection(tank)
        }

        if (selectedTab == TankDetailTab.TANK) {
            renderTankSection(tank)
        }

        if (selectedTab == TankDetailTab.TANK_LIFE) {
            renderLivestockSection(tank)
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

                binding.plantsSection.isVisible = true
                binding.tvEmptyTab.isVisible = false

                currentTank?.let {
                    tank ->
                    renderPlantsSection(tank)
                }
            }

            TankDetailTab.TANK_LIFE -> {
                activateTab(binding.tabTankLife)
                moveTabUnderline(binding.tabTankLife)

                binding.tankLifeSection.isVisible = true
                binding.tvEmptyTab.isVisible = false

                currentTank?.let {
                    tank ->
                    renderLivestockSection(tank)
                }
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

    private fun renderLivestockSection(
        tank: SavedAquariumTank
    ) {
        val livestock = tank.livestock

        binding.tankLifeListContainer.removeAllViews()

        val totalQuantity = livestock.sumOf {
            item ->
            item.quantity.coerceAtLeast(1)
        }

        binding.tvTankLifeSummary.text = if (livestock.isEmpty()) {
            "No livestock yet"
        } else {
            "${livestock.size} species • $totalQuantity total livestock"
        }

        binding.cardTankLifeEmpty.isVisible = livestock.isEmpty()
        binding.tankLifeListContainer.isVisible = livestock.isNotEmpty()

        livestock.forEach {
            item ->
            binding.tankLifeListContainer.addView(
                createLivestockCard(item)
            )
        }
    }

    private fun createLivestockCard(
        livestock: SavedAquariumLivestock
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = 18.dp().toFloat()
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
            params.bottomMargin = 12.dp()
            layoutParams = params

            setOnClickListener {
                openLivestockFormFlow(livestock)
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                14.dp(),
                12.dp(),
                12.dp(),
                12.dp()
            )
        }

        val iconBox = ImageView(requireContext()).apply {
            setImageResource(getLivestockCategoryIcon(livestock.category))
            setColorFilter(Color.WHITE)
            background = createLifeIconBackground(
                color = getLivestockCategoryColor(livestock.category)
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = livestock.category.ifBlank {
                "Livestock"
            }

            layoutParams = LinearLayout.LayoutParams(
                46.dp(),
                46.dp()
            )

            setPadding(
                10.dp(),
                10.dp(),
                10.dp(),
                10.dp()
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
            params.marginEnd = 10.dp()
            layoutParams = params
        }

        val nameText = TextView(requireContext()).apply {
            text = livestock.name.ifBlank {
                "Unnamed livestock"
            }
            textSize = 14.5f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val metaText = TextView(requireContext()).apply {
            text = "${livestock.category.ifBlank { "Other" }} • ${getLivestockQuantityText(livestock.quantity)}"
            textSize = 12.5f
            setTextColor(Color.parseColor("#8FA4BE"))
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 7.dp()
            layoutParams = params
        }

        val dateText = TextView(requireContext()).apply {
            text = getLivestockAddedDateText(livestock.addedDateMillis)
            textSize = 12f
            setTextColor(Color.parseColor("#5FD6B4"))
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 7.dp()
            layoutParams = params
        }

        textBox.addView(nameText)
        textBox.addView(metaText)
        textBox.addView(dateText)

        if (livestock.note.isNotBlank()) {
            val noteText = TextView(requireContext()).apply {
                text = livestock.note
                textSize = 12f
                setTextColor(Color.parseColor("#8FA4BE"))
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = 7.dp()
                layoutParams = params
            }

            textBox.addView(noteText)
        }

        val arrow = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_arrow_right)
            setColorFilter(Color.parseColor("#8FA4BE"))
            scaleType = ImageView.ScaleType.CENTER

            layoutParams = LinearLayout.LayoutParams(
                22.dp(),
                22.dp()
            )
        }

        row.addView(iconBox)
        row.addView(textBox)
        row.addView(arrow)

        card.addView(row)

        return card
    }

    private fun openLivestockFormFlow(
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

    private fun renderPlantsSection(
        tank: SavedAquariumTank
    ) {
        binding.plantListContainer.removeAllViews()

        tank.plants.forEachIndexed {
            index, plant ->
            binding.plantListContainer.addView(
                createPlantCard(
                    index = index,
                    plant = plant
                )
            )
        }
    }

    private fun createPlantCard(
        index: Int,
        plant: SavedAquariumPlant
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
                14.dp(),
                12.dp()
            )
        }

        val number = TextView(requireContext()).apply {
            text = "${index + 1}"
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.NORMAL)
            setBackgroundResource(R.drawable.bg_plant_number_circle)
            includeFontPadding = false

            layoutParams = LinearLayout.LayoutParams(
                38.dp(),
                38.dp()
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

        val categoryText = TextView(requireContext()).apply {
            text = plant.category
            textSize = 12f
            setTextColor(Color.parseColor("#8FA4BE"))
            includeFontPadding = false
        }

        val nameText = TextView(requireContext()).apply {
            text = plant.plantName
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 6.dp()
            layoutParams = params
        }

        textBox.addView(categoryText)
        textBox.addView(nameText)

        row.addView(number)
        row.addView(textBox)

        card.addView(row)

        return card
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
        ).forEach {
            tab ->
            tab.setTextColor(inactiveColor)
            tab.setTypeface(null, Typeface.NORMAL)
        }

        binding.contentScrollView.isVisible = true
        binding.devicesFragmentContainer.isVisible = false
        binding.activityFragmentContainer.isVisible = false

        binding.devicesSection.isVisible = false
        binding.tankSection.isVisible = false
        binding.plantsSection.isVisible = false
        binding.tankLifeSection.isVisible = false
        binding.tvEmptyTab.isVisible = false
    }

    private fun showEmptySection() {
        binding.contentScrollView.isVisible = true
        binding.devicesFragmentContainer.isVisible = false
        binding.activityFragmentContainer.isVisible = false

        binding.devicesSection.isVisible = false
        binding.tankSection.isVisible = false
        binding.plantsSection.isVisible = false
        binding.tankLifeSection.isVisible = false
        binding.tvEmptyTab.isVisible = true
    }

    private fun getLivestockQuantityText(
        quantity: Int
    ): String {
        val safeQuantity = quantity.coerceAtLeast(1)

        return if (safeQuantity == 1) {
            "1 pc"
        } else {
            "$safeQuantity pcs"
        }
    }

    private fun getLivestockAddedDateText(
        addedDateMillis: Long?
    ): String {
        if (addedDateMillis == null || addedDateMillis <= 0L) {
            return "Added date not set"
        }

        val formatter = SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        )

        return "Added ${formatter.format(Date(addedDateMillis))}"
    }

    private fun getLivestockCategoryIcon(
        category: String
    ): Int {
        return when (category) {
            LivestockCategories.FISH -> R.drawable.ic_life_fish_24
            LivestockCategories.SHRIMP -> R.drawable.ic_life_shrimp_24
            LivestockCategories.SNAIL -> R.drawable.ic_life_snail_24
            LivestockCategories.CRAB_CRAYFISH -> R.drawable.ic_life_crab_24
            LivestockCategories.CORAL -> R.drawable.ic_life_coral_24
            else -> R.drawable.ic_life_other_24
        }
    }

    private fun getLivestockCategoryColor(
        category: String
    ): String {
        return when (category) {
            LivestockCategories.FISH -> "#1C5D8F"
            LivestockCategories.SHRIMP -> "#8F4A3A"
            LivestockCategories.SNAIL -> "#3E6B4A"
            LivestockCategories.CRAB_CRAYFISH -> "#7A4D2D"
            LivestockCategories.CORAL -> "#7A4E8F"
            else -> "#3E536B"
        }
    }

    private fun createLifeIconBackground(
        color: String
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(color))
            cornerRadius = 16.dp().toFloat()
        }
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

        const val KEY_CARE_PROFILE_ACTION = "care_profile_action"
        const val CARE_PROFILE_ACTION_PLANTS = "plants"
        const val CARE_PROFILE_ACTION_LIVESTOCK = "livestock"
    }
}