package com.aqua.aqualight.ui.tabs.aquarium.navigation

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit

object AquariumChildTabHost {
    fun showOnce(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        tag: String,
        fragmentFactory: () -> Fragment
    ) {
        if (fragmentManager.findFragmentByTag(tag) != null) {
            return
        }

        fragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                containerId,
                fragmentFactory(),
                tag
            )
        }
    }
}
