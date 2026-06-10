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
import androidx.fragment.app.commit
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.DialogCareProfileBinding
import com.aqua.aqualight.databinding.FragmentTankSettingsBinding
import com.aqua.aqualight.databinding.ItemCareProfileRowBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderScoreBadge
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.careprofile.CareProfileCalculator
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.TankDetailFragment
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.roundToInt

class TankSettingsFragment : Fragment(R.layout.fragment_tank_settings) {

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

        selectedTab = restoreSelectedTab(savedInstanceState)

        setupHeader()
        setupClickListeners()
        setupSystemBackButton()
        setupSwipeBetweenTabs()
        observeTank()
        selectTab(selectedTab)
    }

    private fun setupHeader(
        scoreText: String? = null,
        scoreColor: Int? = null
    ) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = "Tank Settings",
                onBackClick = {
                    findNavController().navigateUp()
                },
                scoreBadge = if (scoreText != null && scoreColor != null) {
                    AquaHeaderScoreBadge(
                        text = scoreText,
                        strokeColor = scoreColor,
                        textColor = scoreColor,
                        contentDescription = "Tank score",
                        onClick = {
                            currentTank?.let { tank ->
                                showCareProfileSheet(tank)
                            }
                        }
                    )
                } else {
                    null
                }
            )
        )
    }

    private fun restoreSelectedTab(
        savedInstanceState: Bundle?
    ): SettingsTab {
        val navSavedTab = findNavController()
            .currentBackStackEntry
            ?.savedStateHandle
            ?.get<String>(KEY_SELECTED_TAB)
            ?.let { tabName ->
                runCatching {
                    SettingsTab.valueOf(tabName)
                }.getOrNull()
            }

        if (navSavedTab != null) {
            return navSavedTab
        }

        val instanceSavedTab = savedInstanceState
            ?.getString(KEY_SELECTED_TAB)
            ?.let { tabName ->
                runCatching {
                    SettingsTab.valueOf(tabName)
                }.getOrNull()
            }

        return instanceSavedTab ?: getInitialTab()
    }

    private fun saveSelectedTabState(
        tab: SettingsTab
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
        binding.tabBasic.setOnClickListener {
            selectTab(SettingsTab.BASIC)
        }

        binding.tabDetails.setOnClickListener {
            selectTab(SettingsTab.DETAILS)
        }

        binding.tabOthers.setOnClickListener {
            selectTab(SettingsTab.OTHERS)
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
            binding.basicFragmentContainer,
            binding.detailsFragmentContainer,
            binding.othersFragmentContainer
        ).forEach { container ->
            container.setOnSwipeLeftListener {
                moveTabBy(offset = 1)
            }

            container.setOnSwipeRightListener {
                moveTabBy(offset = -1)
            }
        }
    }

    private fun moveTabBy(
        offset: Int
    ) {
        val currentIndex = TAB_ORDER.indexOf(selectedTab)

        if (currentIndex == -1) {
            return
        }

        val targetIndex = (currentIndex + offset).coerceIn(
            0,
            TAB_ORDER.lastIndex
        )

        if (targetIndex == currentIndex) {
            return
        }

        selectTab(TAB_ORDER[targetIndex])
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

    private fun selectTab(
        tab: SettingsTab
    ) {
        selectedTab = tab
        saveSelectedTabState(tab)

        resetTabs()
        activateTab(tabViewFor(tab))
        moveTabUnderline(tabViewFor(tab))
        showContentForTab(tab)

        binding.contentScrollView.post {
            binding.contentScrollView.scrollTo(
                0,
                0
            )
        }
    }

    private fun showContentForTab(
        tab: SettingsTab
    ) {
        binding.contentScrollView.isVisible = false

        when (tab) {
            SettingsTab.BASIC -> {
                binding.basicFragmentContainer.isVisible = true

                showFragmentIfNeeded(
                    containerId = R.id.basicFragmentContainer,
                    tag = TAG_BASIC_FRAGMENT
                ) {
                    TankSettingsBasicFragment.newInstance(tankId)
                }
            }

            SettingsTab.DETAILS -> {
                binding.detailsFragmentContainer.isVisible = true

                showFragmentIfNeeded(
                    containerId = R.id.detailsFragmentContainer,
                    tag = TAG_DETAILS_FRAGMENT
                ) {
                    TankSettingsDetailsFragment.newInstance(tankId)
                }
            }

            SettingsTab.OTHERS -> {
                binding.othersFragmentContainer.isVisible = true

                showFragmentIfNeeded(
                    containerId = R.id.othersFragmentContainer,
                    tag = TAG_OTHERS_FRAGMENT
                ) {
                    TankSettingsOthersFragment.newInstance(tankId)
                }
            }
        }
    }

    private fun showFragmentIfNeeded(
        containerId: Int,
        tag: String,
        fragmentFactory: () -> Fragment
    ) {
        val existingFragment = childFragmentManager.findFragmentByTag(tag)

        if (existingFragment != null) {
            return
        }

        childFragmentManager.commit {
            replace(
                containerId,
                fragmentFactory(),
                tag
            )
        }
    }

    private fun getInitialTab(): SettingsTab {
        val startTab = arguments?.getString(ARG_START_TAB)

        return when (startTab) {
            START_TAB_DETAILS -> SettingsTab.DETAILS
            START_TAB_OTHERS -> SettingsTab.OTHERS
            else -> SettingsTab.BASIC
        }
    }

    private fun resetTabs() {
        val inactiveColor = Color.parseColor("#8FA4BE")

        SettingsTab.values().forEach { tab ->
            tabViewFor(tab).apply {
                setTextColor(inactiveColor)
                setTypeface(null, Typeface.NORMAL)
            }
        }

        hideAllTabContainers()
    }

    private fun hideAllTabContainers() {
        binding.contentScrollView.isVisible = true
        binding.basicFragmentContainer.isVisible = false
        binding.detailsFragmentContainer.isVisible = false
        binding.othersFragmentContainer.isVisible = false
    }

    private fun tabViewFor(
        tab: SettingsTab
    ): TextView {
        return when (tab) {
            SettingsTab.BASIC -> binding.tabBasic
            SettingsTab.DETAILS -> binding.tabDetails
            SettingsTab.OTHERS -> binding.tabOthers
        }
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

        setupHeader(
            scoreText = result.percent.toString(),
            scoreColor = color
        )
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

        saveSelectedTabState(selectedTab)

        if (previousEntry?.destination?.id == R.id.tankDetailFragment) {
            previousEntry.savedStateHandle.set(
                TankDetailFragment.KEY_CARE_PROFILE_ACTION,
                action
            )

            navController.navigateUp()
            return
        }

        navController.navigate(
            R.id.action_tankSettingsFragment_to_tankDetailFragment,
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

    fun openMaterialPickerFlow(
        categoryKey: String,
        categoryTitle: String
    ) {
        selectTab(SettingsTab.DETAILS)

        navigateFromTankSettings(
            actionId = R.id.action_tankSettingsFragment_to_materialPickerFragment,
            args = Bundle().apply {
                putString(
                    MaterialPickerFragment.ARG_MODE,
                    MaterialPickerFragment.MODE_SETTINGS
                )
                putLong(
                    MaterialPickerFragment.ARG_TANK_ID,
                    tankId
                )
                putString(
                    MaterialPickerFragment.ARG_CATEGORY_KEY,
                    categoryKey
                )
                putString(
                    MaterialPickerFragment.ARG_CATEGORY_TITLE,
                    categoryTitle
                )
            }
        )
    }

    private fun navigateFromTankSettings(
        actionId: Int,
        args: Bundle
    ) {
        val navController = findNavController()

        if (navController.currentDestination?.id != R.id.tankSettingsFragment) {
            return
        }

        saveSelectedTabState(selectedTab)

        navController.navigate(
            actionId,
            args
        )
    }

    private fun getBasicFragment(): TankSettingsBasicFragment? {
        return childFragmentManager.findFragmentByTag(
            TAG_BASIC_FRAGMENT
        ) as? TankSettingsBasicFragment
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

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
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

    private enum class SettingsTab {
        BASIC,
        DETAILS,
        OTHERS
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"
        private const val ARG_START_TAB = "startTab"

        private const val START_TAB_DETAILS = "details"
        private const val START_TAB_OTHERS = "others"

        private const val KEY_SELECTED_TAB = "selectedTab"

        private const val TAG_BASIC_FRAGMENT = "TankSettingsBasicFragment"
        private const val TAG_DETAILS_FRAGMENT = "TankSettingsDetailsFragment"
        private const val TAG_OTHERS_FRAGMENT = "TankSettingsOthersFragment"

        private val TAB_ORDER = listOf(
            SettingsTab.BASIC,
            SettingsTab.DETAILS,
            SettingsTab.OTHERS
        )
    }
}