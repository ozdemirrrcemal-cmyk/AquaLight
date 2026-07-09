package com.aqua.aqualight.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
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
        const val EXTRA_THEME_DIAGNOSTIC_TRACE =
            "EXTRA_THEME_DIAGNOSTIC_TRACE"
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
    private var themeDiagnosticOverlay: TextView? = null
    private val themeDiagnosticTrace = StringBuilder()
    private var syncTraceCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(
            R.id.nav_host
        ) as NavHostFragment

        navController = navHost.navController
        setupThemeDiagnosticOverlay()
        restorePersistedThemeDiagnosticTrace()
        appendThemeDiagnostic(
            "onCreate saved=${savedInstanceState != null} restoreFlag=${isThemeRestoreFlagSet()} start destination=${destinationLabel(navController.currentDestination)}"
        )

        binding.navHost.isVisible = false
        binding.bottomNav.isVisible = false
        appendThemeDiagnostic(
            "initialHide navHostVisible=${binding.navHost.isVisible} bottomVisible=${binding.bottomNav.isVisible} menu=${bottomMenuLabel()}"
        )

        captureCareTaskIntent(intent)

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                isAuthenticated = isUserAuthenticated()
                appendThemeDiagnostic(
                    "freshCreate auth=$isAuthenticated beforeGraph destination=${destinationLabel(navController.currentDestination)}"
                )

                installRootGraph(startInApp = isAuthenticated)
                appendThemeDiagnostic(
                    "graphInstalled startInApp=$isAuthenticated destination=${destinationLabel(navController.currentDestination)} hierarchy=${hierarchyLabel(navController.currentDestination)}"
                )

                setupBottomBarIfNeeded(navController)

                binding.navHost.isVisible = true
                appendThemeDiagnostic(
                    "navHostShown destination=${destinationLabel(navController.currentDestination)} bottomVisible=${binding.bottomNav.isVisible}"
                )

                restoreSettingsRootAfterThemeChangeIfNeeded()
                startSessionBoundServicesIfNeeded()
                consumePendingCareTaskIfPossible()
            }
        } else {
            setupBottomBarIfNeeded(navController)
            binding.navHost.isVisible = true
            appendThemeDiagnostic(
                "restoredCreate destination=${destinationLabel(navController.currentDestination)} hierarchy=${hierarchyLabel(navController.currentDestination)} bottomVisible=${binding.bottomNav.isVisible}"
            )

            lifecycleScope.launch {
                isAuthenticated = isUserAuthenticated()
                appendThemeDiagnostic(
                    "restoredCreate auth=$isAuthenticated destination=${destinationLabel(navController.currentDestination)}"
                )
                restoreSettingsRootAfterThemeChangeIfNeeded()
                startSessionBoundServicesIfNeeded()
                consumePendingCareTaskIfPossible()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        appendThemeDiagnostic(
            "onNewIntent restoreFlag=${isThemeRestoreFlagSet()} current=${destinationLabel(navController.currentDestination)}"
        )
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

        if (!::binding.isInitialized || !::navController.isInitialized) {
            return
        }

        appendThemeDiagnostic(
            "onPostResume current=${destinationLabel(navController.currentDestination)} bottomVisible=${binding.bottomNav.isVisible}"
        )

        binding.root.post {
            appendThemeDiagnostic(
                "onPostResume.post beforeRestore current=${destinationLabel(navController.currentDestination)} bottomVisible=${binding.bottomNav.isVisible}"
            )
            restoreSettingsRootAfterThemeChangeIfNeeded()
            syncBottomBarState(navController.currentDestination)
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
        intent?.removeExtra(EXTRA_THEME_DIAGNOSTIC_TRACE)
        appendThemeDiagnostic("clearSessionNavigationState called")
    }

    fun appendThemeDiagnostic(message: String) {
        appendThemeDiagnosticInternal(message)
    }

    fun navigationDiagnosticSnapshot(): String {
        return "dest=${destinationLabel(navController.currentDestination)} hierarchy=${hierarchyLabel(navController.currentDestination)} shouldShow=${AppDestinationContract.shouldShowBottomBar(navController.currentDestination)} inside=${AppDestinationContract.isInsideAppGraph(navController.currentDestination)} bottomVisible=${binding.bottomNav.isVisible} bottomHeight=${binding.bottomNav.height} bottomAlpha=${binding.bottomNav.alpha} bottomTranslationY=${binding.bottomNav.translationY} navHostVisible=${binding.navHost.isVisible} navHostHeight=${binding.navHost.height} selected=${resourceName(binding.bottomNav.selectedItemId)} menu=${bottomMenuLabel()}"
    }

    private suspend fun isUserAuthenticated(): Boolean {
        return authSessionManager.currentSessionState() is
            AuthSessionManager.SessionState.Authenticated
    }

    private fun installRootGraph(startInApp: Boolean) {
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
        val shouldRestore = isThemeRestoreFlagSet()
        appendThemeDiagnostic(
            "restoreCheck should=$shouldRestore auth=$isAuthenticated current=${destinationLabel(navController.currentDestination)} bottomVisible=${binding.bottomNav.isVisible} menu=${bottomMenuLabel()}"
        )

        if (!shouldRestore || !isAuthenticated) {
            return
        }

        intent?.removeExtra(EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE)

        binding.root.post {
            val beforeDestination = destinationLabel(navController.currentDestination)
            val restored = runCatching {
                navController.popBackStack(
                    R.id.settingsFragment,
                    false
                )
            }.onFailure {
                appendThemeDiagnostic(
                    "restorePost popToSettings threw ${it.javaClass.simpleName}: ${it.message}"
                )
            }.getOrDefault(false)
            val afterPopDestination = destinationLabel(navController.currentDestination)

            appendThemeDiagnostic(
                "restorePost popToSettings=$restored before=$beforeDestination afterPop=$afterPopDestination selectedBefore=${resourceName(binding.bottomNav.selectedItemId)} menu=${bottomMenuLabel()}"
            )

            if (!restored) {
                selectBottomNavItemSafely(R.id.nav_settings)
            }

            binding.bottomNav.isVisible = true
            binding.bottomNav.alpha = 1f
            binding.bottomNav.bringToFront()
            appendThemeDiagnostic(
                "restorePost forcedBottom visible=${binding.bottomNav.isVisible} height=${binding.bottomNav.height} alpha=${binding.bottomNav.alpha} y=${binding.bottomNav.y} translationY=${binding.bottomNav.translationY}"
            )

            syncBottomBarState(navController.currentDestination)
        }
    }

    private fun selectBottomNavItemSafely(itemId: Int) {
        val item = binding.bottomNav.menu.findItem(itemId)
        appendThemeDiagnostic(
            "selectBottomNavSafely target=${resourceName(itemId)} itemExists=${item != null} currentSelected=${resourceName(binding.bottomNav.selectedItemId)} menu=${bottomMenuLabel()}"
        )

        if (item == null) {
            return
        }

        binding.bottomNav.post {
            runCatching {
                binding.bottomNav.selectedItemId = itemId
            }.onSuccess {
                appendThemeDiagnostic(
                    "selectBottomNavSafely success target=${resourceName(itemId)} selected=${resourceName(binding.bottomNav.selectedItemId)} current=${destinationLabel(navController.currentDestination)}"
                )
            }.onFailure {
                appendThemeDiagnostic(
                    "selectBottomNavSafely failed ${it.javaClass.simpleName}: ${it.message} target=${resourceName(itemId)} selected=${resourceName(binding.bottomNav.selectedItemId)} menu=${bottomMenuLabel()}"
                )
            }
        }
    }

    private fun captureCareTaskIntent(intent: Intent?) {
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
        appendThemeDiagnostic(
            "captureCareTask taskId=$taskId ownerUidBlank=${ownerUid.isBlank()}"
        )
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
            appendThemeDiagnostic("consumeCareTask rejected owner mismatch")
            return
        }

        if (!AppDestinationContract.isInsideAppGraph(navController.currentDestination)) {
            appendThemeDiagnostic(
                "consumeCareTask wait notInsideApp current=${destinationLabel(navController.currentDestination)}"
            )
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
                appendThemeDiagnostic(
                    "consumeCareTask openFailed ${it.javaClass.simpleName}"
                )
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

    private fun setupBottomBarIfNeeded(navController: NavController) {
        if (bottomBarSetup) {
            appendThemeDiagnostic("setupBottomBar skipped alreadySetup")
            return
        }

        bottomBarSetup = true
        appendThemeDiagnostic(
            "setupBottomBar start current=${destinationLabel(navController.currentDestination)} selected=${resourceName(binding.bottomNav.selectedItemId)} menu=${bottomMenuLabel()}"
        )

        binding.bottomNav.setupWithNavController(navController)
        appendThemeDiagnostic(
            "setupWithNavController done selected=${resourceName(binding.bottomNav.selectedItemId)} menu=${bottomMenuLabel()}"
        )

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
            appendThemeDiagnostic(
                "destinationChanged ${destinationLabel(destination)} hierarchy=${hierarchyLabel(destination)}"
            )
            syncBottomBarState(destination)
        }

        observeBottomBarBackStack(navController)

        syncBottomBarState(navController.currentDestination)

        binding.root.post {
            appendThemeDiagnostic(
                "setupBottomBar root.post current=${destinationLabel(navController.currentDestination)} bottomVisible=${binding.bottomNav.isVisible}"
            )
            syncBottomBarState(navController.currentDestination)
        }
    }

    private fun observeBottomBarBackStack(navController: NavController) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navController.currentBackStackEntryFlow.collect { backStackEntry ->
                    appendThemeDiagnostic(
                        "backStackFlow ${destinationLabel(backStackEntry.destination)}"
                    )
                    syncBottomBarState(backStackEntry.destination)
                }
            }
        }
    }

    private fun syncBottomBarState(destination: NavDestination?) {
        val shouldShowBottomBar =
            AppDestinationContract.shouldShowBottomBar(destination)
        val insideApp =
            AppDestinationContract.isInsideAppGraph(destination)
        val isTopLevel =
            destination?.id?.let(
                AppDestinationContract::isTopLevelDestination
            ) == true

        binding.bottomNav.isVisible = shouldShowBottomBar

        if (shouldShowBottomBar) {
            binding.bottomNav.alpha = 1f
            binding.bottomNav.bringToFront()
        }

        if (syncTraceCount < 120) {
            syncTraceCount++
            appendThemeDiagnostic(
                "sync#$syncTraceCount dest=${destinationLabel(destination)} should=$shouldShowBottomBar inside=$insideApp top=$isTopLevel bottomVisible=${binding.bottomNav.isVisible} height=${binding.bottomNav.height} alpha=${binding.bottomNav.alpha} y=${binding.bottomNav.y} translationY=${binding.bottomNav.translationY} navHostVisible=${binding.navHost.isVisible} navHostHeight=${binding.navHost.height} hierarchy=${hierarchyLabel(destination)}"
            )
        }

        exitFromTopLevelBackCallback?.isEnabled =
            shouldShowBottomBar && isTopLevel

        if (insideApp) {
            isAuthenticated = authSessionManager.isAuthenticated()
            startSessionBoundServicesIfNeeded()
            consumePendingCareTaskIfPossible()
        }
    }

    private fun setupThemeDiagnosticOverlay() {
        val padding = (8f * resources.displayMetrics.density).toInt()

        themeDiagnosticOverlay = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(230, 0, 0, 0))
            setPadding(padding, padding, padding, padding)
            gravity = Gravity.START
            maxLines = 18
            setTextIsSelectable(true)
            isFocusable = true
            isFocusableInTouchMode = true
            bringToFront()
        }

        val params = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
        }

        binding.root.addView(
            requireNotNull(themeDiagnosticOverlay),
            params
        )

        updateThemeDiagnosticOverlay()
    }

    private fun restorePersistedThemeDiagnosticTrace() {
        val existingTrace = intent?.getStringExtra(
            EXTRA_THEME_DIAGNOSTIC_TRACE
        ).orEmpty()

        if (existingTrace.isNotBlank()) {
            themeDiagnosticTrace.append(existingTrace)
            updateThemeDiagnosticOverlay()
        }
    }

    private fun appendThemeDiagnosticInternal(message: String) {
        val line = "${System.currentTimeMillis() % 100000}: $message\n"
        themeDiagnosticTrace.append(line)

        if (themeDiagnosticTrace.length > 12000) {
            themeDiagnosticTrace.delete(
                0,
                themeDiagnosticTrace.length - 12000
            )
        }

        intent?.putExtra(
            EXTRA_THEME_DIAGNOSTIC_TRACE,
            themeDiagnosticTrace.toString()
        )

        updateThemeDiagnosticOverlay()
    }

    private fun updateThemeDiagnosticOverlay() {
        themeDiagnosticOverlay?.text = buildString {
            appendLine("AquaLight THEME DIAGNOSTIC")
            appendLine("Uzun bas → tümünü seç/kopyala")
            appendLine("-----")
            append(themeDiagnosticTrace.toString())
        }
        themeDiagnosticOverlay?.bringToFront()
    }

    private fun isThemeRestoreFlagSet(): Boolean {
        return intent?.getBooleanExtra(
            EXTRA_RESTORE_SETTINGS_ROOT_AFTER_THEME_CHANGE,
            false
        ) == true
    }

    private fun destinationLabel(destination: NavDestination?): String {
        return destination?.let { navDestination ->
            "${resourceName(navDestination.id)}(${navDestination.id})"
        } ?: "null"
    }

    private fun hierarchyLabel(destination: NavDestination?): String {
        return destination?.hierarchy?.joinToString(
            separator = " > "
        ) { node ->
            resourceName(node.id)
        } ?: "null"
    }

    private fun bottomMenuLabel(): String {
        return (0 until binding.bottomNav.menu.size()).joinToString(
            separator = ","
        ) { index ->
            resourceName(binding.bottomNav.menu.getItem(index).itemId)
        }
    }

    private fun resourceName(id: Int): String {
        return runCatching {
            resources.getResourceEntryName(id)
        }.getOrElse {
            id.toString()
        }
    }

}
