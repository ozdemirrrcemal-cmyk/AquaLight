package com.aqua.aqualight.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.ActivityMainBinding
import com.aqua.aqualight.lan.LanMonitor

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_START_IN_APP = "EXTRA_START_IN_APP"
        const val EXTRA_OPEN_CARE_TASK_ID = "EXTRA_OPEN_CARE_TASK_ID"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

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

        val taskIdFromNotification = intent.getLongExtra(
            EXTRA_OPEN_CARE_TASK_ID,
            -1L
        )

        val startInApp = intent.getBooleanExtra(
            EXTRA_START_IN_APP,
            false
        ) || taskIdFromNotification > 0L

        if (startInApp) {
            LanMonitor.start(
                context = applicationContext
            )
        }

        if (savedInstanceState == null) {
            binding.navHost.isVisible = false
            binding.bottomNav.isVisible = false

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

        setupBottomBar(navController)

        navController.currentDestination?.let {
            destination ->
            binding.bottomNav.isVisible = isInAppDest(destination.id)
        }

        binding.navHost.isVisible = true

        handleCareTaskNotificationIntent(intent)
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(intent)

        setIntent(intent)
        handleCareTaskNotificationIntent(intent)
    }

    private fun handleCareTaskNotificationIntent(
        intent: Intent?
    ) {
        val taskId = intent?.getLongExtra(
            EXTRA_OPEN_CARE_TASK_ID,
            -1L
        ) ?: -1L

        if (taskId <= 0L) {
            return
        }

        intent?.removeExtra(EXTRA_OPEN_CARE_TASK_ID)

        binding.navHost.post {
            runCatching {
                navController.navigate(
                    R.id.taskDetailFragment,
                    bundleOf(
                        "taskId" to taskId
                    )
                )
            }
        }
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

    private fun setupBottomBar(
        navController: NavController
    ) {
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener {
            _, destination, _ ->
            val inAppDestination = isInAppDest(destination.id)

            binding.bottomNav.isVisible = inAppDestination

            if (inAppDestination) {
                LanMonitor.start(
                    context = applicationContext
                )
            }
        }
    }
}