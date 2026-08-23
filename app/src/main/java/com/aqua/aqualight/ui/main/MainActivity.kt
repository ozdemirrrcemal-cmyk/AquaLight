package com.aqua.aqualight.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.data.auth.AppSessionCoordinator
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.databinding.ActivityMainBinding
import com.aqua.aqualight.ui.navigation.AppDestinationContract
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    companion object {
        @Deprecated("Startup graph selection is owned by AppSessionCoordinator.")
        const val EXTRA_START_IN_APP = "EXTRA_START_IN_APP"
        const val EXTRA_OPEN_CARE_TASK_ID = "EXTRA_OPEN_CARE_TASK_ID"
        const val EXTRA_OPEN_DEVICE_FIRMWARE_UID = "EXTRA_OPEN_DEVICE_FIRMWARE_UID"
        const val EXTRA_OWNER_UID = "EXTRA_OWNER_UID"
        private const val EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE =
            "EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var navigationCoordinator: MainNavigationCoordinator

    private val appSessionCoordinator by lazy {
        AppSessionCoordinator.create(applicationContext)
    }

    private var isAuthenticated: Boolean = false
    private var activeOwnerUid: String = ""
    private var renderedSessionKey: String? = null
    private var lastShownStartupFailure: Throwable? = null

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
        MainActivityDiagnosticNavigation.observe(this, navController)

        binding.navHost.isVisible = false
        binding.bottomNav.isVisible = false

        navigationCoordinator = MainNavigationCoordinator(
            host = this,
            binding = binding,
            navController = navController,
            restoreSettingsExtra = EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE,
            sessionSnapshot = {
                MainNavigationSessionSnapshot(
                    isAuthenticated = isAuthenticated,
                    activeOwnerUid = activeOwnerUid
                )
            },
            deviceFirmwareRouteOperations =
                requireAppContainer().deviceFirmwareNotificationRouteOperations
        )
        navigationCoordinator.captureNotificationIntent(intent)
        observeSessionState()
        appSessionCoordinator.start()
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(intent)

        setIntent(intent)
        navigationCoordinator.captureNotificationIntent(intent)
        appSessionCoordinator.requestReconcile()
        navigationCoordinator.consumePendingNotificationIfPossible()
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
            navigationCoordinator.restoreSettingsRootAfterThemeChangeIfNeeded()
            navigationCoordinator.syncBottomBarState(
                navController.currentDestination
            )
        }
    }

    fun clearSessionNavigationState() {
        isAuthenticated = false
        activeOwnerUid = ""
        renderedSessionKey = null
        navigationCoordinator.clearPendingNotifications()

        intent?.removeExtra(EXTRA_OPEN_CARE_TASK_ID)
        intent?.removeExtra(EXTRA_OPEN_DEVICE_FIRMWARE_UID)
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

                navigationCoordinator.setupBottomBarIfNeeded()
                binding.navHost.isVisible = true
                showLocalDataRecoveryIfNeeded()
                navigationCoordinator.restoreSettingsRootAfterThemeChangeIfNeeded()
                navigationCoordinator.consumePendingNotificationIfPossible()
                navigationCoordinator.syncBottomBarState(navController.currentDestination)
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

                navigationCoordinator.setupBottomBarIfNeeded()
                binding.navHost.isVisible = true
                binding.bottomNav.isVisible = false
                navigationCoordinator.syncBottomBarState(navController.currentDestination)
            }

            is AppSessionCoordinator.State.Failure -> {
                isAuthenticated = false
                activeOwnerUid = ""

                if (renderedSessionKey != "startup-failure") {
                    installRootGraph(startInApp = false)
                    renderedSessionKey = "startup-failure"
                }

                navigationCoordinator.setupBottomBarIfNeeded()
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
}

private object MainActivityDiagnosticNavigation {

    fun observe(activity: MainActivity, navController: NavController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            AppDiagnosticTrace.event(
                category = "navigation",
                name = "destination.changed",
                "destination" to destinationName(activity, destination.id),
                "destinationId" to destination.id
            )
        }
    }

    private fun destinationName(activity: MainActivity, destinationId: Int): String =
        runCatching {
            activity.resources.getResourceEntryName(destinationId)
        }.getOrElse {
            "unresolved"
        }
}
