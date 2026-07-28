package com.aqua.aqualight.ui.main

import android.content.Intent
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.ui.setupWithNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.ActivityMainBinding
import com.aqua.aqualight.ui.navigation.AppDestinationContract
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import com.aqua.aqualight.ui.navigation.CareTaskNotificationRoutePolicy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Owns MainActivity's transient navigation chrome and deferred notification route state.
 *
 * Authentication remains owned by AppSessionCoordinator; this class only consumes a current
 * snapshot when deciding whether navigation UI or an owner-bound care-task route is safe to show.
 */
internal class MainNavigationCoordinator(
    private val host: BaseActivity,
    private val binding: ActivityMainBinding,
    private val navController: NavController,
    private val restoreSettingsExtra: String,
    private val sessionSnapshot: () -> MainNavigationSessionSnapshot
) {
    private var pendingCareTaskId: Long = -1L
    private var pendingCareTaskOwnerUid: String = ""
    private var bottomBarSetup: Boolean = false
    private var exitFromTopLevelBackCallback: OnBackPressedCallback? = null

    fun captureCareTaskIntent(intent: Intent?) {
        val taskId = intent?.getLongExtra(
            MainActivity.EXTRA_OPEN_CARE_TASK_ID,
            -1L
        ) ?: -1L

        if (taskId <= 0L) {
            return
        }

        pendingCareTaskId = taskId
        pendingCareTaskOwnerUid = intent?.getStringExtra(
            MainActivity.EXTRA_OWNER_UID
        ).orEmpty()
        intent?.removeExtra(MainActivity.EXTRA_OPEN_CARE_TASK_ID)
        intent?.removeExtra(MainActivity.EXTRA_START_IN_APP)
        intent?.removeExtra(MainActivity.EXTRA_OWNER_UID)
    }

    fun clearPendingCareTask() {
        pendingCareTaskId = -1L
        pendingCareTaskOwnerUid = ""
    }

    fun restoreSettingsRootAfterThemeChangeIfNeeded() {
        val shouldRestore = host.intent?.getBooleanExtra(
            restoreSettingsExtra,
            false
        ) == true

        if (!shouldRestore || !sessionSnapshot().isAuthenticated) {
            return
        }

        host.intent?.removeExtra(restoreSettingsExtra)

        binding.root.post {
            val currentDestination = navController.currentDestination
            val restored = if (currentDestination?.id == R.id.settingsFragment) {
                true
            } else {
                runCatching {
                    navController.popBackStack(
                        R.id.settingsFragment,
                        false
                    )
                }.getOrDefault(false)
            }

            if (!restored) {
                selectBottomNavItemSafely(R.id.nav_settings)
            }

            syncBottomBarState(navController.currentDestination)
        }
    }

    fun consumePendingCareTaskIfPossible() {
        val taskId = pendingCareTaskId
        val ownerUid = pendingCareTaskOwnerUid
        val session = sessionSnapshot()

        when {
            taskId <= 0L || !session.isAuthenticated -> Unit
            !CareTaskNotificationRoutePolicy.canOpen(
                taskId = taskId,
                notificationOwnerUid = ownerUid,
                activeOwnerUid = session.activeOwnerUid,
                isAuthenticated = true
            ) -> clearPendingCareTask()

            !AppDestinationContract.isInsideAppGraph(navController.currentDestination) -> Unit
            else -> openPendingCareTask(taskId, ownerUid)
        }
    }

    fun setupBottomBarIfNeeded() {
        if (bottomBarSetup || navController.currentDestination == null) {
            return
        }

        bottomBarSetup = true
        binding.bottomNav.setupWithNavController(navController)

        exitFromTopLevelBackCallback =
            object : OnBackPressedCallback(false) {

                override fun handleOnBackPressed() {
                    host.finish()
                }
            }

        host.onBackPressedDispatcher.addCallback(
            host,
            requireNotNull(exitFromTopLevelBackCallback)
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            syncBottomBarState(destination)
        }
        observeBottomBarBackStack()
        syncBottomBarState(navController.currentDestination)

        binding.root.post {
            syncBottomBarState(navController.currentDestination)
        }
    }

    fun syncBottomBarState(destination: NavDestination?) {
        val shouldShowBottomBar =
            sessionSnapshot().isAuthenticated &&
                AppDestinationContract.shouldShowBottomBar(destination)

        binding.bottomNav.isVisible = shouldShowBottomBar

        if (shouldShowBottomBar) {
            binding.bottomNav.alpha = 1f
            binding.bottomNav.bringToFront()
        }

        exitFromTopLevelBackCallback?.isEnabled =
            shouldShowBottomBar &&
                destination?.id?.let(
                    AppDestinationContract::isTopLevelDestination
                ) == true

        if (AppDestinationContract.isInsideAppGraph(destination)) {
            consumePendingCareTaskIfPossible()
        }
    }

    private fun observeBottomBarBackStack() {
        host.lifecycleScope.launch {
            host.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                navController.currentBackStackEntryFlow.collect { backStackEntry ->
                    syncBottomBarState(backStackEntry.destination)
                }
            }
        }
    }

    private fun openPendingCareTask(taskId: Long, ownerUid: String) {
        clearPendingCareTask()
        binding.navHost.post {
            runCatching {
                AppRouteNavigator.openTaskDetail(
                    navController = navController,
                    taskId = taskId
                )
            }.onFailure {
                pendingCareTaskId = taskId
                pendingCareTaskOwnerUid = ownerUid
            }
        }
    }

    private fun selectBottomNavItemSafely(itemId: Int) {
        if (
            navController.currentDestination == null ||
            binding.bottomNav.menu.findItem(itemId) == null
        ) {
            return
        }

        binding.bottomNav.post {
            runCatching {
                binding.bottomNav.selectedItemId = itemId
            }
        }
    }
}

internal data class MainNavigationSessionSnapshot(
    val isAuthenticated: Boolean,
    val activeOwnerUid: String
)
