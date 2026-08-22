package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import androidx.core.content.ContextCompat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.databinding.FragmentTankSettingsBinding
import com.aqua.aqualight.ui.common.bottomsheet.CareProfileBottomSheet
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderScoreBadge
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.tabs.AquaSwipeTabHost
import com.aqua.aqualight.ui.common.tabs.AquaSwipeTabSpec
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.careprofile.CareProfileCalculator
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.detail.TankDetailFragment
import com.aqua.aqualight.ui.tabs.aquarium.navigation.AquariumTabArgs

class TankSettingsFragment : Fragment(R.layout.fragment_tank_settings) {

    private val args: TankSettingsFragmentArgs by navArgs()

    private var _binding: FragmentTankSettingsBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var selectedTab: SettingsTab = SettingsTab.BASIC
    private var currentTank: AquariumTankSnapshot? = null
    private var isDeletingTank: Boolean = false
    private var tabHost: AquaSwipeTabHost<SettingsTab>? = null
    private var careProfileItemsByToken: Map<String, CareProfileCalculator.Item> = emptyMap()

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
        setupResultListeners()
        setupSystemBackButton()
        setupSettingsPager(
            initialTab = selectedTab
        )
        observeTank()
    }


    private fun setupResultListeners() {
        childFragmentManager.setFragmentResultListener(
            CARE_PROFILE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            result.getString(CareProfileBottomSheet.RESULT_TOKEN)
                ?.let(careProfileItemsByToken::get)
                ?.let(::handleCareProfileItemClick)
        }
        childFragmentManager.setFragmentResultListener(
            TANK_MISSING_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, _ -> findNavController().navigateUp() }
    }

    private fun setupHeader(
        scoreText: String? = null,
        scoreColor: Int? = null
    ) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_tank_settings),
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
        tabHost = AquaSwipeTabHost(
            fragment = this,
            tabLayout = binding.settingsTabs,
            viewPager = binding.settingsPager,
            tabs = TAB_ORDER,
            onTabSelected = { tab ->
                selectedTab = tab
                saveSelectedTabState(tab)
            }
        ).also { host ->
            host.attach(
                adapter = TankSettingsPagerAdapter(
                    fragment = this,
                    tankId = tankId
                ),
                initialTab = initialTab,
                offscreenPageLimit = SETTINGS_PAGER_OFFSCREEN_LIMIT
            )
        }
    }

    private fun selectTab(
        tab: SettingsTab,
        smoothScroll: Boolean = true
    ) {
        tabHost?.select(
            tab = tab,
            smoothScroll = smoothScroll
        ) ?: run {
            selectedTab = tab
            saveSelectedTabState(tab)
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

                FeedbackBottomSheet.show(
                    fragmentManager = childFragmentManager,
                    title = getString(R.string.aquarium_tank_not_found_title),
                    message = getString(R.string.aquarium_tank_no_longer_exists_message),
                    primaryText = getString(R.string.ok),
                    cancelText = null,
                    tone = FeedbackBottomSheet.FeedbackTone.ERROR,
                    requestKey = TANK_MISSING_REQUEST_KEY,
                    actionId = ""
                )

                return@observe
            }

            bindTank(tank)
        }
    }

    private fun bindTank(
        tank: AquariumTankSnapshot
    ) {
        currentTank = tank
        renderCareProfileScore(tank)
    }

    fun markTankDeletionInProgress() {
        isDeletingTank = true
    }

    fun markTankDeletionFinished() {
        isDeletingTank = false
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
        tank: AquariumTankSnapshot
    ) {
        val result = CareProfileCalculator.calculate(requireContext(), tank)
        val color = getCareProfileColor(result.percent)

        setupHeader(
            scoreText = result.percent.toString(),
            scoreColor = color
        )
    }

    private fun showCareProfileSheet(tank: AquariumTankSnapshot) {
        val result = CareProfileCalculator.calculate(requireContext(), tank)
        val tokenPairs = result.items.mapIndexed { index, item ->
            buildCareProfileToken(index, item) to item
        }
        careProfileItemsByToken = tokenPairs.toMap()
        CareProfileBottomSheet.show(
            fragmentManager = childFragmentManager,
            percent = result.percent,
            percentText = getString(
                R.string.aquarium_care_profile_percent_format,
                result.percent
            ),
            summaryText = getString(
                R.string.aquarium_care_profile_summary_format,
                result.completedCount,
                result.totalCount
            ),
            profileColor = getCareProfileColor(result.percent),
            titles = result.items.map(CareProfileCalculator.Item::title),
            subtitles = result.items.map(CareProfileCalculator.Item::subtitle),
            completed = result.items.map(CareProfileCalculator.Item::completed).toBooleanArray(),
            tokens = tokenPairs.map { it.first },
            requestKey = CARE_PROFILE_REQUEST_KEY
        )
    }

    private fun buildCareProfileToken(
        index: Int,
        item: CareProfileCalculator.Item
    ): String {
        return buildString {
            append(index)
            append(':')
            append(item.actionKey?.name.orEmpty())
            append(':')
            append(item.materialCategoryKey.orEmpty())
        }
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
            percent < 40 -> ContextCompat.getColor(requireContext(), R.color.aqua_status_danger)
            percent < 75 -> ContextCompat.getColor(requireContext(), R.color.aqua_content_warning)
            else -> ContextCompat.getColor(requireContext(), R.color.aqua_accent_positive)
        }
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
        tabHost?.detach()
        tabHost = null

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
        @StringRes override val titleRes: Int,
        override val stableId: Long
    ) : AquaSwipeTabSpec {
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
        private const val CARE_PROFILE_REQUEST_KEY = "tank_care_profile_result"
        private const val TANK_MISSING_REQUEST_KEY = "tank_settings_missing_result"
        private const val KEY_SELECTED_TAB = "selectedTab"
        private const val SETTINGS_PAGER_OFFSCREEN_LIMIT = 2

        private val TAB_ORDER = listOf(
            SettingsTab.BASIC,
            SettingsTab.DETAILS,
            SettingsTab.OTHERS
        )
    }
}
