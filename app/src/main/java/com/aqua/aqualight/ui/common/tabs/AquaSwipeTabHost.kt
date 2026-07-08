package com.aqua.aqualight.ui.common.tabs

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

interface AquaSwipeTabSpec {
    @get:StringRes
    val titleRes: Int
    val stableId: Long
}

class AquaSwipeTabHost<T : AquaSwipeTabSpec>(
    private val fragment: Fragment,
    private val tabLayout: TabLayout,
    private val viewPager: ViewPager2,
    private val tabs: List<T>,
    private val onTabSelected: (T) -> Unit
) {

    private var selectedTab: T? = null
    private var tabMediator: TabLayoutMediator? = null
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    fun attach(
        adapter: FragmentStateAdapter,
        initialTab: T,
        offscreenPageLimit: Int? = null
    ) {
        detach()

        viewPager.adapter = adapter

        if (offscreenPageLimit != null) {
            viewPager.offscreenPageLimit = offscreenPageLimit
        }

        tabMediator = TabLayoutMediator(
            tabLayout,
            viewPager
        ) { tab, position ->
            tabs.getOrNull(position)?.let { tabSpec ->
                tab.text = fragment.getString(tabSpec.titleRes)
            }
        }.also { mediator ->
            mediator.attach()
        }

        selectedTab = initialTab

        viewPager.setCurrentItem(
            tabIndexOf(initialTab),
            false
        )

        pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(
                position: Int
            ) {
                val tab = tabs.getOrNull(position) ?: return

                if (selectedTab == tab) {
                    return
                }

                selectedTab = tab
                onTabSelected(tab)
            }
        }.also { callback ->
            viewPager.registerOnPageChangeCallback(callback)
        }
    }

    fun select(
        tab: T,
        smoothScroll: Boolean = true
    ) {
        selectedTab = tab
        onTabSelected(tab)

        val targetIndex = tabIndexOf(tab)

        if (viewPager.currentItem != targetIndex) {
            viewPager.setCurrentItem(
                targetIndex,
                smoothScroll
            )
        }
    }

    fun detach() {
        pageChangeCallback?.let { callback ->
            viewPager.unregisterOnPageChangeCallback(callback)
        }
        pageChangeCallback = null

        tabMediator?.detach()
        tabMediator = null

        viewPager.adapter = null
        selectedTab = null
    }

    private fun tabIndexOf(
        tab: T
    ): Int {
        return tabs.indexOf(tab).coerceAtLeast(0)
    }
}
