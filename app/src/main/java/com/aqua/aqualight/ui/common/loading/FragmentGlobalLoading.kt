package com.aqua.aqualight.ui.common.loading

import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.aqua.aqualight.base.BaseActivity
import java.util.Collections
import java.util.WeakHashMap

private val observedFragments =
    Collections.newSetFromMap(
        WeakHashMap<Fragment, Boolean>()
    )

fun Fragment.setFragmentGlobalLoading(
    show: Boolean
) {
    val baseActivity = activity as? BaseActivity
        ?: return

    val ownerKey = globalLoadingOwnerKey()

    if (show) {
        registerGlobalLoadingAutoClear(
            ownerKey = ownerKey
        )
    }

    baseActivity.setGlobalLoading(
        ownerKey = ownerKey,
        show = show
    )
}

fun Fragment.clearFragmentGlobalLoading() {
    val baseActivity = activity as? BaseActivity
        ?: return

    baseActivity.clearGlobalLoading(
        ownerKey = globalLoadingOwnerKey()
    )
}

private fun Fragment.globalLoadingOwnerKey(): String {
    return "${this::class.java.name}@${System.identityHashCode(this)}"
}

private fun Fragment.registerGlobalLoadingAutoClear(
    ownerKey: String
) {
    if (!observedFragments.add(this)) {
        return
    }

    viewLifecycleOwner.lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(
                owner: LifecycleOwner
            ) {
                (activity as? BaseActivity)?.clearGlobalLoading(
                    ownerKey = ownerKey
                )

                observedFragments.remove(
                    this@registerGlobalLoadingAutoClear
                )
            }
        }
    )
}
