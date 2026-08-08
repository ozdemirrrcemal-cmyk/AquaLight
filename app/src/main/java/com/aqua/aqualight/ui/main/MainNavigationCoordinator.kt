@file:Suppress("ReturnCount", "TooManyFunctions")

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
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteDecision
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteRequest
import com.aqua.aqualight.application.notifications.DeviceFirmwareNotificationIntentContract
import com.aqua.aqualight.application.notifications.DeviceFirmwareNotificationKind
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.ActivityMainBinding
import com.aqua.aqualight.ui.navigation.AppDestinationContract
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import com.aqua.aqualight.ui.navigation.CareTaskNotificationRoutePolicy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Owns MainActivity navigation chrome and deferred owner-bound notification routes. */
internal class MainNavigationCoordinator(
    private val host: BaseActivity,
    private val binding: ActivityMainBinding,
    private val navController: NavController,
    private val restoreSettingsExtra: String,
    private val sessionSnapshot: () -> MainNavigationSessionSnapshot,
    deviceFirmwareRouteOperations: DeviceFirmwareNotificationRouteOperations
) {
    private val firmwareRouteGate = DeviceFirmwareNotificationRouteGate(
        sessionSnapshot = sessionSnapshot,
        routeOperations = deviceFirmwareRouteOperations
    )
    private var pendingCareTaskId: Long = -1L
    private var pendingCareTaskOwnerUid: String = ""
    private var pendingFirmwareDeviceUid: String = ""
    private var pendingFirmwareOwnerUid: String = ""
    private var pendingFirmwareKind: DeviceFirmwareNotificationKind? = null
    private var pendingFirmwareTargetVersion: String = ""
    private var nextFirmwareNavigationAttempt: Long = 0L
    private var activeFirmwareNavigationAttempt: Long? = null
    private var bottomBarSetup: Boolean = false
    private var exitFromTopLevelBackCallback: OnBackPressedCallback? = null

    fun captureNotificationIntent(intent: Intent?) {
        val firmwareDeviceUid = intent
            ?.getStringExtra(MainActivity.EXTRA_OPEN_DEVICE_FIRMWARE_UID)
            .orEmpty()
            .trim()
        if (firmwareDeviceUid.isNotBlank()) {
            clearPendingNotifications()
            val kind = DeviceFirmwareNotificationIntentContract.parseKind(
                intent?.getStringExtra(DeviceFirmwareNotificationIntentContract.EXTRA_KIND)
            )
            val targetVersion = intent
                ?.getStringExtra(
                    DeviceFirmwareNotificationIntentContract.EXTRA_TARGET_VERSION
                )
                .orEmpty()
                .trim()
            val validRequest = kind != null &&
                (kind != DeviceFirmwareNotificationKind.AVAILABILITY || targetVersion.isNotBlank())
            if (validRequest) {
                pendingFirmwareDeviceUid = firmwareDeviceUid
                pendingFirmwareOwnerUid = intent
                    ?.getStringExtra(MainActivity.EXTRA_OWNER_UID)
                    .orEmpty()
                pendingFirmwareKind = kind
                pendingFirmwareTargetVersion = targetVersion
            } else {
                clearConsumedNotificationExtras(intent)
            }
            return
        }

        val taskId = intent?.getLongExtra(
            MainActivity.EXTRA_OPEN_CARE_TASK_ID,
            -1L
        ) ?: -1L
        if (taskId <= 0L) return

        clearPendingNotifications()
        pendingCareTaskId = taskId
        pendingCareTaskOwnerUid = intent
            ?.getStringExtra(MainActivity.EXTRA_OWNER_UID)
            .orEmpty()
    }

    fun clearPendingNotifications() {
        clearPendingCareTask()
        clearPendingFirmwareUpdate()
    }

    fun restoreSettingsRootAfterThemeChangeIfNeeded() {
        val shouldRestore = host.intent?.getBooleanExtra(
            restoreSettingsExtra,
            false
        ) == true
        if (!shouldRestore || !sessionSnapshot().isAuthenticated) return

        host.intent?.removeExtra(restoreSettingsExtra)
        binding.root.post {
            val currentDestination = navController.currentDestination
            val restored = currentDestination?.id == R.id.settingsFragment ||
                runCatching {
                    navController.popBackStack(R.id.settingsFragment, false)
                }.getOrDefault(false)
            if (!restored) selectBottomNavItemSafely(R.id.nav_settings)
            syncBottomBarState(navController.currentDestination)
        }
    }

    fun consumePendingNotificationIfPossible() {
        if (!consumePendingFirmwareUpdateIfPossible()) {
            consumePendingCareTaskIfPossible()
        }
    }

    fun setupBottomBarIfNeeded() {
        if (bottomBarSetup || navController.currentDestination == null) return

        bottomBarSetup = true
        binding.bottomNav.setupWithNavController(navController)
        exitFromTopLevelBackCallback = object : OnBackPressedCallback(false) {
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
        val shouldShowBottomBar = sessionSnapshot().isAuthenticated &&
            AppDestinationContract.shouldShowBottomBar(destination)
        binding.bottomNav.isVisible = shouldShowBottomBar
        if (shouldShowBottomBar) {
            binding.bottomNav.alpha = 1f
            binding.bottomNav.bringToFront()
        }
        exitFromTopLevelBackCallback?.isEnabled = shouldShowBottomBar &&
            destination?.id?.let(AppDestinationContract::isTopLevelDestination) == true
        if (AppDestinationContract.isInsideAppGraph(destination)) {
            consumePendingNotificationIfPossible()
        }
    }

    private fun observeBottomBarBackStack() {
        host.lifecycleScope.launch {
            host.repeatOnLifecycle(Lifecycle.State.STARTED) {
                navController.currentBackStackEntryFlow.collect { backStackEntry ->
                    syncBottomBarState(backStackEntry.destination)
                }
            }
        }
    }

    private fun consumePendingFirmwareUpdateIfPossible(): Boolean {
        val request = pendingFirmwareRequest() ?: return false
        val ownerUid = pendingFirmwareOwnerUid
        return when (firmwareRouteGate.evaluate(request, ownerUid)) {
            DeviceFirmwareNotificationRouteDecision.DEFER -> true
            DeviceFirmwareNotificationRouteDecision.REJECT -> {
                rejectPendingFirmwareUpdate()
                false
            }
            DeviceFirmwareNotificationRouteDecision.OPEN -> {
                if (AppDestinationContract.isInsideAppGraph(
                        navController.currentDestination
                    )
                ) {
                    openPendingFirmwareUpdate(request, ownerUid)
                }
                true
            }
        }
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

    private fun openPendingFirmwareUpdate(
        request: DeviceFirmwareNotificationRouteRequest,
        ownerUid: String
    ) {
        if (activeFirmwareNavigationAttempt != null) return

        nextFirmwareNavigationAttempt += 1L
        val attempt = nextFirmwareNavigationAttempt
        activeFirmwareNavigationAttempt = attempt
        binding.navHost.post {
            if (!isCurrentFirmwareAttempt(attempt, request, ownerUid)) {
                return@post
            }
            when (firmwareRouteGate.evaluate(request, ownerUid)) {
                DeviceFirmwareNotificationRouteDecision.DEFER ->
                    activeFirmwareNavigationAttempt = null
                DeviceFirmwareNotificationRouteDecision.REJECT ->
                    rejectPendingFirmwareUpdate()
                DeviceFirmwareNotificationRouteDecision.OPEN ->
                    navigateToFirmwareUpdate(attempt, request, ownerUid)
            }
        }
    }

    private fun navigateToFirmwareUpdate(
        attempt: Long,
        request: DeviceFirmwareNotificationRouteRequest,
        ownerUid: String
    ) {
        runCatching {
            AppRouteNavigator.openDeviceFirmwareUpdate(navController, request.deviceUid)
        }.onSuccess {
            if (activeFirmwareNavigationAttempt == attempt) {
                clearPendingFirmwareUpdate()
                clearConsumedNotificationExtras(host.intent)
                host.lifecycleScope.launch {
                    firmwareRouteGate.acknowledgeOpened(request, ownerUid)
                }
            }
        }.onFailure {
            if (activeFirmwareNavigationAttempt == attempt) {
                activeFirmwareNavigationAttempt = null
            }
        }
    }

    private fun openPendingCareTask(taskId: Long, ownerUid: String) {
        clearPendingCareTask()
        binding.navHost.post {
            runCatching {
                AppRouteNavigator.openTaskDetail(navController, taskId)
            }.onSuccess {
                clearConsumedNotificationExtras(host.intent)
            }.onFailure {
                pendingCareTaskId = taskId
                pendingCareTaskOwnerUid = ownerUid
            }
        }
    }

    private fun isCurrentFirmwareAttempt(
        attempt: Long,
        request: DeviceFirmwareNotificationRouteRequest,
        ownerUid: String
    ): Boolean {
        return activeFirmwareNavigationAttempt == attempt &&
            pendingFirmwareDeviceUid == request.deviceUid &&
            pendingFirmwareKind == request.kind &&
            pendingFirmwareTargetVersion == request.targetVersion &&
            pendingFirmwareOwnerUid.trim() == ownerUid.trim()
    }

    private fun pendingFirmwareRequest(): DeviceFirmwareNotificationRouteRequest? {
        val kind = pendingFirmwareKind ?: return null
        val deviceUid = pendingFirmwareDeviceUid.trim()
        if (deviceUid.isBlank()) return null
        return DeviceFirmwareNotificationRouteRequest(
            deviceUid = deviceUid,
            kind = kind,
            targetVersion = pendingFirmwareTargetVersion.trim()
        )
    }

    private fun rejectPendingFirmwareUpdate() {
        clearPendingFirmwareUpdate()
        clearConsumedNotificationExtras(host.intent)
    }

    private fun clearPendingFirmwareUpdate() {
        pendingFirmwareDeviceUid = ""
        pendingFirmwareOwnerUid = ""
        pendingFirmwareKind = null
        pendingFirmwareTargetVersion = ""
        activeFirmwareNavigationAttempt = null
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
        intent?.removeExtra(DeviceFirmwareNotificationIntentContract.EXTRA_KIND)
        intent?.removeExtra(DeviceFirmwareNotificationIntentContract.EXTRA_TARGET_VERSION)
    }

    private fun selectBottomNavItemSafely(itemId: Int) {
        val destinationReady = navController.currentDestination != null
        val itemExists = binding.bottomNav.menu.findItem(itemId) != null
        if (!destinationReady || !itemExists) return

        binding.bottomNav.post {
            runCatching { binding.bottomNav.selectedItemId = itemId }
        }
    }
}

internal data class MainNavigationSessionSnapshot(
    val isAuthenticated: Boolean,
    val activeOwnerUid: String
)
