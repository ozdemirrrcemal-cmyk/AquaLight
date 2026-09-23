package com.aqua.aqualight.ui.tabs.aquarium.detail

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

internal class TankDetailPagerAdapter(
    fragment: Fragment,
    private val tankId: Long
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int {
        return TANK_DETAIL_TAB_ORDER.size
    }

    override fun createFragment(position: Int): Fragment {
        return when (TANK_DETAIL_TAB_ORDER[position]) {
            TankDetailTab.DEVICES -> TankDetailDevicesFragment.newInstance(tankId)
            TankDetailTab.ACTIVITY -> TankDetailActivityFragment.newInstance(tankId)
            TankDetailTab.TANK -> TankDetailTankFragment.newInstance(tankId)
            TankDetailTab.PLANTS -> TankDetailPlantsFragment.newInstance(tankId)
            TankDetailTab.TANK_LIFE -> TankDetailLifeFragment.newInstance(tankId)
        }
    }

    override fun getItemId(position: Int): Long {
        return TANK_DETAIL_TAB_ORDER[position].stableId
    }

    override fun containsItem(itemId: Long): Boolean {
        return TANK_DETAIL_TAB_ORDER.any { tab ->
            tab.stableId == itemId
        }
    }
}
