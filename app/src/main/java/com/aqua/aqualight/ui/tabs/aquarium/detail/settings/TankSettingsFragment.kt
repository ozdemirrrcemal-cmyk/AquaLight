package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
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
import com.aqua.aqualight.ui.tabs.aquarium.navigation.AquariumTabArgs
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.math.roundToInt

class TankSettingsFragment : Fragment(R.layout.fragment_tank_settings) {

    private val args: TankSettingsFragmentArgs by navArgs()

    private var _binding: FragmentTankSettingsBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var selectedTab: SettingsTab = SettingsTab.BASIC
    private var currentTank: SavedAquariumTank? = null
    private var isDeletingTank: Boolean = false
    private var tabMediator: TabLayoutMediator? = null
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

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

        _binding = FragmentTankSettingsBinding.bind(view)

        selectedTab = restoreSelectedTab(savedInstanceState)

        setupHeader()
        setupSystemBackButton()
        setupSettingsPager(
            initialTab = selectedTab
        )
        observeTank()
    }

    private fun setupHeader(
        scoreText: String? = null,
        scoreColor: Int? = null
    ) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_settings),
                onBackClick = {
                    findNavController().navigateUp()
                },
                scoreBadge = if (scoreText != null && scoreColor != null) {
                    AquaHeaderScoreBadge(
                        text = scoreText,
                        strokeColor = scoreColor,
                        textColor = scoreColor,
                        contentDescription = getString(R.string.aquarium_content_desc_tank_score),
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

    private fun setupSettingsPager(
        initialTab: SettingsTab
    ) {
        binding.settingsPager.adapter =
            TankSettingsPagerAdapter(
                fragment = this,
                tankId = tankId
            )

        binding.settingsPager.offscreenPageLimit =
            SETTINGS_PAGER_OFFSCREEN_LIMIT

        tabMediator =
            TabLayoutMediator(
                binding.settingsTabs,
                binding.settingsPager
            ) { tab, position ->
                tab.text = getString(
                    TAB_ORDER[position].titleRes
                )
            }.also { mediator ->
                mediator.attach()
            }

        binding.settingsPager.setCurrentItem(
            tabIndexOf(
                tab = initialTab
            ),
            false
        )

        pageChangeCallback =
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(
                    position: Int
                ) {
                    val tab = TAB_ORDER.getOrNull(
                        position
                    ) ?: return

                    if (selectedTab == tab) {
                        return
                    }

                    selectedTab = tab
                    saveSelectedTabState(tab)
                }
            }.also { callback ->
                binding.settingsPager.registerOnPageChangeCallback(
                    callback
                )
            }
    }

    private fun selectTab(
        tab: SettingsTab,
        smoothScroll: Boolean = true
    ) {
        selectedTab = tab
        saveSelectedTabState(tab)

        val targetIndex = tabIndexOf(tab)

        if (binding.settingsPager.currentItem != targetIndex) {
            binding.settingsPager.setCurrentItem(
                targetIndex,
                smoothScroll
            )
        }
    }

    private fun tabIndexOf(
        tab: SettingsTab
    ): Int {
        return TAB_ORDER.indexOf(tab).coerceAtLeast(0)
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
                    title = getString(R.string.aquarium_tank_not_found_title),
                    message = getString(R.string.aquarium_tank_no_longer_exists_message),
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

    private fun getInitialTab(): SettingsTab {
        val startTab = args.startTab

        return when (startTab) {
            AquariumTabArgs.DETAILS -> SettingsTab.DETAILS
            AquariumTabArgs.OTHERS -> SettingsTab.OTHERS
            AquariumTabArgs.BASIC -> SettingsTab.BASIC
            else -> SettingsTab.BASIC
        }
    }

    private fun renderCareProfileScore(
        tank: SavedAquariumTank
    ) {
        val result = CareProfileCalculator.calculate(requireContext(), tank)
        val color = getCareProfileColor(result.percent)

        setupHeader(
            scoreText = result.percent.toString(),
            scoreColor = color
        )
    }

    private fun showCareProfileSheet(
        tank: SavedAquariumTank
    ) {
        val result = CareProfileCalculator.calculate(requireContext(), tank)
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = DialogCareProfileBinding.inflate(layoutInflater)

        val profileColor = getCareProfileColor(result.percent)

        sheetBinding.tvCareProfilePercent.text = getString(
            R.string.aquarium_care_profile_percent_format,
            result.percent
        )
        sheetBinding.tvCareProfileSummary.text = getString(
            R.string.aquarium_care_profile_summary_format,
            result.completedCount,
            result.totalCount
        )

        sheetBinding.careProgressTrack.background = createRoundedDrawable(
            color = "#DDE3EA",
            radiusPx = 3.dp()
        )

        sheetBinding.careProgressFill.background = createRoundedDrawable(
            color = colorToHex(profileColor),
            radiusPx = 3.dp()
        )

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
                getString(R.string.aquarium_care_profile_status_complete)
            } else {
                getString(R.string.aquarium_care_profile_status_missing)
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

            binding.settingsPager.post {
                openMaterialPickerFlow(
                    categoryKey = item.materialCategoryKey,
                    categoryTitle = item.materialCategoryTitle
                )
            }

            return
        }

        when (item.actionKey) {
            CareProfileCalculator.ActionKey.TANK_NAME -> {
                openBasicCareProfileAction(
                    TankSettingsBasicFragment.ACTION_TANK_NAME
                )
            }

            CareProfileCalculator.ActionKey.TANK_TYPE -> {
                openBasicCareProfileAction(
                    TankSettingsBasicFragment.ACTION_TANK_TYPE
                )
            }

            CareProfileCalculator.ActionKey.TANK_SIZE -> {
                openBasicCareProfileAction(
                    TankSettingsBasicFragment.ACTION_TANK_SIZE
                )
            }

            CareProfileCalculator.ActionKey.SETUP_DATE -> {
                openBasicCareProfileAction(
                    TankSettingsBasicFragment.ACTION_SETUP_DATE
                )
            }

            CareProfileCalculator.ActionKey.TANK_STYLE -> {
                openBasicCareProfileAction(
                    TankSettingsBasicFragment.ACTION_STYLE
                )
            }

            CareProfileCalculator.ActionKey.PLANTS -> {
                openTankDetailCareProfileAction(
                    TankDetailFragment.CARE_PROFILE_ACTION_PLANTS
                )
            }

            CareProfileCalculator.ActionKey.LIVESTOCK -> {
                openTankDetailCareProfileAction(
                    TankDetailFragment.CARE_PROFILE_ACTION_LIVESTOCK
                )
            }

            null -> Unit
        }
    }

    private fun openBasicCareProfileAction(
        action: String
    ) {
        selectTab(SettingsTab.BASIC)

        binding.settingsPager.post {
            childFragmentManager.executePendingTransactions()

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

        if (navController.currentDestination?.id != R.id.tankSettingsFragment) {
            return
        }

        navController.navigate(
            TankSettingsFragmentDirections.actionTankSettingsFragmentToTankDetailFragment(
                tankId = tankId
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
            TankSettingsFragmentDirections.actionTankSettingsFragmentToMaterialPickerFragment(
                argMode = MaterialPickerFragment.MODE_SETTINGS,
                argTankId = tankId,
                argCategoryKey = categoryKey,
                argCategoryTitle = categoryTitle
            )
        )
    }

    private fun navigateFromTankSettings(
        directions: NavDirections
    ) {
        val navController = findNavController()

        if (navController.currentDestination?.id != R.id.tankSettingsFragment) {
            return
        }

        saveSelectedTabState(selectedTab)

        navController.navigate(
            directions
        )
    }

    private fun getBasicFragment(): TankSettingsBasicFragment? {
        return childFragmentManager.fragments
            .filterIsInstance<TankSettingsBasicFragment>()
            .firstOrNull()
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
        pageChangeCallback?.let { callback ->
            binding.settingsPager.unregisterOnPageChangeCallback(
                callback
            )
        }
        pageChangeCallback = null

        tabMediator?.detach()
        tabMediator = null

        binding.settingsPager.adapter = null

        _binding = null

        super.onDestroyView()
    }

    private class TankSettingsPagerAdapter(
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
                SettingsTab.BASIC -> TankSettingsBasicFragment.newInstance(tankId)
                SettingsTab.DETAILS -> TankSettingsDetailsFragment.newInstance(tankId)
                SettingsTab.OTHERS -> TankSettingsOthersFragment.newInstance(tankId)
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

    private enum class SettingsTab(
        @StringRes val titleRes: Int,
        val stableId: Long
    ) {
        BASIC(
            R.string.aquarium_settings_tab_basic,
            1L
        ),
        DETAILS(
            R.string.aquarium_settings_tab_details,
            2L
        ),
        OTHERS(
            R.string.aquarium_settings_tab_others,
            3L
        )
    }

    companion object {
        private const val KEY_SELECTED_TAB = "selectedTab"
        private const val SETTINGS_PAGER_OFFSCREEN_LIMIT = 2

        private val TAB_ORDER = listOf(
            SettingsTab.BASIC,
            SettingsTab.DETAILS,
            SettingsTab.OTHERS
        )
    }
}
