package com.aqua.aqualight.smoke

import android.os.Bundle
import android.text.Layout
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.NavHostFragment
import com.aqua.aqualight.R
import com.aqua.aqualight.app.AquaApp
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.ui.tabs.aquarium.AquariumFragment
import com.aqua.aqualight.ui.tabs.devices.DevicesFragment
import com.aqua.aqualight.ui.tabs.maintenance.AquariumMaintenanceFragment
import com.aqua.aqualight.ui.tabs.settings.SettingsFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CI-only Activity packaged exclusively in the minified releaseSmoke variant.
 * It exercises real Fragment lifecycle, accessibility labels and visual profiles without
 * opening Firebase, BLE or WebSocket infrastructure.
 */
class ReleaseSmokeActivity : BaseActivity() {

    private lateinit var navHostFragment: NavHostFragment
    private lateinit var navController: NavController
    private lateinit var smokeContainer: ReleaseSmokeAppContainer
    private var smokeStarted = false
    private val smokeTheme: String by lazy {
        intent.getStringExtra(EXTRA_SMOKE_THEME).orEmpty().lowercase().ifBlank { THEME_LIGHT }
    }
    private val smokeProfile: String by lazy {
        intent.getStringExtra(EXTRA_SMOKE_PROFILE).orEmpty().lowercase().ifBlank { smokeTheme }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(
            if (smokeTheme == THEME_DARK) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
        smokeContainer = ReleaseSmokeAppContainer(applicationContext)
        (application as AquaApp).replaceAppContainerForProcess(smokeContainer)
        super.onCreate(savedInstanceState)

        val requestedDirection = requestedLayoutDirection()
        window.decorView.layoutDirection = requestedDirection
        setContentView(
            FrameLayout(this).apply {
                id = SMOKE_CONTAINER_ID
                layoutDirection = requestedDirection
            }
        )

        navHostFragment = NavHostFragment()
        supportFragmentManager.commitNow {
            replace(SMOKE_CONTAINER_ID, navHostFragment, SMOKE_NAV_HOST_TAG)
            setPrimaryNavigationFragment(navHostFragment)
        }

        navController = navHostFragment.navController
        navController.graph = createSmokeGraph()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (smokeStarted) return
        smokeStarted = true

        lifecycleScope.launch {
            runCatching {
                val healthData = smokeContainer.prepareHealthSmoke()
                smokeScreens.forEach { screen ->
                    if (navController.currentDestination?.id != screen.destinationId) {
                        navController.navigate(screen.destinationId)
                    }

                    navHostFragment.childFragmentManager.executePendingTransactions()
                    delay(SCREEN_SETTLE_MILLIS)

                    val fragment =
                        navHostFragment.childFragmentManager.primaryNavigationFragment
                            ?: error("${screen.name} did not become the primary navigation Fragment")

                    check(screen.fragmentClass.isInstance(fragment)) {
                        "Expected ${screen.name}, found ${fragment::class.java.name}"
                    }
                    check(fragment.isAdded) {
                        "${screen.name} was not added"
                    }
                    check(fragment.view != null) {
                        "${screen.name} did not create a view"
                    }
                    check(
                        fragment.lifecycle.currentState.isAtLeast(
                            Lifecycle.State.STARTED
                        )
                    ) {
                        "${screen.name} did not reach STARTED"
                    }

                    val fragmentRoot = fragment.requireView()
                    applyRequestedLayoutDirection(fragmentRoot)
                    delay(LAYOUT_DIRECTION_SETTLE_MILLIS)
                    verifyRequestedLayoutDirection(fragmentRoot)
                    verifyIconAccessibility(fragmentRoot)
                    verifyLargeFontText(fragmentRoot)
                    captureSmokeScreen(screen.name, smokeProfile)
                }
                WaterAnalysisNavigationSmoke(navHostFragment).verify()
                HealthObservationNavigationSmoke(navHostFragment, healthData, ::captureHealthScreen).verify()
            }.onSuccess {
                renderResult("$PASS_MARKER:$smokeProfile")
            }.onFailure { error ->
                renderResult(
                    "$FAIL_MARKER\n${error::class.java.name}\n${error.message.orEmpty()}"
                )
            }
        }
    }

    private fun captureHealthScreen(name: String) {
        val fragment = navHostFragment.childFragmentManager.primaryNavigationFragment
            ?: error("Health observation route did not create a primary navigation fragment")
        val root = fragment.requireView()
        applyRequestedLayoutDirection(root)
        HealthObservationContrastSmoke.verify(root)
        verifyIconAccessibility(root)
        verifyLargeFontText(root)
        captureSmokeScreen(name, smokeProfile)
    }

    private fun createSmokeGraph(): NavGraph {
        val navigatorProvider = navController.navigatorProvider
        val graphNavigator = navigatorProvider.getNavigator(
            NavGraphNavigator::class.java
        )
        val fragmentNavigator = navigatorProvider.getNavigator(
            FragmentNavigator::class.java
        )
        val screens = smokeScreens

        return NavGraph(graphNavigator).apply {
            id = SMOKE_GRAPH_ID
            screens.forEach { screen ->
                addDestination(
                    fragmentNavigator.createDestination().apply {
                        id = screen.destinationId
                        setClassName(screen.fragmentClass.name)
                    }
                )
            }
            setStartDestination(screens.first().destinationId)
        }
    }

    private val smokeScreens: List<SmokeScreen> = listOf(
        SmokeScreen(
            name = "AquariumFragment",
            destinationId = DESTINATION_AQUARIUM,
            fragmentClass = AquariumFragment::class.java
        ),
        SmokeScreen(
            name = "AquariumMaintenanceFragment",
            destinationId = DESTINATION_MAINTENANCE,
            fragmentClass = AquariumMaintenanceFragment::class.java
        ),
        SmokeScreen(
            name = "DevicesFragment",
            destinationId = DESTINATION_DEVICES,
            fragmentClass = DevicesFragment::class.java
        ),
        SmokeScreen(
            name = "SettingsFragment",
            destinationId = DESTINATION_SETTINGS,
            fragmentClass = SettingsFragment::class.java
        )
    )

    private fun requestedLayoutDirection(): Int {
        return if (smokeProfile.startsWith(RTL_PROFILE_PREFIX)) {
            View.LAYOUT_DIRECTION_RTL
        } else {
            View.LAYOUT_DIRECTION_LTR
        }
    }

    private fun applyRequestedLayoutDirection(root: View) {
        val direction = requestedLayoutDirection()
        window.decorView.layoutDirection = direction
        root.layoutDirection = direction
        root.requestLayout()
    }

    private fun verifyRequestedLayoutDirection(root: View) {
        check(root.layoutDirection == requestedLayoutDirection()) {
            "${root.debugName()} did not apply the $smokeProfile layout direction"
        }
    }

    private fun verifyIconAccessibility(root: View) {
        fun visit(view: View) {
            if (view.visibility != View.VISIBLE) return

            val iconOnlyControl =
                view is ImageButton ||
                    view is ImageView && view.isClickable

            if (iconOnlyControl && view.isEnabled && view.isShown) {
                check(!view.contentDescription.isNullOrBlank()) {
                    "Visible icon control ${view.debugName()} has no content description"
                }
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))
                }
            }
        }

        visit(root)
    }

    private fun verifyLargeFontText(root: View) {
        if (!smokeProfile.startsWith(LARGE_FONT_PROFILE_PREFIX)) return

        fun visit(view: View) {
            if (view.visibility != View.VISIBLE || !view.isShown) return

            if (view is TextView && view.text.isNotBlank()) {
                val textLayout = view.layout
                if (textLayout != null) {
                    for (line in 0 until textLayout.lineCount) {
                        check(textLayout.getEllipsisCount(line) == 0) {
                            "${view.debugName()} ellipsized text at 200% font scale"
                        }
                    }
                }

                if (view.id in STRICT_SINGLE_LINE_TEXT_IDS) {
                    val availableWidth = (
                        view.width - view.compoundPaddingLeft - view.compoundPaddingRight
                    ).coerceAtLeast(0)
                    val desiredWidth = Layout.getDesiredWidth(view.text, view.paint)
                    check(desiredWidth <= availableWidth + TEXT_WIDTH_TOLERANCE_PX) {
                        "${view.debugName()} clipped text at 200% font scale: " +
                            "$desiredWidth > $availableWidth"
                    }
                }
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))
                }
            }
        }

        visit(root)
    }

    private fun renderResult(message: String) {
        supportFragmentManager.fragments.forEach { fragment ->
            supportFragmentManager.commitNow {
                remove(fragment)
            }
        }

        setContentView(
            TextView(this).apply {
                text = message
                gravity = Gravity.CENTER
                textSize = 20f
                id = View.generateViewId()
            }
        )
    }

    private data class SmokeScreen(
        val name: String,
        val destinationId: Int,
        val fragmentClass: Class<out Fragment>
    )

    private companion object {
        const val SMOKE_CONTAINER_ID = 0x5A030001
        const val SMOKE_GRAPH_ID = 0x5A030002
        const val DESTINATION_AQUARIUM = 0x5A030101
        const val DESTINATION_MAINTENANCE = 0x5A030102
        const val DESTINATION_DEVICES = 0x5A030103
        const val DESTINATION_SETTINGS = 0x5A030104
        const val SMOKE_NAV_HOST_TAG = "release_smoke_nav_host"
        const val SCREEN_SETTLE_MILLIS = 700L
        const val LAYOUT_DIRECTION_SETTLE_MILLIS = 200L
        const val PASS_MARKER = "RELEASE_SMOKE_PASS"
        const val FAIL_MARKER = "RELEASE_SMOKE_FAIL"
        const val EXTRA_SMOKE_THEME = "aqua_smoke_theme"
        const val EXTRA_SMOKE_PROFILE = "aqua_smoke_profile"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val LARGE_FONT_PROFILE_PREFIX = "large-font-"
        const val RTL_PROFILE_PREFIX = "rtl-"
        const val TEXT_WIDTH_TOLERANCE_PX = 1f

        val STRICT_SINGLE_LINE_TEXT_IDS = setOf(
            R.id.tabAll,
            R.id.tabToday,
            R.id.tabUpcoming,
            R.id.tabHistory,
            R.id.btnEmptyAddCareTask
        )
    }
}

private fun View.debugName(): String = runCatching {
    resources.getResourceEntryName(id)
}.getOrElse {
    this::class.java.simpleName
}
