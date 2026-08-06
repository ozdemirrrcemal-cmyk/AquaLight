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
 * Authentication remains owned by AppSessionCoordinator; this class consumes only the committed
 * session snapshot and application-owned route policies before opening owner-bound destinations.
 */
@Suppress("TooManyFunctions", "LongParameterList")
internal class MainNavigationCoordinator(
    private val host: BaseActivity,
    private val binding: ActivityMainBinding,
    private val navController: NavController,
    private val restoreSettingsExtra: String,
    private val sessionSnapshot: () -> MainNavigationSessionSnapshot,
    private val canOpenDeviceFirmwareUpdate: (String, String) -> Boolean = { _, _ -> false }
) {
    private var pendingCareTaskId: Long = -1L
    private var pendingCareTaskOwnerUid: String = ""
    private var pendingFirmwareDeviceUid: String = ""
    private var pendingFirmwareOwnerUid: String = ""
    private var bottomBarSetup: Boolean = false
    private var exitFromTopLevelBackCallback: OnBackPressedCallback? = null

    fun captureNotificationIntent(intent: Intent?) {
        val ownerUid = intent?.getStringExtra(MainActivity.EXTRA_OWNER_UID).orEmpty()
        val taskId = intent?.getLongExtra(
            MainActivity.EXTRA_OPEN_CARE_TASK_ID,
            -1L
        ) ?: -1L
        val firmwareDeviceUid = intent?.getStringExtra(
            MainActivity.EXTRA_OPEN_DEVICE_FIRMWARE_UPDATE_UID
        ).orEmpty()

        if (taskId > 0L) {
            pendingCareTaskId = taskId
            pendingCareTaskOwnerUid = ownerUid
        }
        if (firmwareDeviceUid.isNotBlank()) {
            pendingFirmwareDeviceUid = firmwareDeviceUid.trim()
            pendingFirmwareOwnerUid = ownerUid
        }

        intent?.removeExtra(MainActivity.EXTRA_OPEN_CARE_TASK_ID)
        intent?.removeExtra(MainActivity.EXTRA_OPEN_DEVICE_FIRMWARE_UPDATE_UID)
        intent?.removeExtra(MainActivity.EXTRA_START_IN_APP)
        intent?.removeExtra(MainActivity.EXTRA_OWNER_UID)
    }

    fun captureCareTaskIntent(intent: Intent?) {
        captureNotificationIntent(intent)
    }

    fun clearPendingNotifications() {
        clearPendingCareTask()
        clearPendingFirmwareUpdate()
    }

    fun clearPendingCareTask() {
        pendingCareTaskId = -1L
        pendingCareTaskOwnerUid = ""
    }

    private fun clearPendingFirmwareUpdate() {
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

    fun consumePendingNotificationsIfPossible() {
        consumePendingCareTaskIfPossible()
        consumePendingFirmwareUpdateIfPossible()
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

    private fun consumePendingFirmwareUpdateIfPossible() {
        val deviceUid = pendingFirmwareDeviceUid
        val ownerUid = pendingFirmwareOwnerUid
        val session = sessionSnapshot()

        when {
            deviceUid.isBlank() || !session.isAuthenticated -> Unit
            ownerUid != session.activeOwnerUid -> clearPendingFirmwareUpdate()
            !canOpenDeviceFirmwareUpdate(ownerUid, deviceUid) -> clearPendingFirmwareUpdate()
            !AppDestinationContract.isInsideAppGraph(navController.currentDestination) -> Unit
            else -> openPendingFirmwareUpdate(deviceUid, ownerUid)
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
            consumePendingNotificationsIfPossible()
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

    private fun openPendingFirmwareUpdate(deviceUid: String, ownerUid: String) {
        clearPendingFirmwareUpdate()
        val devicesItem = binding.bottomNav.menu.findItem(R.id.nav_devices)
        if (devicesItem == null) {
            pendingFirmwareDeviceUid = deviceUid
            pendingFirmwareOwnerUid = ownerUid
            return
        }

        binding.bottomNav.post {
            runCatching {
                binding.bottomNav.selectedItemId = R.id.nav_devices
                binding.navHost.post {
                    runCatching {
                        AppRouteNavigator.openDeviceFirmwareUpdate(
                            navController = navController,
                            deviceUid = deviceUid
                        )
                    }.onFailure {
                        pendingFirmwareDeviceUid = deviceUid
                        pendingFirmwareOwnerUid = ownerUid
                    }
                }
            }.onFailure {
                pendingFirmwareDeviceUid = deviceUid
                pendingFirmwareOwnerUid = ownerUid
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
