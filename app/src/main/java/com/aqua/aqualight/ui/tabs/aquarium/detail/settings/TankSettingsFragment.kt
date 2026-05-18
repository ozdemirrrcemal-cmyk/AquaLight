package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentTankSettingsBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.common.TankStyleBottomSheet
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryCatalog
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.ui.tabs.aquarium.export.TankPdfExporter
import kotlinx.coroutines.Dispatchers
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class TankSettingsFragment : Fragment(R.layout.fragment_tank_settings),
MaterialPickerFragment.MaterialPickerHost {

  private var _binding: FragmentTankSettingsBinding? = null
  private val binding get() = _binding!!

  private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
  private lateinit var userPrefs: UserPreferencesManager

  private var tankId: Long = 0L
  private var selectedTab: SettingsTab = SettingsTab.BASIC
  private var currentTank: SavedAquariumTank? = null
  private var pendingCameraUri: Uri? = null
  private var isDeletingTank: Boolean = false
  private var isDuplicatingTank: Boolean = false
  private var isExportingTank: Boolean = false

  private val galleryLauncher = registerForActivityResult(
    ActivityResultContracts.GetContent()
  ) {
    uri ->
    if (uri != null) {
      startImageCrop(uri)
    }
  }

  private val cameraLauncher = registerForActivityResult(
    ActivityResultContracts.TakePicture()
  ) {
    success ->
    if (success) {
      pendingCameraUri?.let {
        uri ->
        startImageCrop(uri)
      }
    }
  }

  private val cropLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) {
    result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val outputUri = result.data?.let {
        intent ->
        UCrop.getOutput(intent)
      }

      if (outputUri != null) {
        saveTankPhoto(outputUri)
      }
    } else if (result.resultCode == UCrop.RESULT_ERROR) {
      val error = result.data?.let {
        intent ->
        UCrop.getError(intent)
      }

      showSnackBar(
        message = error?.message ?: "Photo could not be cropped.",
        type = BaseActivity.SnackType.ERROR
      )
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

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

    _binding = FragmentTankSettingsBinding.bind(view)
    userPrefs = UserPreferencesManager.create(requireContext())

    setupClickListeners()
    setupSystemBackButton()
    setupSwipeBetweenTabs()
    observeTank()
    selectTab(getInitialTab())
  }

  private fun setupClickListeners() {
    binding.btnBack.setOnClickListener {
      if (handleMaterialPickerBack()) {
        return@setOnClickListener
      }

      findNavController().navigateUp()
    }
    binding.tabBasic.setOnClickListener {
      selectTab(SettingsTab.BASIC)
    }

    binding.tabDetails.setOnClickListener {
      selectTab(SettingsTab.DETAILS)
    }

    binding.tabOthers.setOnClickListener {
      selectTab(SettingsTab.OTHERS)
    }

    binding.btnChangePhoto.setOnClickListener {
      showPhotoSourceSheet()
    }

    binding.rowTankName.setOnClickListener {
      showTankNameSheet()
    }

    binding.rowTankType.setOnClickListener {
      showTankTypeSheet()
    }

    binding.rowSize.setOnClickListener {
      showTankSizeSheet()
    }

    binding.rowVolume.setOnClickListener {
      showVolumeUnitSheet()
    }

    binding.rowSetupDate.setOnClickListener {
      showSetupDateSheet()
    }

    binding.rowStyle.setOnClickListener {
      showStyleSheet()
    }

    binding.rowIdea.setOnClickListener {
      showIdeaSheet()
    }

    binding.rowDuplicateTank.setOnClickListener {
      showDuplicateTankConfirmationDialog()
    }

    binding.rowExportTankData.setOnClickListener {
      exportTankDataAsPdf()
    }

    binding.rowDeleteTank.setOnClickListener {
      showDeleteTankConfirmationDialog()
    }
  }

  private fun setupSystemBackButton() {
    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (handleMaterialPickerBack()) {
            return
          }

          findNavController().navigateUp()
        }
      }
    )
  }

  private fun handleMaterialPickerBack(): Boolean {
    if (!binding.settingsMaterialPickerContainer.isVisible) {
      return false
    }

    closeMaterialPickerFlow()
    return true
  }

  private fun observeTank() {
    aquariumTankViewModel.tanks.observe(viewLifecycleOwner) {
      tanks ->
      val tank = tanks.firstOrNull {
        savedTank ->
        savedTank.id == tankId
      }

      if (tank == null) {
        if (isDeletingTank) {
          return@observe
        }

        DialogManager.showInfoDialog(
          context = requireContext(),
          type = DialogType.ERROR,
          title = "Tank Not Found",
          message = "This tank no longer exists.",
          onDismiss = {
            findNavController().navigateUp()
          }
        )

        return@observe
      }

      bindTank(tank)
    }
  }

  private fun bindTank(
    tank: SavedAquariumTank
  ) {
    currentTank = tank

    if (!tank.photoUri.isNullOrBlank()) {
      binding.imgTankPhoto.load(Uri.parse(tank.photoUri)) {
        placeholder(R.drawable.nature_aquarium)
        error(R.drawable.nature_aquarium)
        crossfade(true)
      }
    } else {
      binding.imgTankPhoto.setImageResource(R.drawable.nature_aquarium)
    }

    binding.tvSettingTankName.text = tank.name

    binding.tvSettingTankType.text = tank.tankType.ifBlank {
      "-"
    }

    binding.tvSettingSize.text = getSizeText(tank)
    binding.tvSettingVolume.text = getVolumeText(tank)
    binding.tvSettingSetupDate.text = getSetupDateText(tank.setupDateMillis)

    binding.tvSettingStyle.text = tank.tankStyle.ifBlank {
      "-"
    }

    binding.tvSettingIdea.text = tank.description.ifBlank {
      "No idea added"
    }

    renderMaterials(tank)
  }

  private fun showSnackBar(
    message: String,
    type: BaseActivity.SnackType = BaseActivity.SnackType.NORMAL
  ) {
    (activity as? BaseActivity)?.showSnackBar(
      message = message,
      type = type
    )
  }

  private fun showPhotoSourceSheet() {
    val dialog = BottomSheetDialog(requireContext())

    val container = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(
        18.dp(),
        18.dp(),
        18.dp(),
        20.dp()
      )
      background = createTopRoundedDrawable(
        color = "#10233A",
        radiusPx = 24.dp()
      )
    }

    val title = TextView(requireContext()).apply {
      text = "Aquarium photo"
      textSize = 16f
      setTextColor(Color.WHITE)
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false
    }

    val cameraRow = createPhotoSourceRow(
      title = "Camera",
      subtitle = "Take a new aquarium photo"
    ) {
      dialog.dismiss()
      openCamera()
    }

    val galleryRow = createPhotoSourceRow(
      title = "Gallery",
      subtitle = "Choose from your gallery"
    ) {
      dialog.dismiss()
      openGallery()
    }

    container.addView(title)
    container.addView(cameraRow)
    container.addView(galleryRow)

    showConfiguredBottomSheet(
      dialog = dialog,
      content = container
    )
  }

  private fun createPhotoSourceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
  ): View {
    val row = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(
        14.dp(),
        11.dp(),
        14.dp(),
        11.dp()
      )
      background = createRoundedDrawable(
        color = "#16314D",
        radiusPx = 14.dp()
      )

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      params.topMargin = 10.dp()
      layoutParams = params

      setOnClickListener {
        onClick()
      }
    }

    val titleText = TextView(requireContext()).apply {
      text = title
      textSize = 14f
      setTextColor(Color.WHITE)
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false
    }

    val subtitleText = TextView(requireContext()).apply {
      text = subtitle
      textSize = 12f
      setTextColor(Color.parseColor("#8FA4BE"))
      includeFontPadding = false

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      params.topMargin = 5.dp()
      layoutParams = params
    }

    row.addView(titleText)
    row.addView(subtitleText)

    return row
  }

  private fun openGallery() {
    galleryLauncher.launch("image/*")
  }

  private fun openCamera() {
    val cameraUri = createTankPhotoUri()
    pendingCameraUri = cameraUri
    cameraLauncher.launch(cameraUri)
  }

  private fun startImageCrop(
    sourceUri: Uri
  ) {
    val destinationUri = createTankPhotoCropUri()

    val options = UCrop.Options().apply {
      setToolbarTitle("Crop aquarium photo")
      setToolbarColor(Color.parseColor("#081B31"))
      setToolbarWidgetColor(Color.WHITE)
      setRootViewBackgroundColor(Color.parseColor("#081B31"))
      setActiveControlsWidgetColor(Color.parseColor("#2196F3"))
      setCompressionQuality(90)
      setFreeStyleCropEnabled(false)
      setHideBottomControls(true)
    }

    val cropIntent = UCrop.of(
      sourceUri,
      destinationUri
    )
    .withAspectRatio(
      16f,
      9f
    )
    .withMaxResultSize(
      1600,
      900
    )
    .withOptions(options)
    .getIntent(requireContext())
    .apply {
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    cropLauncher.launch(cropIntent)
  }

  private fun saveTankPhoto(
    photoUri: Uri
  ) {
    binding.imgTankPhoto.load(photoUri) {
      placeholder(R.drawable.nature_aquarium)
      error(R.drawable.nature_aquarium)
      crossfade(true)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      try {
        aquariumTankViewModel.updateTankPhoto(
          tankId = tankId,
          photoUri = photoUri.toString()
        )

        showSnackBar(
          message = "Photo updated.",
          type = BaseActivity.SnackType.SUCCESS
        )
      } catch (exception: Exception) {
        exception.printStackTrace()

        showSnackBar(
          message = "Photo could not be saved.",
          type = BaseActivity.SnackType.ERROR
        )
      }
    }
  }

  private fun createTankPhotoUri(): Uri {
    val directory = File(
      requireContext().filesDir,
      "tank_photos"
    )

    if (!directory.exists()) {
      directory.mkdirs()
    }

    val file = File(
      directory,
      "tank_camera_${tankId}_${System.currentTimeMillis()}.jpg"
    )

    return FileProvider.getUriForFile(
      requireContext(),
      "${requireContext().packageName}.fileprovider",
      file
    )
  }

  private fun createTankPhotoCropUri(): Uri {
    val directory = File(
      requireContext().filesDir,
      "tank_photos"
    )

    if (!directory.exists()) {
      directory.mkdirs()
    }

    val file = File(
      directory,
      "tank_crop_${tankId}_${System.currentTimeMillis()}.jpg"
    )

    return Uri.fromFile(file)
  }

  private fun selectTab(
    tab: SettingsTab
  ) {
    selectedTab = tab

    resetTabs()

    when (tab) {
      SettingsTab.BASIC -> {
        activateTab(binding.tabBasic)
        moveTabUnderline(binding.tabBasic)
        binding.basicSection.isVisible = true
      }

      SettingsTab.DETAILS -> {
        activateTab(binding.tabDetails)
        moveTabUnderline(binding.tabDetails)
        binding.detailsSection.isVisible = true
      }

      SettingsTab.OTHERS -> {
        activateTab(binding.tabOthers)
        moveTabUnderline(binding.tabOthers)
        binding.othersSection.isVisible = true
      }
    }

    binding.contentScrollView.post {
      binding.contentScrollView.scrollTo(
        0,
        0
      )
    }
  }

  private fun getInitialTab(): SettingsTab {
    val startTab = arguments?.getString("startTab")

    return when (startTab) {
      "details" -> SettingsTab.DETAILS
      "others" -> SettingsTab.OTHERS
      else -> SettingsTab.BASIC
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun setupSwipeBetweenTabs() {
    val gestureDetector = GestureDetector(
      requireContext(),
      object : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(
          e: MotionEvent
        ): Boolean {
          return true
        }

        override fun onFling(
          e1: MotionEvent?,
          e2: MotionEvent,
          velocityX: Float,
          velocityY: Float
        ): Boolean {
          if (e1 == null) {
            return false
          }

          if (binding.settingsMaterialPickerContainer.isVisible) {
            return false
          }

          val diffX = e2.x - e1.x
          val diffY = e2.y - e1.y

          val isHorizontalSwipe = abs(diffX) > abs(diffY)
          val hasEnoughDistance = abs(diffX) > 70.dp()
          val hasEnoughVelocity = abs(velocityX) > 500

          if (!isHorizontalSwipe || !hasEnoughDistance || !hasEnoughVelocity) {
            return false
          }

          if (diffX < 0) {
            moveToNextTab()
          } else {
            moveToPreviousTab()
          }

          return true
        }
      }
    )

    val swipeTouchListener = View.OnTouchListener {
      _, event ->
      gestureDetector.onTouchEvent(event)
      false
    }

    binding.contentScrollView.setOnTouchListener(swipeTouchListener)
    binding.settingsTabsContainer.setOnTouchListener(swipeTouchListener)
  }

  private fun moveToNextTab() {
    when (selectedTab) {
      SettingsTab.BASIC -> {
        selectTab(SettingsTab.DETAILS)
      }

      SettingsTab.DETAILS -> {
        selectTab(SettingsTab.OTHERS)
      }

      SettingsTab.OTHERS -> Unit
    }
  }

  private fun moveToPreviousTab() {
    when (selectedTab) {
      SettingsTab.BASIC -> Unit

      SettingsTab.DETAILS -> {
        selectTab(SettingsTab.BASIC)
      }

      SettingsTab.OTHERS -> {
        selectTab(SettingsTab.DETAILS)
      }
    }
  }

  private fun resetTabs() {
    val inactiveColor = Color.parseColor("#8FA4BE")

    listOf(
      binding.tabBasic,
      binding.tabDetails,
      binding.tabOthers
    ).forEach {
      tab ->
      tab.setTextColor(inactiveColor)
      tab.setTypeface(null, Typeface.NORMAL)
    }

    binding.basicSection.isVisible = false
    binding.detailsSection.isVisible = false
    binding.othersSection.isVisible = false
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
    binding.settingsTabsContainer.post {
      val textWidth = tabView.paint
      .measureText(tabView.text.toString())
      .toInt()

      val underlineWidth = (textWidth * 0.90f)
      .toInt()
      .coerceIn(
        36.dp(),
        72.dp()
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

  private fun renderMaterials(
    tank: SavedAquariumTank
  ) {
    binding.bioMaterialsContainer.removeAllViews()
    binding.hardwareMaterialsContainer.removeAllViews()

    MaterialCategoryCatalog.bioCategories.forEach {
      category ->
      val selectedMaterials = tank.materials.filter {
        material ->
        material.categoryKey == category.key
      }

      binding.bioMaterialsContainer.addView(
        createMaterialCard(
          categoryKey = category.key,
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

      binding.hardwareMaterialsContainer.addView(
        createMaterialCard(
          categoryKey = category.key,
          title = category.title,
          materials = selectedMaterials
        )
      )
    }
  }

  private fun createMaterialCard(
    categoryKey: String,
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

    val iconBackground = GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      setColor(Color.parseColor("#263B5A"))
      cornerRadius = 12.dp().toFloat()
    }

    val iconBox = TextView(requireContext()).apply {
      text = title.take(2).uppercase(Locale.getDefault())
      gravity = Gravity.CENTER
      textSize = 10f
      setTextColor(Color.WHITE)
      setTypeface(null, Typeface.BOLD)
      background = iconBackground
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
      text = getMaterialSummary(materials)
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

    val arrow = ImageView(requireContext()).apply {
      setImageResource(R.drawable.ic_arrow_right)
      setColorFilter(Color.parseColor("#8FA4BE"))
      scaleType = ImageView.ScaleType.CENTER

      layoutParams = LinearLayout.LayoutParams(
        22.dp(),
        22.dp()
      )
    }

    textBox.addView(titleText)
    textBox.addView(summaryText)

    row.addView(iconBox)
    row.addView(textBox)
    row.addView(arrow)

    card.addView(row)

    card.setOnClickListener {
      openMaterialPickerFlow(
        categoryKey = categoryKey,
        categoryTitle = title
      )
    }

    return card
  }

  fun openMaterialPickerFlow(
    categoryKey: String,
    categoryTitle: String
  ) {
    binding.settingsMaterialPickerContainer.isVisible = true

    childFragmentManager.beginTransaction()
    .replace(
      R.id.settingsMaterialPickerContainer,
      MaterialPickerFragment.newSettingsInstance(
        tankId = tankId,
        categoryKey = categoryKey,
        categoryTitle = categoryTitle
      ),
      "SETTINGS_MATERIAL_PICKER_FRAGMENT"
    )
    .commit()
  }

  override fun closeMaterialPickerFlow() {
    val fragment = childFragmentManager.findFragmentById(
      R.id.settingsMaterialPickerContainer
    )

    if (fragment != null) {
      childFragmentManager.beginTransaction()
      .remove(fragment)
      .commit()
    }

    binding.settingsMaterialPickerContainer.isVisible = false
  }

  private fun getMaterialSummary(
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

  private fun showTankNameSheet() {
    val tank = currentTank ?: return
    val dialog = BottomSheetDialog(requireContext())

    val container = createStepSheetContainer()
    container.addView(createStepSheetHeader("Tank Name", dialog))

    val input = createStepInput(
      text = tank.name,
      hint = "Enter tank name",
      inputType = InputType.TYPE_CLASS_TEXT
    )

    val saveButton = createStepSheetSaveButton {
      val newName = input.text.toString().trim()

      if (newName.length < 2) {
        showSnackBar(
          message = "Tank name must be at least 2 characters.",
          type = BaseActivity.SnackType.WARNING
        )
        return@createStepSheetSaveButton
      }

      viewLifecycleOwner.lifecycleScope.launch {
        aquariumTankViewModel.updateTankName(
          tankId = tankId,
          name = newName
        )

        dialog.dismiss()
      }
    }

    container.addView(input)
    container.addView(saveButton)
    container.addView(createStepSheetCancelButton(dialog))

    showConfiguredBottomSheet(
      dialog = dialog,
      content = container
    )
  }

  private fun showTankTypeSheet() {
    val tank = currentTank ?: return
    val dialog = BottomSheetDialog(requireContext())

    val container = createStepSheetContainer()
    container.addView(createStepSheetHeader("Tank Type", dialog))

    var selectedType = tank.tankType.ifBlank {
      "Fish"
    }

    val grid = GridLayout(requireContext()).apply {
      columnCount = 3

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      params.topMargin = 16.dp()
      layoutParams = params
    }

    fun renderOptions() {
      grid.removeAllViews()

      listOf(
        "Fish",
        "Shrimp",
        "Planted",
        "Marine",
        "Softies",
        "Mixed Reef",
        "SPS",
        "Coral",
        "Other"
      ).forEach {
        type ->
        grid.addView(
          createGridOption(
            text = type,
            selected = type.equals(
              selectedType,
              ignoreCase = true
            )
          ) {
            selectedType = type
            renderOptions()
          }
        )
      }
    }

    renderOptions()

    val saveButton = createStepSheetSaveButton {
      viewLifecycleOwner.lifecycleScope.launch {
        aquariumTankViewModel.updateTankType(
          tankId = tankId,
          tankType = selectedType
        )

        dialog.dismiss()
      }
    }

    container.addView(grid)
    container.addView(saveButton)
    container.addView(createStepSheetCancelButton(dialog))

    showConfiguredBottomSheet(
      dialog = dialog,
      content = container
    )
  }

  private fun showTankSizeSheet() {
    val tank = currentTank ?: return
    val dialog = BottomSheetDialog(requireContext())

    val container = createStepSheetContainer()
    container.addView(createStepSheetHeader("Size", dialog))

    val unitRow = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      background = createRoundedDrawable(
        color = "#16314D",
        radiusPx = 13.dp()
      )
      setPadding(
        14.dp(),
        0,
        14.dp(),
        0
      )

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        46.dp()
      )
      params.topMargin = 16.dp()
      layoutParams = params
    }

    val unitTitle = TextView(requireContext()).apply {
      text = "Unit"
      textSize = 13.5f
      setTextColor(Color.WHITE)
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false

      layoutParams = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
      )
    }

    val unitValue = TextView(requireContext()).apply {
      text = "centimeters"
      textSize = 13.5f
      setTextColor(Color.parseColor("#8FA4BE"))
      includeFontPadding = false
    }

    unitRow.addView(unitTitle)
    unitRow.addView(unitValue)

    val inputsRow = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      params.topMargin = 22.dp()
      layoutParams = params
    }

    val widthInput = addSizeInputColumn(
      parent = inputsRow,
      label = "Width",
      value = tank.widthCm.toString()
    )

    val lengthInput = addSizeInputColumn(
      parent = inputsRow,
      label = "Length",
      value = tank.lengthCm.toString()
    )

    val heightInput = addSizeInputColumn(
      parent = inputsRow,
      label = "Height",
      value = tank.heightCm.toString()
    )

    val saveButton = createStepSheetSaveButton {
      val width = widthInput.text.toString().toIntOrNull()
      val length = lengthInput.text.toString().toIntOrNull()
      val height = heightInput.text.toString().toIntOrNull()

      if (width == null || length == null || height == null ||
        width <= 0 || length <= 0 || height <= 0
      ) {
        showSnackBar(
          message = "Please enter valid tank size.",
          type = BaseActivity.SnackType.WARNING
        )
        return@createStepSheetSaveButton
      }

      viewLifecycleOwner.lifecycleScope.launch {
        aquariumTankViewModel.updateTankSize(
          tankId = tankId,
          widthCm = width,
          lengthCm = length,
          heightCm = height
        )

        dialog.dismiss()
      }
    }

    container.addView(unitRow)
    container.addView(inputsRow)
    container.addView(saveButton)
    container.addView(createStepSheetCancelButton(dialog))

    showConfiguredBottomSheet(
      dialog = dialog,
      content = container
    )
  }

  private fun showVolumeUnitSheet() {
    val tank = currentTank ?: return
    val dialog = BottomSheetDialog(requireContext())

    val container = createStepSheetContainer()
    container.addView(createStepSheetHeader("Volume", dialog))

    var selectedUnit = tank.volumeUnit.ifBlank {
      "L"
    }

    val grid = GridLayout(requireContext()).apply {
      columnCount = 2

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      params.topMargin = 16.dp()
      layoutParams = params
    }

    fun renderOptions() {
      grid.removeAllViews()

      listOf(
        "L",
        "gal"
      ).forEach {
        unit ->
        grid.addView(
          createGridOption(
            text = unit,
            selected = unit.equals(
              selectedUnit,
              ignoreCase = true
            )
          ) {
            selectedUnit = unit
            renderOptions()
          }
        )
      }
    }

    renderOptions()

    val info = TextView(requireContext()).apply {
      text = "Volume is calculated automatically from tank size."
      textSize = 12.5f
      setTextColor(Color.parseColor("#8FA4BE"))
      includeFontPadding = false

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      params.topMargin = 12.dp()
      layoutParams = params
    }

    val saveButton = createStepSheetSaveButton {
      viewLifecycleOwner.lifecycleScope.launch {
        aquariumTankViewModel.updateTankVolumeUnit(
          tankId = tankId,
          volumeUnit = selectedUnit
        )

        dialog.dismiss()
      }
    }

    container.addView(grid)
    container.addView(info)
    container.addView(saveButton)
    container.addView(createStepSheetCancelButton(dialog))

    showConfiguredBottomSheet(
      dialog = dialog,
      content = container
    )
  }

  private fun showSetupDateSheet() {
    val tank = currentTank ?: return
    val dialog = BottomSheetDialog(requireContext())

    val calendar = Calendar.getInstance().apply {
      timeInMillis = tank.setupDateMillis ?: System.currentTimeMillis()
    }

    val container = createStepSheetContainer()
    container.addView(createStepSheetHeader("Setup Date", dialog))

    val pickerRow = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      params.topMargin = 24.dp()
      layoutParams = params
    }

    val monthNames = Array(12) {
      index ->
      DateFormatSymbols(Locale.getDefault())
      .months[index]
      .replaceFirstChar {
        char ->
        if (char.isLowerCase()) {
          char.titlecase(Locale.getDefault())
        } else {
          char.toString()
        }
      }
    }

    val dayPicker = createDateNumberPicker().apply {
      minValue = 1
      maxValue = 31
      value = calendar.get(Calendar.DAY_OF_MONTH)
    }

    val monthPicker = createDateNumberPicker().apply {
      minValue = 0
      maxValue = 11
      displayedValues = monthNames
      value = calendar.get(Calendar.MONTH)
    }

    val yearPicker = createDateNumberPicker().apply {
      val currentYear = Calendar.getInstance().get(Calendar.YEAR)

      minValue = currentYear - 10
      maxValue = currentYear + 10
      value = calendar.get(Calendar.YEAR)
    }

    fun updateDayMax() {
      val tempCalendar = Calendar.getInstance().apply {
        set(
          Calendar.YEAR,
          yearPicker.value
        )
        set(
          Calendar.MONTH,
          monthPicker.value
        )
        set(
          Calendar.DAY_OF_MONTH,
          1
        )
      }

      val maxDay = tempCalendar.getActualMaximum(
        Calendar.DAY_OF_MONTH
      )

      dayPicker.maxValue = maxDay

      if (dayPicker.value > maxDay) {
        dayPicker.value = maxDay
      }
    }

    monthPicker.setOnValueChangedListener {
      _, _, _ ->
      updateDayMax()
    }

    yearPicker.setOnValueChangedListener {
      _, _, _ ->
      updateDayMax()
    }

    updateDayMax()

    pickerRow.addView(dayPicker)
    pickerRow.addView(monthPicker)
    pickerRow.addView(yearPicker)

    val saveButton = createStepSheetSaveButton {
      val selectedCalendar = Calendar.getInstance().apply {
        set(
          Calendar.YEAR,
          yearPicker.value
        )
        set(
          Calendar.MONTH,
          monthPicker.value
        )
        set(
          Calendar.DAY_OF_MONTH,
          dayPicker.value
        )
        set(
          Calendar.HOUR_OF_DAY,
          0
        )
        set(
          Calendar.MINUTE,
          0
        )
        set(
          Calendar.SECOND,
          0
        )
        set(
          Calendar.MILLISECOND,
          0
        )
      }

      viewLifecycleOwner.lifecycleScope.launch {
        aquariumTankViewModel.updateTankSetupDate(
          tankId = tankId,
          setupDateMillis = selectedCalendar.timeInMillis
        )

        dialog.dismiss()
      }
    }

    container.addView(pickerRow)
    container.addView(saveButton)
    container.addView(createStepSheetCancelButton(dialog))

    showConfiguredBottomSheet(
      dialog = dialog,
      content = container
    )
  }

  private fun showStyleSheet() {
    val tank = currentTank ?: return

    TankStyleBottomSheet.show(
      fragment = this,
      currentStyle = tank.tankStyle
    ) {
      newStyle ->
      viewLifecycleOwner.lifecycleScope.launch {
        aquariumTankViewModel.updateTankStyle(
          tankId = tankId,
          tankStyle = newStyle
        )
      }
    }
  }

  private fun showIdeaSheet() {
    val tank = currentTank ?: return
    val dialog = BottomSheetDialog(requireContext())

    val container = createStepSheetContainer()
    container.addView(createStepSheetHeader("Idea", dialog))

    val input = createStepMultilineInput(
      text = tank.description,
      hint = "Write your aquarium idea or concept..."
    )

    val saveButton = createStepSheetSaveButton {
      val newIdea = input.text.toString().trim()

      viewLifecycleOwner.lifecycleScope.launch {
        aquariumTankViewModel.updateTankDescription(
          tankId = tankId,
          description = newIdea
        )

        dialog.dismiss()
      }
    }

    container.addView(input)
    container.addView(saveButton)
    container.addView(createStepSheetCancelButton(dialog))

    showConfiguredBottomSheet(
      dialog = dialog,
      content = container
    )
  }

  private fun showDuplicateTankConfirmationDialog() {
    val tank = currentTank ?: return

    if (isDuplicatingTank) {
      return
    }

    DialogManager.showConfirmDialog(
      context = requireContext(),
      type = DialogType.INFO,
      title = "Duplicate Tank?",
      message = "This will create a copy of \"${tank.name}\" with the same tank data, plants and components.",
      confirmTextResId = R.string.duplicate,
      cancelTextResId = R.string.cancel,
      onConfirm = {
        duplicateCurrentTank()
      }
    )
  }

  private fun duplicateCurrentTank() {
    if (isDuplicatingTank) {
      return
    }

    isDuplicatingTank = true

    val baseActivity = activity as? BaseActivity
    baseActivity?.showLoading(true)

    viewLifecycleOwner.lifecycleScope.launch {
      try {
        aquariumTankViewModel.duplicateTank(
          tankId = tankId
        )

        baseActivity?.showLoading(false)

        val popped = findNavController().popBackStack(
          R.id.aquariumFragment,
          false
        )

        if (!popped) {
          findNavController().navigate(
            R.id.aquariumFragment
          )
        }

      } catch (exception: Exception) {
        exception.printStackTrace()

        isDuplicatingTank = false
        baseActivity?.showLoading(false)

        DialogManager.showInfoDialog(
          context = requireContext(),
          type = DialogType.ERROR,
          title = "Duplicate Failed",
          message = "Tank could not be duplicated."
        )
      }
    }
  }


  private fun exportTankDataAsPdf() {
    val tank = currentTank ?: return

    if (isExportingTank) {
      return
    }

    isExportingTank = true

    val appContext = requireContext().applicationContext
    val baseActivity = activity as? BaseActivity

    baseActivity?.showLoading(true)

    viewLifecycleOwner.lifecycleScope.launch {
      try {
        val pdfUri = withContext(Dispatchers.IO) {
          val connectedDevices = userPrefs.devicesForTankFlow(
            tankId = tankId
          ).first()

          TankPdfExporter.createTankReportPdf(
            context = appContext,
            tank = tank,
            devices = connectedDevices
          )
        }
        baseActivity?.showLoading(false)
        isExportingTank = false

        TankPdfExporter.shareTankReportPdf(
          context = requireContext(),
          pdfUri = pdfUri,
          tankName = tank.name
        )

      } catch (exception: Exception) {
        exception.printStackTrace()

        isExportingTank = false
        baseActivity?.showLoading(false)

        DialogManager.showInfoDialog(
          context = requireContext(),
          type = DialogType.ERROR,
          title = "Export Failed",
          message = "Tank report could not be created."
        )
      }
    }
  }

  private fun showDeleteTankConfirmationDialog() {
    val tank = currentTank ?: return

    DialogManager.showConfirmDialog(
      context = requireContext(),
      type = DialogType.WARNING,
      title = "Delete Tank?",
      message = "This will permanently delete \"${tank.name}\" and all saved tank data.",
      confirmTextResId = R.string.delete,
      cancelTextResId = R.string.cancel,
      onConfirm = {
        deleteCurrentTank()
      }
    )
  }

  private fun deleteCurrentTank() {
    if (isDeletingTank) {
      return
    }

    isDeletingTank = true

    val baseActivity = activity as? BaseActivity
    baseActivity?.showLoading(true)

    viewLifecycleOwner.lifecycleScope.launch {
      try {
        userPrefs.unassignDevicesFromTank(
          tankId = tankId
        )

        aquariumTankViewModel.deleteTanks(
          tankIds = listOf(tankId)
        )

        baseActivity?.showLoading(false)

        val popped = findNavController().popBackStack(
          R.id.aquariumFragment,
          false
        )

        if (!popped) {
          findNavController().navigate(
            R.id.aquariumFragment
          )
        }

      } catch (exception: Exception) {
        exception.printStackTrace()

        isDeletingTank = false
        baseActivity?.showLoading(false)

        DialogManager.showInfoDialog(
          context = requireContext(),
          type = DialogType.ERROR,
          title = "Delete Failed",
          message = "Tank could not be deleted."
        )
      }
    }
  }

  private fun createStepSheetContainer(): LinearLayout {
    return LinearLayout(requireContext()).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(
        18.dp(),
        18.dp(),
        18.dp(),
        20.dp()
      )
      background = createTopRoundedDrawable(
        color = "#10233A",
        radiusPx = 24.dp()
      )
    }
  }

  private fun createStepSheetHeader(
    title: String,
    dialog: BottomSheetDialog
  ): View {
    val row = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }

    val leftSpacer = View(requireContext()).apply {
      layoutParams = LinearLayout.LayoutParams(
        38.dp(),
        38.dp()
      )
    }

    val titleText = TextView(requireContext()).apply {
      text = title
      gravity = Gravity.CENTER
      textSize = 16f
      setTextColor(Color.WHITE)
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false

      layoutParams = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
      )
    }

    val close = TextView(requireContext()).apply {
      text = "×"
      gravity = Gravity.CENTER
      textSize = 30f
      setTextColor(Color.WHITE)
      includeFontPadding = false

      layoutParams = LinearLayout.LayoutParams(
        38.dp(),
        38.dp()
      )

      setOnClickListener {
        dialog.dismiss()
      }
    }

    row.addView(leftSpacer)
    row.addView(titleText)
    row.addView(close)

    return row
  }

  private fun createStepInput(
    text: String,
    hint: String,
    inputType: Int
  ): EditText {
    return EditText(requireContext()).apply {
      setText(text)
      this.hint = hint
      this.inputType = inputType
      setSingleLine(true)
      textSize = 14f
      setTextColor(Color.WHITE)
      setHintTextColor(Color.parseColor("#8FA4BE"))
      background = createRoundedDrawable(
        color = "#16314D",
        radiusPx = 14.dp()
      )
      setPadding(
        14.dp(),
        0,
        14.dp(),
        0
      )
      setSelectAllOnFocus(true)

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        48.dp()
      )
      params.topMargin = 18.dp()
      layoutParams = params
    }
  }

  private fun createStepMultilineInput(
    text: String,
    hint: String
  ): EditText {
    return EditText(requireContext()).apply {
      setText(text)
      this.hint = hint
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
      gravity = Gravity.TOP or Gravity.START
      minLines = 4
      maxLines = 5
      textSize = 14f
      setTextColor(Color.WHITE)
      setHintTextColor(Color.parseColor("#8FA4BE"))
      background = createRoundedDrawable(
        color = "#16314D",
        radiusPx = 14.dp()
      )
      setPadding(
        14.dp(),
        12.dp(),
        14.dp(),
        12.dp()
      )
      setSelectAllOnFocus(false)

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        112.dp()
      )
      params.topMargin = 18.dp()
      layoutParams = params
    }
  }

  private fun createGridOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
  ): View {
    return TextView(requireContext()).apply {
      this.text = text
      gravity = Gravity.CENTER
      textSize = 13.5f
      setTextColor(Color.WHITE)
      setTypeface(
        null,
        if (selected) Typeface.BOLD else Typeface.NORMAL
      )
      includeFontPadding = false

      background = createRoundedDrawable(
        color = if (selected) "#1C3D63" else "#10233A",
        radiusPx = 13.dp(),
        strokeColor = if (selected) "#2196F3" else "#223A57",
        strokeWidthPx = 1.dp()
      )

      val params = GridLayout.LayoutParams().apply {
        width = 0
        height = 46.dp()
        columnSpec = GridLayout.spec(
          GridLayout.UNDEFINED,
          1f
        )
        setMargins(
          0,
          0,
          8.dp(),
          8.dp()
        )
      }

      layoutParams = params

      setOnClickListener {
        onClick()
      }
    }
  }

  private fun addSizeInputColumn(
    parent: LinearLayout,
    label: String,
    value: String
  ): EditText {
    val column = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.VERTICAL

      val params = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
      )
      params.marginEnd = 8.dp()
      layoutParams = params
    }

    val labelText = TextView(requireContext()).apply {
      text = label
      textSize = 13f
      setTextColor(Color.WHITE)
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false
    }

    val input = EditText(requireContext()).apply {
      setText(value)
      inputType = InputType.TYPE_CLASS_NUMBER
      setSingleLine(true)
      setSelectAllOnFocus(true)
      gravity = Gravity.CENTER_VERTICAL
      textSize = 18f
      setTextColor(Color.WHITE)
      setHintTextColor(Color.parseColor("#8FA4BE"))
      background = createRoundedDrawable(
        color = "#16314D",
        radiusPx = 13.dp()
      )
      setPadding(
        14.dp(),
        0,
        14.dp(),
        0
      )

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        50.dp()
      )
      params.topMargin = 9.dp()
      layoutParams = params
    }

    column.addView(labelText)
    column.addView(input)

    parent.addView(column)

    return input
  }

  private fun createDateNumberPicker(): NumberPicker {
    return NumberPicker(requireContext()).apply {
      wrapSelectorWheel = false

      layoutParams = LinearLayout.LayoutParams(
        0,
        128.dp(),
        1f
      )
    }
  }

  private fun createStepSheetSaveButton(
    onClick: () -> Unit
  ): MaterialButton {
    return MaterialButton(requireContext()).apply {
      text = "Save"
      textSize = 14f
      setTypeface(null, Typeface.BOLD)
      setAllCaps(false)
      setTextColor(Color.WHITE)
      cornerRadius = 16.dp()
      backgroundTintList = android.content.res.ColorStateList.valueOf(
        Color.parseColor("#2196F3")
      )

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        50.dp()
      )
      params.topMargin = 26.dp()
      layoutParams = params

      setOnClickListener {
        onClick()
      }
    }
  }

  private fun createStepSheetCancelButton(
    dialog: BottomSheetDialog
  ): View {
    return TextView(requireContext()).apply {
      text = "Cancel"
      gravity = Gravity.CENTER
      textSize = 14f
      setTextColor(Color.parseColor("#8FA4BE"))
      includeFontPadding = false

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        40.dp()
      )
      params.topMargin = 10.dp()
      layoutParams = params

      setOnClickListener {
        dialog.dismiss()
      }
    }
  }

  private fun showConfiguredBottomSheet(
    dialog: BottomSheetDialog,
    content: View
  ) {
    dialog.setContentView(content)

    dialog.setOnShowListener {
      val bottomSheet = dialog.findViewById<FrameLayout>(
        com.google.android.material.R.id.design_bottom_sheet
      )

      bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
    }

    dialog.show()
  }

  private fun getSizeText(
    tank: SavedAquariumTank
  ): String {
    return "${tank.widthCm} W x ${tank.lengthCm} L x ${tank.heightCm} H"
  }

  private fun getVolumeText(
    tank: SavedAquariumTank
  ): String {
    val liter = (tank.widthCm * tank.lengthCm * tank.heightCm) / 1000.0

    return if (tank.volumeUnit.equals("gal", ignoreCase = true)) {
      val gallon = liter * 0.264172
      "${gallon.roundToInt()} gal"
    } else {
      "${liter.roundToInt()} L"
    }
  }

  private fun getSetupDateText(
    setupDateMillis: Long?
  ): String {
    if (setupDateMillis == null) {
      return "-"
    }

    val formatter = SimpleDateFormat(
      "dd MMM yyyy",
      Locale.getDefault()
    )

    return formatter.format(Date(setupDateMillis))
  }

  private fun createTopRoundedDrawable(
    color: String,
    radiusPx: Int
  ): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      setColor(Color.parseColor(color))

      cornerRadii = floatArrayOf(
        radiusPx.toFloat(),
        radiusPx.toFloat(),
        radiusPx.toFloat(),
        radiusPx.toFloat(),
        0f,
        0f,
        0f,
        0f
      )
    }
  }

  private fun createRoundedDrawable(
    color: String,
    radiusPx: Int,
    strokeColor: String? = null,
    strokeWidthPx: Int = 0
  ): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      setColor(Color.parseColor(color))
      cornerRadius = radiusPx.toFloat()

      if (strokeColor != null && strokeWidthPx > 0) {
        setStroke(
          strokeWidthPx,
          Color.parseColor(strokeColor)
        )
      }
    }
  }

  private fun Int.dp(): Int {
    return (this * resources.displayMetrics.density).toInt()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  private enum class SettingsTab {
    BASIC,
    DETAILS,
    OTHERS
  }

  companion object {
    private const val ARG_TANK_ID = "tankId"
  }
}