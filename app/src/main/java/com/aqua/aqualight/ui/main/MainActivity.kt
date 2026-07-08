package com.aqua.aqualight.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.auth.AuthSessionManager
import com.aqua.aqualight.data.auth.SessionBoundServiceManager
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.databinding.ActivityMainBinding
import com.aqua.aqualight.ui.navigation.AppDestinationContract
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_START_IN_APP = "EXTRA_START_IN_APP"
        const val EXTRA_OPEN_CARE_TASK_ID = "EXTRA_OPEN_CARE_TASK_ID"
        const val EXTRA_OWNER_UID = "EXTRA_OWNER_UID"
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

                startSessionBoundServicesIfNeeded()
                consumePendingCareTaskIfPossible()
            }
        } else {
            setupBottomBarIfNeeded(navController)
            binding.navHost.isVisible = true

            lifecycleScope.launch {
                isAuthenticated = isUserAuthenticated()
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
            startSessionBoundServicesIfNeeded()
            consumePendingCareTaskIfPossible()
        }
    }

    fun clearSessionNavigationState() {
        isAuthenticated = false
        pendingCareTaskId = -1L
        pendingCareTaskOwnerUid = ""

        intent?.removeExtra(EXTRA_OPEN_CARE_TASK_ID)
        intent?.removeExtra(EXTRA_START_IN_APP)
        intent?.removeExtra(EXTRA_OWNER_UID)
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

        binding.bottomNav.setOnItemSelectedListener { item ->
            navigateTopLevelDestination(
                destinationId = item.itemId
            )
        }

        binding.bottomNav.setOnItemReselectedListener {
            // Keep the current top-level back stack untouched on reselection.
        }

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

        syncBottomBarState(
            navController.currentDestination
        )

        binding.root.post {
            syncBottomBarState(
                navController.currentDestination
            )
        }
    }

    private fun navigateTopLevelDestination(
        destinationId: Int
    ): Boolean {
        if (destinationId !in AppDestinationContract.topLevelGraphIds) {
            return false
        }

        if (currentTopLevelGraphId(navController.currentDestination) == destinationId) {
            return true
        }

        val options = navOptions {
            anim {
                enter = R.anim.aqua_nav_enter
                exit = R.anim.aqua_nav_exit
                popEnter = R.anim.aqua_nav_enter
                popExit = R.anim.aqua_nav_exit
            }

            launchSingleTop = true
            restoreState = true

            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        }

        return runCatching {
            navController.navigate(
                destinationId,
                null,
                options
            )
            true
        }.getOrDefault(false)
    }

    private fun syncBottomBarState(
        destination: NavDestination?
    ) {
        val destinationId =
            destination?.id

        val isInsideAppGraph =
            destinationId == R.id.nav_app ||
                AppDestinationContract.isInsideAppGraph(destination)

        val isTopLevelDestination =
            destinationId == R.id.nav_app ||
                destinationId?.let(
                    AppDestinationContract::isTopLevelDestination
                ) == true

        val shouldShowBottomBar =
            isInsideAppGraph && isTopLevelDestination

        binding.bottomNav.isVisible =
            shouldShowBottomBar

        if (shouldShowBottomBar) {
            syncBottomNavSelectedItem(destination)
        }

        exitFromTopLevelBackCallback?.isEnabled =
            shouldShowBottomBar

        if (isInsideAppGraph) {
            isAuthenticated = authSessionManager.isAuthenticated()
            startSessionBoundServicesIfNeeded()
            consumePendingCareTaskIfPossible()
        }
    }

    private fun syncBottomNavSelectedItem(
        destination: NavDestination?
    ) {
        val graphId = currentTopLevelGraphId(destination) ?: return

        if (binding.bottomNav.selectedItemId == graphId) {
            return
        }

        binding.bottomNav.menu.findItem(graphId)?.isChecked = true
    }

    private fun currentTopLevelGraphId(
        destination: NavDestination?
    ): Int? {
        return destination
            ?.hierarchy
            ?.firstOrNull { node ->
                node.id in AppDestinationContract.topLevelGraphIds
            }
            ?.id
    }

}
