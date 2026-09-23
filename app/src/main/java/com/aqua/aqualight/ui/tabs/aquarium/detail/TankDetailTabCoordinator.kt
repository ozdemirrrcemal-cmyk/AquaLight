package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailBinding
import com.aqua.aqualight.ui.common.tabs.AquaSwipeTabHost
import com.aqua.aqualight.ui.common.tabs.AquaSwipeTabSpec
import com.aqua.aqualight.ui.tabs.aquarium.navigation.TankDetailTabArgs

internal class TankDetailTabCoordinator(
    private val navController: NavController
) {

    private var tabHost: AquaSwipeTabHost<TankDetailTab>? = null

    var selectedTab: TankDetailTab = TankDetailTab.DEVICES
        private set

    fun attach(
        fragment: Fragment,
        binding: FragmentTankDetailBinding,
        tankId: Long,
        startTab: String,
        savedInstanceState: Bundle?
    ) {
        selectedTab = restoreSelectedTab(
            startTab = startTab,
            savedInstanceState = savedInstanceState
        )

        tabHost = AquaSwipeTabHost(
            fragment = fragment,
            tabLayout = binding.tankTabs,
            viewPager = binding.tankDetailPager,
            tabs = TANK_DETAIL_TAB_ORDER,
            onTabSelected = { tab ->
                selectedTab = tab
                persistSelection()
            }
        ).also { host ->
            host.attach(
                adapter = TankDetailPagerAdapter(
                    fragment = fragment,
                    tankId = tankId
                ),
                initialTab = selectedTab,
                offscreenPageLimit = TANK_PAGER_OFFSCREEN_LIMIT
            )
        }
    }

    fun select(
        tab: TankDetailTab,
        smoothScroll: Boolean = true
    ) {
        selectedTab = tab
        persistSelection()
        tabHost?.select(
            tab = tab,
            smoothScroll = smoothScroll
        )
    }

    fun persistSelection() {
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.set(
                TankDetailFragment.KEY_SELECTED_TAB,
                selectedTab.name
            )
    }

    fun saveInstanceState(outState: Bundle) {
        outState.putString(
            TankDetailFragment.KEY_SELECTED_TAB,
            selectedTab.name
        )
    }

    fun detach() {
        tabHost?.detach()
        tabHost = null
    }

    private fun restoreSelectedTab(
        startTab: String,
        savedInstanceState: Bundle?
    ): TankDetailTab {
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle

        return savedStateHandle
            ?.remove<String>(TankDetailFragment.KEY_RETURN_TAB)
            ?.let(::tabFromStoredValue)
            ?: savedStateHandle
                ?.get<String>(TankDetailFragment.KEY_SELECTED_TAB)
                ?.let(::tabFromStoredValue)
            ?: savedInstanceState
                ?.getString(TankDetailFragment.KEY_SELECTED_TAB)
                ?.let(::tabFromStoredValue)
            ?: tabFromStoredValue(startTab)
            ?: TankDetailTab.DEVICES
    }

    private fun tabFromStoredValue(value: String): TankDetailTab? {
        return runCatching {
            TankDetailTab.valueOf(value)
        }.getOrNull() ?: when (value) {
            TankDetailTabArgs.ACTIVITY -> TankDetailTab.ACTIVITY
            TankDetailTabArgs.TANK -> TankDetailTab.TANK
            TankDetailTabArgs.PLANTS -> TankDetailTab.PLANTS
            TankDetailTabArgs.TANK_LIFE -> TankDetailTab.TANK_LIFE
            TankDetailTabArgs.DEVICES -> TankDetailTab.DEVICES
            else -> null
        }
    }

    private companion object {
        const val TANK_PAGER_OFFSCREEN_LIMIT = 1
    }
}

internal enum class TankDetailTab(
    @StringRes override val titleRes: Int,
    override val stableId: Long
) : AquaSwipeTabSpec {
    DEVICES(
        R.string.aquarium_detail_tab_devices,
        1L
    ),
    ACTIVITY(
        R.string.aquarium_detail_tab_activity,
        2L
    ),
    TANK(
        R.string.aquarium_detail_tab_tank,
        3L
    ),
    PLANTS(
        R.string.aquarium_detail_tab_plants,
        4L
    ),
    TANK_LIFE(
        R.string.aquarium_tank_life_title,
        5L
    )
}

internal val TANK_DETAIL_TAB_ORDER = listOf(
    TankDetailTab.DEVICES,
    TankDetailTab.ACTIVITY,
    TankDetailTab.TANK,
    TankDetailTab.PLANTS,
    TankDetailTab.TANK_LIFE
)
