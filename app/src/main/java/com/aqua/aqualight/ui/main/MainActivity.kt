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
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.databinding.ActivityMainBinding
import com.aqua.aqualight.ui.navigation.AppDestinationContract
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_START_IN_APP = "EXTRA_START_IN_APP"
        const val EXTRA_OPEN_CARE_TASK_ID = "EXTRA_OPEN_CARE_TASK_ID"
        const val EXTRA_OWNER_UID = "EXTRA_OWNER_UID"
        const val EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE =
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

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                isAuthenticated = isUserAuthenticated()

                installRootGraph(
                    startInApp = isAuthenticated
                )

                setupBottomBarIfNeeded(navController)

                binding.navHost.isVisible = true

                restoreSettingsRootAfterThemeChangeIfNeeded()
                startSessionBoundServicesIfNeeded()
                consumePendingCareTaskIfPossible()
            }
        } else {
            setupBottomBarIfNeeded(navController)
            binding.navHost.isVisible = true

            lifecycleScope.launch {
                isAuthenticated = isUserAuthenticated()
                restoreSettingsRootAfterThemeChangeIfNeeded()
                startSessionBoundServicesIfNeeded()
                consumePendingCareTaskIfPossible()
            }
        }
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(intent)

        setIntent(intent)
        captureCareTaskIntent(intent)

        lifecycleScope.launch {
            isAuthenticated = isUserAuthenticated()
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

    private suspend fun isUserAuthenticated(): Boolean {
        return authSessionManager.currentSessionState() is
            AuthSessionManager.SessionState.Authenticated
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
            val restored = runCatching {
                navController.popBackStack(
                    R.id.settingsFragment,
                    false
                )
            }.getOrDefault(false)

            if (!restored) {
                binding.bottomNav.selectedItemId = R.id.nav_settings
            }

            binding.bottomNav.isVisible = true
            binding.bottomNav.alpha = 1f
            binding.bottomNav.bringToFront()

            syncBottomBarState(
                navController.currentDestination
            )
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
        if (bottomBarSetup) {
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
