package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
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
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.ContentSheetIdeaBinding
import com.aqua.aqualight.databinding.ContentSheetSetupDateBinding
import com.aqua.aqualight.databinding.ContentSheetTankNameBinding
import com.aqua.aqualight.databinding.ContentSheetTankSizeBinding
import com.aqua.aqualight.databinding.DialogCareProfileBinding
import com.aqua.aqualight.databinding.FragmentTankSettingsBinding
import com.aqua.aqualight.databinding.ItemCareProfileRowBinding
import com.aqua.aqualight.ui.common.bottomsheet.SettingsContentBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankStyleBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankTypeBottomSheet
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.careprofile.CareProfileCalculator
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryCatalog
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.TankDetailFragment
import com.aqua.aqualight.ui.tabs.aquarium.export.TankPdfExporter
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.yalantis.ucrop.UCrop
import java.io.File
import java.text.DateFormatSymbols
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aqua.aqualight.ui.common.bottomsheet.SetupDateBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankSizeBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet

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

    private val sizeFormatter = DecimalFormat(
        "#0.##",
        DecimalFormatSymbols(Locale.US)
    )

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
        setupPhotoSourceResultListener()
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

        binding.scoreContainer.setOnClickListener {
            currentTank?.let {
                tank ->
                showCareProfileSheet(tank)
            }
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
            toggleVolumeUnit()
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

        binding.tvSettingSizeTitle.text = getSizeTitleText(tank)
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
        renderCareProfileScore(tank)
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

    private fun showSettingsBottomSheet(
        title: String,
        contentView: View,
        onDialogReady: ((BottomSheetDialog) -> Unit)? = null
) {
    SettingsContentBottomSheet.show(
        fragment = this,
        title = title,
        contentView = contentView,
        onDialogReady = onDialogReady
    )
}

private fun setupPhotoSourceResultListener() {
    childFragmentManager.setFragmentResultListener(
        PhotoSourceBottomSheet.REQUEST_KEY,
        viewLifecycleOwner
    ) {
        _, bundle ->

        when (bundle.getString(PhotoSourceBottomSheet.RESULT_KEY)) {
            PhotoSourceBottomSheet.RESULT_CAMERA -> {
                openCamera()
            }

            PhotoSourceBottomSheet.RESULT_GALLERY -> {
                openGallery()
            }
        }
    }
}

private fun showPhotoSourceSheet() {
    PhotoSourceBottomSheet
    .newInstance(
        title = "Aquarium Photo"
    )
    .show(
        childFragmentManager,
        PhotoSourceBottomSheet.TAG
    )
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

private fun renderCareProfileScore(
    tank: SavedAquariumTank
) {
    val result = CareProfileCalculator.calculate(tank)
    val color = getCareProfileColor(result.percent)

    binding.scoreContainer.isVisible = true
    binding.tvScore.text = result.percent.toString()
    binding.tvScore.setTextColor(color)
    binding.scoreContainer.strokeColor = color
}

private fun showCareProfileSheet(
    tank: SavedAquariumTank
) {
    val result = CareProfileCalculator.calculate(tank)
    val dialog = BottomSheetDialog(requireContext())
    val sheetBinding = DialogCareProfileBinding.inflate(layoutInflater)

    val profileColor = getCareProfileColor(result.percent)

    sheetBinding.tvCareProfilePercent.text = "${result.percent}%"
    sheetBinding.tvCareProfileSummary.text =
    "${result.completedCount} of ${result.totalCount} care details completed"

    sheetBinding.careProgressTrack.background = createRoundedDrawable(
        color = "#DDE3EA",
        radiusPx = 3.dp()
    )

    sheetBinding.careProgressFill.background = createRoundedDrawable(
        color = colorToHex(profileColor),
        radiusPx = 3.dp()
    )

    sheetBinding.btnCloseCareProfile.setOnClickListener {
        dialog.dismiss()
    }

    sheetBinding.careProfileItemsContainer.removeAllViews()

    result.items.forEach {
        item ->
        val rowBinding = ItemCareProfileRowBinding.inflate(
            layoutInflater,
            sheetBinding.careProfileItemsContainer,
            false
        )

        rowBinding.tvCareProfileItemTitle.text = item.title
        rowBinding.tvCareProfileItemSubtitle.text = item.subtitle

        rowBinding.tvCareProfileItemStatus.text = if (item.completed) {
            "Complete"
        } else {
            "Missing"
        }

        rowBinding.tvCareProfileItemStatus.setTextColor(
            if (item.completed) {
                Color.parseColor("#5FD6B4")
            } else {
                Color.parseColor("#E0A84C")
            }
        )

        rowBinding.tvCareProfileItemStatus.background = createRoundedDrawable(
            color = if (item.completed) "#09251D" else "#2A2315",
            radiusPx = 14.dp(),
            strokeColor = if (item.completed) "#1E5A48" else "#6A4D1E",
            strokeWidthPx = 1.dp()
        )

        rowBinding.root.setOnClickListener {
            dialog.dismiss()
            handleCareProfileItemClick(item)
        }

        sheetBinding.careProfileItemsContainer.addView(rowBinding.root)
    }

    dialog.setContentView(sheetBinding.root)

    dialog.setOnShowListener {
        val bottomSheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        )

        val maxHeight = (
            resources.displayMetrics.heightPixels * 0.82f
        ).roundToInt()

        bottomSheet?.let {
            sheet ->
            sheet.setBackgroundColor(Color.TRANSPARENT)

            val params = sheet.layoutParams
            params.height = maxHeight
            sheet.layoutParams = params
        }

        dialog.behavior.peekHeight = maxHeight

        sheetBinding.careProgressTrack.post {
            val fillWidth = (
                sheetBinding.careProgressTrack.width * result.percent / 100f
            ).roundToInt()

            val params = sheetBinding.careProgressFill.layoutParams
            params.width = fillWidth
            sheetBinding.careProgressFill.layoutParams = params
        }
    }

    dialog.show()
}

private fun handleCareProfileItemClick(
    item: CareProfileCalculator.Item
) {
    if (
        item.materialCategoryKey != null &&
        item.materialCategoryTitle != null
    ) {
        selectTab(SettingsTab.DETAILS)

        binding.contentScrollView.post {
            openMaterialPickerFlow(
                categoryKey = item.materialCategoryKey,
                categoryTitle = item.materialCategoryTitle
            )
        }

        return
    }

    when (item.title) {
        "Tank name" -> {
            selectTab(SettingsTab.BASIC)

            binding.contentScrollView.post {
                showTankNameSheet()
            }
        }

        "Tank type" -> {
            selectTab(SettingsTab.BASIC)

            binding.contentScrollView.post {
                showTankTypeSheet()
            }
        }

        "Tank size" -> {
            selectTab(SettingsTab.BASIC)

            binding.contentScrollView.post {
                showTankSizeSheet()
            }
        }

        "Setup date" -> {
            selectTab(SettingsTab.BASIC)

            binding.contentScrollView.post {
                showSetupDateSheet()
            }
        }

        "Tank style" -> {
            selectTab(SettingsTab.BASIC)

            binding.contentScrollView.post {
                showStyleSheet()
            }
        }

        "Plants" -> {
            openTankDetailCareProfileAction(
                TankDetailFragment.CARE_PROFILE_ACTION_PLANTS
            )
        }

        "Livestock" -> {
            openTankDetailCareProfileAction(
                TankDetailFragment.CARE_PROFILE_ACTION_LIVESTOCK
            )
        } else -> Unit
    }
}

private fun openTankDetailCareProfileAction(
    action: String
) {
    val navController = findNavController()
    val previousEntry = navController.previousBackStackEntry

    if (previousEntry?.destination?.id == R.id.tankDetailFragment) {
        previousEntry.savedStateHandle.set(
            TankDetailFragment.KEY_CARE_PROFILE_ACTION,
            action
        )

        navController.navigateUp()
        return
    }

    navController.popBackStack()

    navController.navigate(
        R.id.tankDetailFragment,
        bundleOf(
            "tankId" to tankId
        )
    )

    navController.currentBackStackEntry
    ?.savedStateHandle
    ?.set(
        TankDetailFragment.KEY_CARE_PROFILE_ACTION,
        action
    )
}

private fun getCareProfileColor(
    percent: Int
): Int {
    return when {
        percent < 40 -> Color.parseColor("#D85C5C")
        percent < 75 -> Color.parseColor("#E0A84C")
        else -> Color.parseColor("#5FD6B4")
    }
}

private fun colorToHex(
    color: Int
): String {
    return String.format(
        "#%06X",
        0xFFFFFF and color
    )
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

    val contentBinding = ContentSheetTankNameBinding.inflate(
        layoutInflater
    )

    contentBinding.inputTankName.setText(tank.name)

    showSettingsBottomSheet(
        title = "Tank Name",
        contentView = contentBinding.root
    ) {
        dialog ->

        contentBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        contentBinding.btnSave.setOnClickListener {
            val newName = contentBinding.inputTankName.text
            .toString()
            .trim()

            if (newName.length < 2) {
                showSnackBar(
                    message = "Tank name must be at least 2 characters.",
                    type = BaseActivity.SnackType.WARNING
                )
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                aquariumTankViewModel.updateTankName(
                    tankId = tankId,
                    name = newName
                )

                dialog.dismiss()
            }
        }
    }
}

private fun showTankTypeSheet() {
    val tank = currentTank ?: return

    TankTypeBottomSheet.show(
        fragment = this,
        currentType = tank.tankType,
        onSave = {
            selectedType,
            dismiss ->

            viewLifecycleOwner.lifecycleScope.launch {
                aquariumTankViewModel.updateTankType(
                    tankId = tankId,
                    tankType = selectedType
                )

                dismiss()
            }
        }
    )
}

private fun showTankSizeSheet() {
    val tank = currentTank ?: return

    TankSizeBottomSheet.show(
        fragment = this,
        currentWidthCm = tank.widthCm,
        currentLengthCm = tank.lengthCm,
        currentHeightCm = tank.heightCm,
        currentUnit = tank.sizeUnit,
        title = "Size",
        onInvalidInput = {
            showSnackBar(
                message = "Please enter valid tank size.",
                type = BaseActivity.SnackType.WARNING
            )
        },
        onSave = {
            result,
            dismiss ->

            viewLifecycleOwner.lifecycleScope.launch {
                aquariumTankViewModel.updateTankSize(
                    tankId = tankId,
                    widthCm = result.widthCm,
                    lengthCm = result.lengthCm,
                    heightCm = result.heightCm,
                    sizeUnit = result.sizeUnit
                )

                dismiss()
            }
        }
    )
}

private fun toggleVolumeUnit() {
    val tank = currentTank ?: return

    val newUnit = if (
        tank.volumeUnit.equals(
            "gal",
            ignoreCase = true
        )
    ) {
        "L"
    } else {
        "gal"
    }

    viewLifecycleOwner.lifecycleScope.launch {
        aquariumTankViewModel.updateTankVolumeUnit(
            tankId = tankId,
            volumeUnit = newUnit
        )
    }
}

private fun showSetupDateSheet() {
    val tank = currentTank ?: return

    val currentYear = Calendar.getInstance().get(
        Calendar.YEAR
    )

    SetupDateBottomSheet.show(
        fragment = this,
        currentMillis = tank.setupDateMillis,
        minYear = currentYear - 10,
        maxYear = currentYear + 10,
        monthLocale = Locale.getDefault(),
        onSave = {
            selectedMillis,
            dismiss ->

            viewLifecycleOwner.lifecycleScope.launch {
                aquariumTankViewModel.updateTankSetupDate(
                    tankId = tankId,
                    setupDateMillis = selectedMillis
                )

                dismiss()
            }
        }
    )
}

private fun showStyleSheet() {
    val tank = currentTank ?: return

    TankStyleBottomSheet.show(
        fragment = this,
        currentStyle = tank.tankStyle,
        onSave = {
            selectedStyle,
            dismiss ->

            viewLifecycleOwner.lifecycleScope.launch {
                aquariumTankViewModel.updateTankStyle(
                    tankId = tankId,
                    tankStyle = selectedStyle
                )

                dismiss()
            }
        }
    )
}

private fun showIdeaSheet() {
    val tank = currentTank ?: return

    val contentBinding = ContentSheetIdeaBinding.inflate(
        layoutInflater
    )

    contentBinding.inputIdea.setText(tank.description)

    showSettingsBottomSheet(
        title = "Idea",
        contentView = contentBinding.root
    ) {
        dialog ->

        contentBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        contentBinding.btnSave.setOnClickListener {
            val newIdea = contentBinding.inputIdea.text
            .toString()
            .trim()

            viewLifecycleOwner.lifecycleScope.launch {
                aquariumTankViewModel.updateTankDescription(
                    tankId = tankId,
                    description = newIdea
                )

                dialog.dismiss()
            }
        }
    }
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

private fun getSizeTitleText(
    tank: SavedAquariumTank
): String {
    return if (tank.sizeUnit.equals("in", ignoreCase = true)) {
        "Size (in)"
    } else {
        "Size (cm)"
    }
}

private fun getSizeText(
    tank: SavedAquariumTank
): String {
    return if (tank.sizeUnit.equals("in", ignoreCase = true)) {
        val widthIn = tank.widthCm / 2.54
        val lengthIn = tank.lengthCm / 2.54
        val heightIn = tank.heightCm / 2.54

        "${sizeFormatter.format(widthIn)} W x ${sizeFormatter.format(lengthIn)} L x ${sizeFormatter.format(heightIn)} H"
    } else {
        "${tank.widthCm} W x ${tank.lengthCm} L x ${tank.heightCm} H"
    }
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