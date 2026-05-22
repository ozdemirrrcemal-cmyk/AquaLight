package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.DialogCareProfileBinding
import com.aqua.aqualight.databinding.FragmentTankSettingsBinding
import com.aqua.aqualight.databinding.ItemCareProfileRowBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.careprofile.CareProfileCalculator
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.TankDetailFragment
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.roundToInt

class TankSettingsFragment : Fragment(R.layout.fragment_tank_settings),
    MaterialPickerFragment.MaterialPickerHost {

    private var _binding: FragmentTankSettingsBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var selectedTab: SettingsTab = SettingsTab.BASIC
    private var currentTank: SavedAquariumTank? = null
    private var isDeletingTank: Boolean = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
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

        binding.scoreContainer.setOnClickListener {
            currentTank?.let { tank ->
                showCareProfileSheet(tank)
            }
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

    private fun setupSwipeBetweenTabs() {
        binding.basicFragmentContainer.setOnSwipeLeftListener {
            moveToNextTab()
        }

        binding.basicFragmentContainer.setOnSwipeRightListener {
            moveToPreviousTab()
        }

        binding.detailsFragmentContainer.setOnSwipeLeftListener {
            moveToNextTab()
        }

        binding.detailsFragmentContainer.setOnSwipeRightListener {
            moveToPreviousTab()
        }

        binding.othersFragmentContainer.setOnSwipeLeftListener {
            moveToNextTab()
        }

        binding.othersFragmentContainer.setOnSwipeRightListener {
            moveToPreviousTab()
        }
    }

    private fun setTabSwipeEnabled(
        enabled: Boolean
    ) {
        binding.basicFragmentContainer.setSwipeEnabled(enabled)
        binding.detailsFragmentContainer.setSwipeEnabled(enabled)
        binding.othersFragmentContainer.setSwipeEnabled(enabled)
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

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { savedTank ->
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
        renderCareProfileScore(tank)
    }

    fun markTankDeletionInProgress() {
        isDeletingTank = true
    }

    private fun handleMaterialPickerBack(): Boolean {
        if (!binding.settingsMaterialPickerContainer.isVisible) {
            return false
        }

        closeMaterialPickerFlow()
        return true
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

                binding.contentScrollView.isVisible = false
                binding.basicFragmentContainer.isVisible = true

                showBasicFragmentIfNeeded()
            }

            SettingsTab.DETAILS -> {
                activateTab(binding.tabDetails)
                moveTabUnderline(binding.tabDetails)

                binding.contentScrollView.isVisible = false
                binding.detailsFragmentContainer.isVisible = true

                showDetailsFragmentIfNeeded()
            }

            SettingsTab.OTHERS -> {
                activateTab(binding.tabOthers)
                moveTabUnderline(binding.tabOthers)

                binding.contentScrollView.isVisible = false
                binding.othersFragmentContainer.isVisible = true

                showOthersFragmentIfNeeded()
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

    private fun resetTabs() {
        val inactiveColor = Color.parseColor("#8FA4BE")

        listOf(
            binding.tabBasic,
            binding.tabDetails,
            binding.tabOthers
        ).forEach { tab ->
            tab.setTextColor(inactiveColor)
            tab.setTypeface(null, Typeface.NORMAL)
        }

        binding.contentScrollView.isVisible = true
        binding.basicFragmentContainer.isVisible = false
        binding.detailsFragmentContainer.isVisible = false
        binding.othersFragmentContainer.isVisible = false
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

        result.items.forEach { item ->
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

            bottomSheet?.let { sheet ->
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

            binding.detailsFragmentContainer.post {
                openMaterialPickerFlow(
                    categoryKey = item.materialCategoryKey,
                    categoryTitle = item.materialCategoryTitle
                )
            }

            return
        }

        when (item.title) {
            "Tank name" -> {
                openBasicCareProfileAction(
                    TankSettingsBasicFragment.ACTION_TANK_NAME
                )
            }

            "Tank type" -> {
                openBasicCareProfileAction(
                    TankSettingsBasicFragment.ACTION_TANK_TYPE
                )
            }

            "Tank size" -> {
                openBasicCareProfileAction(
                    TankSettingsBasicFragment.ACTION_TANK_SIZE
                )
            }

            "Setup date" -> {
                openBasicCareProfileAction(
                    TankSettingsBasicFragment.ACTION_SETUP_DATE
                )
            }

            "Tank style" -> {
                openBasicCareProfileAction(
                    TankSettingsBasicFragment.ACTION_STYLE
                )
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
            }

            else -> Unit
        }
    }

    private fun openBasicCareProfileAction(
        action: String
    ) {
        selectTab(SettingsTab.BASIC)

        childFragmentManager.executePendingTransactions()

        binding.basicFragmentContainer.post {
            val basicFragment = getBasicFragment() ?: return@post

            basicFragment.openCareProfileAction(action)
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

    fun openMaterialPickerFlow(
        categoryKey: String,
        categoryTitle: String
    ) {
        setTabSwipeEnabled(false)

        binding.settingsMaterialPickerContainer.isVisible = true

        childFragmentManager.beginTransaction()
            .replace(
                R.id.settingsMaterialPickerContainer,
                MaterialPickerFragment.newSettingsInstance(
                    tankId = tankId,
                    categoryKey = categoryKey,
                    categoryTitle = categoryTitle
                ),
                TAG_MATERIAL_PICKER_FRAGMENT
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
        setTabSwipeEnabled(true)
    }

    private fun showBasicFragmentIfNeeded() {
        binding.basicFragmentContainer.isVisible = true

        val existingFragment = getBasicFragment()

        if (existingFragment != null) {
            return
        }

        childFragmentManager.beginTransaction()
            .replace(
                R.id.basicFragmentContainer,
                TankSettingsBasicFragment.newInstance(tankId),
                TAG_BASIC_FRAGMENT
            )
            .commit()
    }

    private fun getBasicFragment(): TankSettingsBasicFragment? {
        return childFragmentManager.findFragmentByTag(
            TAG_BASIC_FRAGMENT
        ) as? TankSettingsBasicFragment
    }

    private fun showDetailsFragmentIfNeeded() {
        binding.detailsFragmentContainer.isVisible = true

        val existingFragment = getDetailsFragment()

        if (existingFragment != null) {
            return
        }

        childFragmentManager.beginTransaction()
            .replace(
                R.id.detailsFragmentContainer,
                TankSettingsDetailsFragment.newInstance(tankId),
                TAG_DETAILS_FRAGMENT
            )
            .commit()
    }

    private fun getDetailsFragment(): TankSettingsDetailsFragment? {
        return childFragmentManager.findFragmentByTag(
            TAG_DETAILS_FRAGMENT
        ) as? TankSettingsDetailsFragment
    }

    private fun showOthersFragmentIfNeeded() {
        binding.othersFragmentContainer.isVisible = true

        val existingFragment = getOthersFragment()

        if (existingFragment != null) {
            return
        }

        childFragmentManager.beginTransaction()
            .replace(
                R.id.othersFragmentContainer,
                TankSettingsOthersFragment.newInstance(tankId),
                TAG_OTHERS_FRAGMENT
            )
            .commit()
    }

    private fun getOthersFragment(): TankSettingsOthersFragment? {
        return childFragmentManager.findFragmentByTag(
            TAG_OTHERS_FRAGMENT
        ) as? TankSettingsOthersFragment
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

        private const val TAG_BASIC_FRAGMENT = "TankSettingsBasicFragment"
        private const val TAG_DETAILS_FRAGMENT = "TankSettingsDetailsFragment"
        private const val TAG_OTHERS_FRAGMENT = "TankSettingsOthersFragment"
        private const val TAG_MATERIAL_PICKER_FRAGMENT = "SettingsMaterialPickerFragment"
    }
}