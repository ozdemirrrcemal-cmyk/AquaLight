package com.aqua.aqualight.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
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

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        configureSystemBars()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

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

        binding.bottomNav.setupWithNavController(navController)

        val exitFromTopLevelBackCallback =
            object : OnBackPressedCallback(false) {

                override fun handleOnBackPressed() {
                    finish()
                }
            }

        onBackPressedDispatcher.addCallback(
            this,
            exitFromTopLevelBackCallback
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            syncBottomBarState(
                destination = destination,
                exitFromTopLevelBackCallback = exitFromTopLevelBackCallback
            )
        }

        binding.root.post {
            syncBottomBarState(
                destination = navController.currentDestination,
                exitFromTopLevelBackCallback = exitFromTopLevelBackCallback
            )
        }
    }

    private fun syncBottomBarState(
        destination: NavDestination?,
        exitFromTopLevelBackCallback: OnBackPressedCallback
    ) {
        val isInsideAppGraph =
            AppDestinationContract.isInsideAppGraph(destination)

        val isTopLevelDestination =
            AppDestinationContract.isTopLevelDestination(destination)

        binding.bottomNav.isVisible =
            isInsideAppGraph && isTopLevelDestination

        exitFromTopLevelBackCallback.isEnabled =
            isInsideAppGraph && isTopLevelDestination

        if (isInsideAppGraph) {
            isAuthenticated = authSessionManager.isAuthenticated()
            startSessionBoundServicesIfNeeded()
            consumePendingCareTaskIfPossible()
        }
    }

    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        window.statusBarColor =
            Color.TRANSPARENT

        window.navigationBarColor =
            Color.BLACK

        val controller = WindowInsetsControllerCompat(
            window,
            window.decorView
        )

        controller.isAppearanceLightStatusBars =
            false

        controller.isAppearanceLightNavigationBars =
            false
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars()
            )

            val navigationBars = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            binding.navHost.updatePadding(
                top = statusBars.top
            )

            binding.bottomNav.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                bottomMargin = navigationBars.bottom
            }

            insets
        }

        binding.root.systemUiVisibility =
            binding.root.systemUiVisibility and
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()

        ViewCompat.requestApplyInsets(binding.root)
    }

}
