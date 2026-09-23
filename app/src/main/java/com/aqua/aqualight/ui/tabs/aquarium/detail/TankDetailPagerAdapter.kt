package com.aqua.aqualight.ui.tabs.aquarium.detail

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

internal class TankDetailPagerAdapter(
    fragment: Fragment,
    private val tankId: Long
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int {
        return tankDetailTabOrder.size
    }

    override fun createFragment(position: Int): Fragment {
        return when (tankDetailTabOrder[position]) {
            TankDetailTab.DEVICES -> TankDetailDevicesFragment.newInstance(tankId)
            TankDetailTab.ACTIVITY -> TankDetailActivityFragment.newInstance(tankId)
            TankDetailTab.TANK -> TankDetailTankFragment.newInstance(tankId)
            TankDetailTab.PLANTS -> TankDetailPlantsFragment.newInstance(tankId)
            TankDetailTab.TANK_LIFE -> TankDetailLifeFragment.newInstance(tankId)
        }
    }

    override fun getItemId(position: Int): Long {
        return tankDetailTabOrder[position].stableId
    }

    override fun containsItem(itemId: Long): Boolean {
        return tankDetailTabOrder.any { tab ->
            tab.stableId == itemId
        }
    }
}
