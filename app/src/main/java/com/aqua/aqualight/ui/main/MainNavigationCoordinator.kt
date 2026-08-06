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
import com.aqua.aqualight.ui.navigation.DeviceFirmwareNotificationRoutePolicy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Owns MainActivity's transient navigation chrome and deferred notification route state.
 *
 * Authentication remains owned by AppSessionCoordinator; this class only consumes a current
 * snapshot when deciding whether navigation UI or an owner-bound notification route is safe.
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
    private var pendingFirmwareDeviceUid: String = ""
    private var pendingFirmwareOwnerUid: String = ""
    private var bottomBarSetup: Boolean = false
    private var exitFromTopLevelBackCallback: OnBackPressedCallback? = null

    fun captureNotificationIntent(intent: Intent?) {
        val firmwareDeviceUid = intent
            ?.getStringExtra(MainActivity.EXTRA_OPEN_DEVICE_FIRMWARE_UID)
            .orEmpty()
            .trim()
        if (firmwareDeviceUid.isNotBlank()) {
            clearPendingNotifications()
            pendingFirmwareDeviceUid = firmwareDeviceUid
            pendingFirmwareOwnerUid = intent
                ?.getStringExtra(MainActivity.EXTRA_OWNER_UID)
                .orEmpty()
            return
        }

        val taskId = intent?.getLongExtra(
            MainActivity.EXTRA_OPEN_CARE_TASK_ID,
            -1L
        ) ?: -1L
        if (taskId <= 0L) {
            return
        }

        clearPendingNotifications()
        pendingCareTaskId = taskId
        pendingCareTaskOwnerUid = intent
            ?.getStringExtra(MainActivity.EXTRA_OWNER_UID)
            .orEmpty()
    }

    fun clearPendingNotifications() {
        pendingCareTaskId = -1L
        pendingCareTaskOwnerUid = ""
        pendingFirmwareDeviceUid = ""
        pendingFirmwareOwnerUid = ""
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

    fun consumePendingNotificationIfPossible() {
        if (consumePendingFirmwareUpdateIfPossible()) {
            return
        }
        consumePendingCareTaskIfPossible()
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
            consumePendingNotificationIfPossible()
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

    private fun consumePendingFirmwareUpdateIfPossible(): Boolean {
        val deviceUid = pendingFirmwareDeviceUid
        if (deviceUid.isBlank()) {
            return false
        }

        val ownerUid = pendingFirmwareOwnerUid
        val session = sessionSnapshot()
        when {
            !session.isAuthenticated -> return true
            !DeviceFirmwareNotificationRoutePolicy.canOpen(
                deviceUid = deviceUid,
                notificationOwnerUid = ownerUid,
                activeOwnerUid = session.activeOwnerUid,
                isAuthenticated = true
            ) -> {
                clearPendingFirmwareUpdate()
                clearConsumedNotificationExtras(host.intent)
                return false
            }
            !AppDestinationContract.isInsideAppGraph(navController.currentDestination) -> return true
            else -> openPendingFirmwareUpdate(deviceUid, ownerUid)
        }
        return true
    }

    private fun consumePendingCareTaskIfPossible() {
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
            ) -> {
                clearPendingCareTask()
                clearConsumedNotificationExtras(host.intent)
            }

            !AppDestinationContract.isInsideAppGraph(navController.currentDestination) -> Unit
            else -> openPendingCareTask(taskId, ownerUid)
        }
    }

    private fun openPendingFirmwareUpdate(deviceUid: String, ownerUid: String) {
        clearPendingFirmwareUpdate()
        binding.navHost.post {
            runCatching {
                AppRouteNavigator.openDeviceFirmwareUpdate(
                    navController = navController,
                    deviceUid = deviceUid
                )
            }.onSuccess {
                clearConsumedNotificationExtras(host.intent)
            }.onFailure {
                pendingFirmwareDeviceUid = deviceUid
                pendingFirmwareOwnerUid = ownerUid
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
            }.onSuccess {
                clearConsumedNotificationExtras(host.intent)
            }.onFailure {
                pendingCareTaskId = taskId
                pendingCareTaskOwnerUid = ownerUid
            }
        }
    }

    private fun clearPendingFirmwareUpdate() {
        pendingFirmwareDeviceUid = ""
        pendingFirmwareOwnerUid = ""
    }

    private fun clearPendingCareTask() {
        pendingCareTaskId = -1L
        pendingCareTaskOwnerUid = ""
    }

    private fun clearConsumedNotificationExtras(intent: Intent?) {
        intent?.removeExtra(MainActivity.EXTRA_OPEN_CARE_TASK_ID)
        intent?.removeExtra(MainActivity.EXTRA_OPEN_DEVICE_FIRMWARE_UID)
        intent?.removeExtra(MainActivity.EXTRA_START_IN_APP)
        intent?.removeExtra(MainActivity.EXTRA_OWNER_UID)
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
