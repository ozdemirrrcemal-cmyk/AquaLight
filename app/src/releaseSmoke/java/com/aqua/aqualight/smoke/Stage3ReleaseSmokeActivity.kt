package com.aqua.aqualight.smoke

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.NavHostFragment
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
 * It exercises the real Fragment lifecycle, navigation environment and Stage 3
 * ViewModel factory callsites without opening Firebase, BLE or WebSocket infrastructure.
 */
class Stage3ReleaseSmokeActivity : BaseActivity() {

    private lateinit var navHostFragment: NavHostFragment
    private lateinit var navController: NavController
    private var smokeStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as AquaApp).replaceAppContainerForProcess(
            Stage3SmokeAppContainer(applicationContext)
        )
        super.onCreate(savedInstanceState)

        setContentView(
            FrameLayout(this).apply {
                id = SMOKE_CONTAINER_ID
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
                smokeScreens().forEach { screen ->
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
                }
            }.onSuccess {
                renderResult(PASS_MARKER)
            }.onFailure { error ->
                renderResult(
                    "$FAIL_MARKER\n${error::class.java.name}\n${error.message.orEmpty()}"
                )
            }
        }
    }

    private fun createSmokeGraph(): NavGraph {
        val navigatorProvider = navController.navigatorProvider
        val graphNavigator = navigatorProvider.getNavigator(
            NavGraphNavigator::class.java
        )
        val fragmentNavigator = navigatorProvider.getNavigator(
            FragmentNavigator::class.java
        )
        val screens = smokeScreens()

        return NavGraph(graphNavigator).apply {
            id = SMOKE_GRAPH_ID
            screens.forEach { screen ->
                addDestination(
                    fragmentNavigator.createDestination().apply {
                        id = screen.destinationId
                        className = screen.fragmentClass.name
                        setLabel(screen.name)
                    }
                )
            }
            setStartDestination(screens.first().destinationId)
        }
    }

    private fun smokeScreens(): List<SmokeScreen> = listOf(
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
        const val SMOKE_NAV_HOST_TAG = "stage3_release_smoke_nav_host"
        const val SCREEN_SETTLE_MILLIS = 700L
        const val PASS_MARKER = "STAGE3_RELEASE_SMOKE_PASS"
        const val FAIL_MARKER = "STAGE3_RELEASE_SMOKE_FAIL"
    }
}
