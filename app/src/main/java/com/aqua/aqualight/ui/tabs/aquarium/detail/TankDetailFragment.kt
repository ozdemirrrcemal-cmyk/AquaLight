package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.navigation.fragment.findNavController
import android.widget.ImageView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryCatalog
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumPlant
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.res.ColorStateList
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.first
import android.view.LayoutInflater

class TankDetailFragment : Fragment(R.layout.fragment_tank_detail) {

  private var _binding: FragmentTankDetailBinding? = null
  private val binding get() = _binding!!

  private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

  private lateinit var userPrefs: UserPreferencesManager
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
    userPrefs = UserPreferencesManager.create(requireContext())
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
    observeTank()
    observeTankDevices()
    selectTab(selectedTab)
  }

  private fun setupClickListeners() {
    binding.btnBack.setOnClickListener {
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

    binding.btnAddActivity.setOnClickListener {
      Toast.makeText(
        requireContext(),
        "Add activity will be connected later.",
        Toast.LENGTH_SHORT
      ).show()
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
      val tank = tanks.firstOrNull {
        it.id == tankId
      }

      if (tank == null) {
        Toast.makeText(
          requireContext(),
          "Tank not found.",
          Toast.LENGTH_SHORT
        ).show()

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
  }

  private fun observeTankDevices() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        userPrefs.devicesForTankFlow(tankId).collect {
          devices ->
          renderDevicesSection(devices)
        }
      }
    }
  }

  private fun renderDevicesSection(
    devices: List<UserPreferencesManager.DeviceInfoUi>
  ) {
    binding.tankDevicesContainer.removeAllViews()

    binding.cardDevicesEmpty.isVisible = devices.isEmpty()
    binding.tankDevicesContainer.isVisible = devices.isNotEmpty()

    devices.forEach {
      device ->
      binding.tankDevicesContainer.addView(
        createAssignedDeviceCard(device)
      )
    }
  }

  private fun createAssignedDeviceCard(
    device: UserPreferencesManager.DeviceInfoUi
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
        12.dp(),
        12.dp()
      )
    }

    val iconBox = TextView(requireContext()).apply {
      text = createDeviceShortCode(device)
      gravity = Gravity.CENTER
      textSize = 12f
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
      params.marginEnd = 10.dp()
      layoutParams = params
    }

    val titleText = TextView(requireContext()).apply {
      text = getDeviceTitle(device)
      textSize = 14f
      setTextColor(Color.WHITE)
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }

    val typeText = TextView(requireContext()).apply {
      text = getDeviceTypeText(device)
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

    val statusText = TextView(requireContext()).apply {
      val online = isDeviceOnline(device)

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
      setImageResource(R.drawable.ic_close_20)
      setColorFilter(Color.parseColor("#8FA4BE"))
      setBackgroundResource(R.drawable.bg_device_remove_icon_circle)

      scaleType = ImageView.ScaleType.CENTER
      isClickable = true
      isFocusable = true
      contentDescription = "Remove device"

      layoutParams = LinearLayout.LayoutParams(
        40.dp(),
        40.dp()
      )

      setPadding(
        10.dp(),
        10.dp(),
        10.dp(),
        10.dp()
      )

      setOnClickListener {
        showRemoveDeviceConfirmationDialog(device)
      }
    }

    row.addView(iconBox)
    row.addView(textBox)
    row.addView(removeButton)

    card.addView(row)

    return card
  }

  private fun showAddDeviceBottomSheet() {
    viewLifecycleOwner.lifecycleScope.launch {
      val allDevices = userPrefs.devicesFlow.first()

      val availableDevices = allDevices.filter {
        device ->
        device.tankId == null
      }

      showAvailableDevicesBottomSheet(
        devices = availableDevices,
        hasAnySavedDevice = allDevices.isNotEmpty()
      )
    }
  }

  private fun showAvailableDevicesBottomSheet(
    devices: List<UserPreferencesManager.DeviceInfoUi>,
    hasAnySavedDevice: Boolean
  ) {
    val dialog = BottomSheetDialog(requireContext())

    val contentView = LayoutInflater.from(requireContext()).inflate(
      R.layout.bottom_sheet_add_device,
      null,
      false
    )

    val titleText = contentView.findViewById<TextView>(
      R.id.tvAddDeviceBottomTitle
    )

    val messageText = contentView.findViewById<TextView>(
      R.id.tvAddDeviceBottomMessage
    )

    val devicesContainer = contentView.findViewById<LinearLayout>(
      R.id.availableDevicesContainer
    )

    val emptyContainer = contentView.findViewById<LinearLayout>(
      R.id.emptyAddDeviceContainer
    )

    val emptyTitle = contentView.findViewById<TextView>(
      R.id.tvAddDeviceEmptyTitle
    )

    val emptyMessage = contentView.findViewById<TextView>(
      R.id.tvAddDeviceEmptyMessage
    )

    val scanButton = contentView.findViewById<MaterialButton>(
      R.id.btnOpenDeviceScan
    )

    titleText.text = currentTank?.name?.let {
      tankName ->
      "Add Device to $tankName"
    } ?: "Add Device"

    devicesContainer.removeAllViews()

    if (devices.isNotEmpty()) {
      messageText.text = "Select a saved device to connect it to this aquarium."

      devicesContainer.isVisible = true
      emptyContainer.isVisible = false

      devices.forEach {
        device ->
        devicesContainer.addView(
          createAvailableDeviceCard(
            device = device,
            dialog = dialog
          )
        )
      }
    } else {
      devicesContainer.isVisible = false
      emptyContainer.isVisible = true

      if (hasAnySavedDevice) {
        messageText.text = "All saved devices are already connected to another aquarium."
        emptyTitle.text = "No available devices"
        emptyMessage.text = "Remove a device from another aquarium or manage your saved devices."
        scanButton.text = "Manage Devices"
      } else {
        messageText.text = "No saved devices found."
        emptyTitle.text = "No saved devices"
        emptyMessage.text = "Scan and save a device first, then connect it to this aquarium."
        scanButton.text = "Scan Devices"
      }

      scanButton.setOnClickListener {
        dialog.dismiss()
        openDeviceScanScreen()
      }
    }

    dialog.setContentView(contentView)

    dialog.setOnShowListener {
      val bottomSheet = dialog.findViewById<View>(
        com.google.android.material.R.id.design_bottom_sheet
      )

      bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
    }

    dialog.show()
  }

  private fun openDeviceScanScreen() {
    runCatching {
      findNavController().navigate(
        R.id.scanDevicesFragment
      )
    }.onFailure {
      runCatching {
        findNavController().navigate(
          R.id.devicesFragment
        )
      }
    }
  }

  private fun createAvailableDeviceCard(
    device: UserPreferencesManager.DeviceInfoUi,
    dialog: BottomSheetDialog
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
      params.bottomMargin = 10.dp()
      layoutParams = params

      setOnClickListener {
        assignDeviceToCurrentTank(
          device = device,
          dialog = dialog
        )
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
      text = createDeviceShortCode(device)
      gravity = Gravity.CENTER
      textSize = 12f
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
      text = getDeviceTitle(device)
      textSize = 14f
      setTextColor(Color.WHITE)
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }

    val infoText = TextView(requireContext()).apply {
      text = getDeviceInfoText(device)
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

    textBox.addView(titleText)
    textBox.addView(infoText)

    row.addView(iconBox)
    row.addView(textBox)

    card.addView(row)

    return card
  }

  private fun assignDeviceToCurrentTank(
    device: UserPreferencesManager.DeviceInfoUi,
    dialog: BottomSheetDialog
  ) {
    viewLifecycleOwner.lifecycleScope.launch {
      userPrefs.assignDeviceToTank(
        deviceId = device.id,
        tankId = tankId
      )

      dialog.dismiss()
    }
  }

  private fun showRemoveDeviceConfirmationDialog(
    device: UserPreferencesManager.DeviceInfoUi
  ) {
    DialogManager.showConfirmDialog(
      context = requireContext(),
      type = DialogType.WARNING,
      title = "Remove Device?",
      message = "\"${getDeviceTitle(device)}\" will be removed from this tank. The device will stay saved in Devices.",
      confirmTextResId = R.string.confirm,
      cancelTextResId = R.string.cancel,
      onConfirm = {
        removeDeviceFromCurrentTank(device)
      }
    )
  }

  private fun removeDeviceFromCurrentTank(
    device: UserPreferencesManager.DeviceInfoUi
  ) {
    viewLifecycleOwner.lifecycleScope.launch {
      userPrefs.removeDeviceFromTank(
        deviceId = device.id
      )
    }
  }

  private fun getDeviceTitle(
    device: UserPreferencesManager.DeviceInfoUi
  ): String {
    return device.name.ifBlank {
      device.aquaName.ifBlank {
        "Device"
      }
    }
  }

  private fun getDeviceTypeText(
    device: UserPreferencesManager.DeviceInfoUi
  ): String {
    return device.aquaName.ifBlank {
      "Device"
    }
  }

  private fun getDeviceInfoText(
    device: UserPreferencesManager.DeviceInfoUi
  ): String {
    val typeText = device.aquaName.ifBlank {
      "AquaLight Device"
    }

    return if (device.ip.isBlank()) {
      typeText
    } else {
      "$typeText • ${device.ip}"
    }
  }

  private fun createDeviceShortCode(
    device: UserPreferencesManager.DeviceInfoUi
  ): String {
    val source = device.aquaName.ifBlank {
      device.name.ifBlank {
        "DV"
      }
    }

    val words = source
    .trim()
    .split(Regex("\\s+"))
    .filter {
      word ->
      word.isNotBlank()
    }

    if (words.size >= 2) {
      return "${words[0].first()}${words[1].first()}"
      .uppercase(Locale.getDefault())
    }

    return source
    .take(2)
    .uppercase(Locale.getDefault())
  }

  private fun isDeviceOnline(
    device: UserPreferencesManager.DeviceInfoUi
  ): Boolean {
    if (device.lastSeenMillis <= 0L) {
      return false
    }

    return System.currentTimeMillis() - device.lastSeenMillis <= ONLINE_TIMEOUT_MS
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

        binding.devicesSection.isVisible = true
        binding.tvEmptyTab.isVisible = false
      }

      TankDetailTab.ACTIVITY -> {
        activateTab(binding.tabActivity)
        moveTabUnderline(binding.tabActivity)

        binding.activitySection.isVisible = true
        binding.tvEmptyTab.isVisible = false
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

        showEmptySection()
      }
    }
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

    binding.devicesSection.isVisible = false
    binding.activitySection.isVisible = false
    binding.tankSection.isVisible = false
    binding.plantsSection.isVisible = false
    binding.tvEmptyTab.isVisible = false
  }

  private fun showEmptySection() {
    binding.devicesSection.isVisible = false
    binding.activitySection.isVisible = false
    binding.tankSection.isVisible = false
    binding.plantsSection.isVisible = false
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
    private const val ONLINE_TIMEOUT_MS = 60_000L
  }
}