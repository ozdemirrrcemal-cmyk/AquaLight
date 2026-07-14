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
import com.aqua.aqualight.data.auth.AuthSessionManager
import com.aqua.aqualight.data.auth.SessionBoundServiceManager
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.databinding.ActivityMainBinding
import com.aqua.aqualight.ui.navigation.AppDestinationContract
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_START_IN_APP = "EXTRA_START_IN_APP"
        const val EXTRA_OPEN_CARE_TASK_ID = "EXTRA_OPEN_CARE_TASK_ID"
        const val EXTRA_OWNER_UID = "EXTRA_OWNER_UID"
        private const val EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE =
            "EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val authSessionManager by lazy {
        AuthSessionManager.create(this)
    }

    private var isAuthenticated: Boolean = false
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

        lifecycleScope.launch {
            val sessionError = refreshAuthenticationState()

            if (sessionError == null) {
                ensureRootGraphForCurrentSession(
                    startInApp = isAuthenticated
                )
            } else {
                installRootGraph(startInApp = false)
            }

            setupBottomBarIfNeeded(navController)

            binding.navHost.isVisible = true

            showSessionStartupFailureIfNeeded(sessionError)
            showLocalDataRecoveryIfNeeded()
            restoreSettingsRootAfterThemeChangeIfNeeded()
            startSessionBoundServicesIfNeeded()
            consumePendingCareTaskIfPossible()
        }
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(intent)

        setIntent(intent)
        captureCareTaskIntent(intent)

        lifecycleScope.launch {
            val sessionError = refreshAuthenticationState()

            if (sessionError == null) {
                ensureRootGraphForCurrentSession(
                    startInApp = isAuthenticated
                )
            } else {
                installRootGraph(startInApp = false)
            }

            showSessionStartupFailureIfNeeded(sessionError)
            showLocalDataRecoveryIfNeeded()
            restoreSettingsRootAfterThemeChangeIfNeeded()
            startSessionBoundServicesIfNeeded()
            consumePendingCareTaskIfPossible()
        }
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

    private suspend fun isUserAuthenticated(): Boolean {
        return authSessionManager.currentSessionState() is
            AuthSessionManager.SessionState.Authenticated
    }

    private suspend fun refreshAuthenticationState(): Throwable? {
        return try {
            isAuthenticated = isUserAuthenticated()
            null
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }

            error.printStackTrace()
            isAuthenticated = false
            error
        }
    }

    private fun showSessionStartupFailureIfNeeded(
        error: Throwable?
    ) {
        if (error == null) return

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

    private fun ensureRootGraphForCurrentSession(
        startInApp: Boolean
    ) {
        if (navController.currentDestination != null) {
            return
        }

        installRootGraph(
            startInApp = startInApp
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
            ensureRootGraphForCurrentSession(
                startInApp = true
            )

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

        val activeUid = authSessionManager.currentUser()?.uid.orEmpty()

        if (
            ownerUid.isNotBlank() &&
            !UserDataScope.belongsToOwner(
                recordOwnerUid = ownerUid,
                ownerUid = activeUid,
                includeLegacy = false
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

    private fun startSessionBoundServicesIfNeeded() {
        if (!isAuthenticated) {
            return
        }

        SessionBoundServiceManager.start(
            context = applicationContext
        )
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
            isAuthenticated = authSessionManager.isAuthenticated()
            startSessionBoundServicesIfNeeded()
            consumePendingCareTaskIfPossible()
        }
    }

}
