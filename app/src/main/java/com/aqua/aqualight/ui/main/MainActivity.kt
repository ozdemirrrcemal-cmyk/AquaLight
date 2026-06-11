package com.aqua.aqualight.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.aqua.aqualight.NavAppDirections
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.auth.AuthSessionManager
import com.aqua.aqualight.data.auth.SessionBoundServiceManager
import com.aqua.aqualight.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_START_IN_APP = "EXTRA_START_IN_APP"
        const val EXTRA_OPEN_CARE_TASK_ID = "EXTRA_OPEN_CARE_TASK_ID"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val authSessionManager by lazy {
        AuthSessionManager.create(this)
    }

    private var isAuthenticated: Boolean = false
    private var pendingCareTaskId: Long = -1L
    private var bottomBarSetup: Boolean = false

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

        intent?.removeExtra(EXTRA_OPEN_CARE_TASK_ID)
        intent?.removeExtra(EXTRA_START_IN_APP)
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

        pendingCareTaskId = taskId
        intent?.removeExtra(EXTRA_OPEN_CARE_TASK_ID)
        intent?.removeExtra(EXTRA_START_IN_APP)
    }

    private fun consumePendingCareTaskIfPossible() {
        val taskId = pendingCareTaskId

        if (taskId <= 0L || !isAuthenticated) {
            return
        }

        val currentDestinationId = navController.currentDestination?.id
            ?: return

        if (!isInAppDest(currentDestinationId)) {
            return
        }

        pendingCareTaskId = -1L

        binding.navHost.post {
            runCatching {
                navController.navigate(
                    NavAppDirections.actionGlobalTaskDetailFragment(
                        taskId = taskId
                    )
                )
            }.onFailure {
                pendingCareTaskId = taskId
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

    private fun isInAppDest(
        destinationId: Int
    ): Boolean {
        return when (destinationId) {
            R.id.aquariumFragment,
            R.id.aquariumMaintenanceFragment,
            R.id.devicesFragment,
            R.id.settingsFragment -> true
            else -> false
        }
    }

    private fun setupBottomBarIfNeeded(
        navController: NavController
    ) {
        if (bottomBarSetup) {
            return
        }

        bottomBarSetup = true

        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val inAppDestination = isInAppDest(destination.id)

            binding.bottomNav.isVisible = inAppDestination

            if (inAppDestination) {
                isAuthenticated = authSessionManager.isAuthenticated()
                startSessionBoundServicesIfNeeded()
                consumePendingCareTaskIfPossible()
            }
        }
    }
}
