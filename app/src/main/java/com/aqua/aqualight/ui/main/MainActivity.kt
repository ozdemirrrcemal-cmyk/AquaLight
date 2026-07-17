package com.aqua.aqualight.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.auth.AppSessionCoordinator
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.databinding.ActivityMainBinding
import com.aqua.aqualight.ui.navigation.AppDestinationContract
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import com.aqua.aqualight.ui.navigation.CareTaskNotificationRoutePolicy
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    companion object {
        @Deprecated("Startup graph selection is owned by AppSessionCoordinator.")
        const val EXTRA_START_IN_APP = "EXTRA_START_IN_APP"
        const val EXTRA_OPEN_CARE_TASK_ID = "EXTRA_OPEN_CARE_TASK_ID"
        const val EXTRA_OWNER_UID = "EXTRA_OWNER_UID"
        private const val EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE =
            "EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val appSessionCoordinator by lazy {
        AppSessionCoordinator.create(applicationContext)
    }

    private var isAuthenticated: Boolean = false
    private var activeOwnerUid: String = ""
    private var renderedSessionKey: String? = null
    private var lastShownStartupFailure: Throwable? = null
    private var pendingCareTaskId: Long = -1L
    private var pendingCareTaskOwnerUid: String = ""
    private var bottomBarSetup: Boolean = false
    private var exitFromTopLevelBackCallback: OnBackPressedCallback? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(
            R.id.nav_host
        ) as NavHostFragment

        navController = navHost.navController

        binding.navHost.isVisible = false
        binding.bottomNav.isVisible = false

        captureCareTaskIntent(intent)
        observeSessionState()
        appSessionCoordinator.start()
    }

    override fun onStart() {
        super.onStart()
        appSessionCoordinator.enterForeground()
    }

    override fun onStop() {
        appSessionCoordinator.leaveForeground()
        super.onStop()
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(intent)

        setIntent(intent)
        captureCareTaskIntent(intent)
        appSessionCoordinator.requestReconcile()
        consumePendingCareTaskIfPossible()
    }

    override fun onPostResume() {
        super.onPostResume()

        if (
            !::binding.isInitialized ||
            !::navController.isInitialized
        ) {
            return
        }

        binding.root.post {
            restoreSettingsRootAfterThemeChangeIfNeeded()
            syncBottomBarState(
                navController.currentDestination
            )
        }
    }

    fun clearSessionNavigationState() {
        isAuthenticated = false
        activeOwnerUid = ""
        renderedSessionKey = null
        pendingCareTaskId = -1L
        pendingCareTaskOwnerUid = ""

        intent?.removeExtra(EXTRA_OPEN_CARE_TASK_ID)
        intent?.removeExtra(EXTRA_START_IN_APP)
        intent?.removeExtra(EXTRA_OWNER_UID)
        intent?.removeExtra(EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE)
    }

    fun markSettingsRootRestoreAfterThemeChange() {
        intent?.putExtra(
            EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE,
            true
        )
    }

    private fun observeSessionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                appSessionCoordinator.state.collect { state ->
                    renderSessionState(state)
                }
            }
        }
    }

    private fun renderSessionState(
        state: AppSessionCoordinator.State
    ) {
        when (state) {
            AppSessionCoordinator.State.Starting -> {
                binding.navHost.isVisible = false
                binding.bottomNav.isVisible = false
            }

            is AppSessionCoordinator.State.Authenticated -> {
                val sessionKey = "authenticated:${state.ownerUid}"
                val graphMustBeReplaced =
                    renderedSessionKey != sessionKey ||
                        !AppDestinationContract.isInsideAppGraph(
                            navController.currentDestination
                        )

                isAuthenticated = true
                activeOwnerUid = state.ownerUid
                lastShownStartupFailure = null

                if (graphMustBeReplaced) {
                    installRootGraph(startInApp = true)
                    renderedSessionKey = sessionKey
                }

                setupBottomBarIfNeeded(navController)
                binding.navHost.isVisible = true
                showLocalDataRecoveryIfNeeded()
                restoreSettingsRootAfterThemeChangeIfNeeded()
                consumePendingCareTaskIfPossible()
                syncBottomBarState(navController.currentDestination)
            }

            AppSessionCoordinator.State.Unauthenticated -> {
                val sessionKey = "unauthenticated"
                isAuthenticated = false
                activeOwnerUid = ""
                lastShownStartupFailure = null

                if (renderedSessionKey != sessionKey) {
                    installRootGraph(startInApp = false)
                    renderedSessionKey = sessionKey
                }

                setupBottomBarIfNeeded(navController)
                binding.navHost.isVisible = true
                binding.bottomNav.isVisible = false
                syncBottomBarState(navController.currentDestination)
            }

            is AppSessionCoordinator.State.Failure -> {
                isAuthenticated = false
                activeOwnerUid = ""

                if (renderedSessionKey != "startup-failure") {
                    installRootGraph(startInApp = false)
                    renderedSessionKey = "startup-failure"
                }

                setupBottomBarIfNeeded(navController)
                binding.navHost.isVisible = true
                binding.bottomNav.isVisible = false
                showSessionStartupFailureIfNeeded(state.error)
            }
        }
    }

    private fun showSessionStartupFailureIfNeeded(
        error: Throwable
    ) {
        if (lastShownStartupFailure === error) {
            return
        }
        lastShownStartupFailure = error

        DialogManager.showInfoDialog(
            context = this,
            type = DialogType.ERROR,
            title = getString(R.string.session_startup_failed_title),
            message = getString(R.string.session_startup_failed_message)
        )
    }

    private fun showLocalDataRecoveryIfNeeded() {
        if (!isAuthenticated) return

        val recoveredAreas = LocalDataRecoveryTracker.consumeRecoveredAreas()
        if (recoveredAreas.isEmpty()) return

        val messageRes = when (recoveredAreas) {
            setOf(LocalDataRecoveryTracker.Area.AQUARIUM_TANKS) ->
                R.string.local_data_recovery_tanks_message

            setOf(LocalDataRecoveryTracker.Area.CARE_TASKS) ->
                R.string.local_data_recovery_care_tasks_message

            setOf(LocalDataRecoveryTracker.Area.KNOWN_DEVICES) ->
                R.string.local_data_recovery_devices_message

            setOf(LocalDataRecoveryTracker.Area.TANK_DEVICE_ASSIGNMENTS) ->
                R.string.local_data_recovery_assignments_message

            else -> R.string.local_data_recovery_combined_message
        }

        DialogManager.showInfoDialog(
            context = this,
            type = DialogType.WARNING,
            title = getString(R.string.local_data_recovery_title),
            message = getString(messageRes)
        )
    }

    private fun installRootGraph(
        startInApp: Boolean
    ) {
        val graph = navController.navInflater.inflate(
            R.navigation.nav_root
        ).apply {
            setStartDestination(
                if (startInApp) {
                    R.id.nav_app
                } else {
                    R.id.authContainerFragment
                }
            )
        }

        navController.graph = graph
    }

    private fun restoreSettingsRootAfterThemeChangeIfNeeded() {
        val shouldRestore = intent?.getBooleanExtra(
            EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE,
            false
        ) == true

        if (!shouldRestore || !isAuthenticated) {
            return
        }

        intent?.removeExtra(
            EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE
        )

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
                selectBottomNavItemSafely(
                    R.id.nav_settings
                )
            }

            syncBottomBarState(
                navController.currentDestination
            )
        }
    }

    private fun selectBottomNavItemSafely(
        itemId: Int
    ) {
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

    private fun captureCareTaskIntent(
        intent: Intent?
    ) {
        val taskId = intent?.getLongExtra(
            EXTRA_OPEN_CARE_TASK_ID,
            -1L
        ) ?: -1L

        if (taskId <= 0L) {
            return
        }

        val ownerUid = intent?.getStringExtra(
            EXTRA_OWNER_UID
        ).orEmpty()

        pendingCareTaskId = taskId
        pendingCareTaskOwnerUid = ownerUid
        intent?.removeExtra(EXTRA_OPEN_CARE_TASK_ID)
        intent?.removeExtra(EXTRA_START_IN_APP)
        intent?.removeExtra(EXTRA_OWNER_UID)
    }

    private fun consumePendingCareTaskIfPossible() {
        val taskId = pendingCareTaskId
        val ownerUid = pendingCareTaskOwnerUid

        if (taskId <= 0L || !isAuthenticated) {
            return
        }

        if (
            !CareTaskNotificationRoutePolicy.canOpen(
                taskId = taskId,
                notificationOwnerUid = ownerUid,
                activeOwnerUid = activeOwnerUid,
                isAuthenticated = true
            )
        ) {
            pendingCareTaskId = -1L
            pendingCareTaskOwnerUid = ""
            return
        }

        if (!AppDestinationContract.isInsideAppGraph(navController.currentDestination)) {
            return
        }

        pendingCareTaskId = -1L
        pendingCareTaskOwnerUid = ""

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

    private fun setupBottomBarIfNeeded(
        navController: NavController
    ) {
        if (bottomBarSetup || navController.currentDestination == null) {
            return
        }

        bottomBarSetup = true

        binding.bottomNav.setupWithNavController(navController)

        exitFromTopLevelBackCallback =
            object : OnBackPressedCallback(false) {

                override fun handleOnBackPressed() {
                    finish()
                }
            }

        onBackPressedDispatcher.addCallback(
            this,
            requireNotNull(exitFromTopLevelBackCallback)
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            syncBottomBarState(destination)
        }

        observeBottomBarBackStack(navController)

        syncBottomBarState(
            navController.currentDestination
        )

        binding.root.post {
            syncBottomBarState(
                navController.currentDestination
            )
        }
    }

    private fun observeBottomBarBackStack(
        navController: NavController
    ) {
        lifecycleScope.launch {
            repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                navController.currentBackStackEntryFlow.collect { backStackEntry ->
                    syncBottomBarState(
                        backStackEntry.destination
                    )
                }
            }
        }
    }

    private fun syncBottomBarState(
        destination: NavDestination?
    ) {
        val shouldShowBottomBar =
            isAuthenticated &&
                AppDestinationContract.shouldShowBottomBar(destination)

        binding.bottomNav.isVisible =
            shouldShowBottomBar

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
}
