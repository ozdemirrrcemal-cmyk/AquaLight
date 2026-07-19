package com.aqua.aqualight.smoke

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
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
import com.aqua.aqualight.app.AquaApp
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.ui.tabs.aquarium.AquariumFragment
import com.aqua.aqualight.ui.tabs.devices.DevicesFragment
import com.aqua.aqualight.ui.tabs.maintenance.AquariumMaintenanceFragment
import com.aqua.aqualight.ui.tabs.settings.SettingsFragment
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CI-only Activity packaged exclusively in the minified releaseSmoke variant.
 * It exercises the real Fragment lifecycle, navigation environment and release-smoke
 * ViewModel factory callsites without opening Firebase, BLE or WebSocket infrastructure.
 */
class ReleaseSmokeActivity : BaseActivity() {

    private lateinit var navHostFragment: NavHostFragment
    private lateinit var navController: NavController
    private var smokeStarted = false

    private val smokeTheme: String by lazy {
        intent.getStringExtra(EXTRA_SMOKE_THEME)
            .orEmpty()
            .lowercase()
            .ifBlank { THEME_LIGHT }
    }

    private val smokeVariant: String by lazy {
        intent.getStringExtra(EXTRA_SMOKE_VARIANT)
            .orEmpty()
            .lowercase()
            .replace(Regex("[^a-z0-9_-]"), "-")
            .ifBlank { smokeTheme }
    }

    private val smokeRtl: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_SMOKE_RTL, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(
            if (smokeTheme == THEME_DARK) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
        (application as AquaApp).replaceAppContainerForProcess(
            ReleaseSmokeAppContainer(applicationContext)
        )
        super.onCreate(savedInstanceState)

        val layoutDirection = if (smokeRtl) {
            View.LAYOUT_DIRECTION_RTL
        } else {
            View.LAYOUT_DIRECTION_LTR
        }
        window.decorView.layoutDirection = layoutDirection

        setContentView(
            FrameLayout(this).apply {
                id = SMOKE_CONTAINER_ID
                this.layoutDirection = layoutDirection
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
                    val fragmentView = checkNotNull(fragment.view) {
                        "${screen.name} did not create a view"
                    }
                    check(
                        fragment.lifecycle.currentState.isAtLeast(
                            Lifecycle.State.STARTED
                        )
                    ) {
                        "${screen.name} did not reach STARTED"
                    }
                    verifyVisibleIconControlDescriptions(fragmentView, screen.name)
                    captureScreen(screen)
                }
            }.onSuccess {
                renderResult("$PASS_MARKER:$smokeVariant")
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
                        setClassName(screen.fragmentClass.name)
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

    private fun verifyVisibleIconControlDescriptions(root: View, screenName: String) {
        val missingDescriptions = mutableListOf<String>()
        root.forEachDescendantInclusive { view ->
            if (view.visibility != View.VISIBLE || !view.isEnabled) return@forEachDescendantInclusive
            val isIconOnlyControl = view is ImageButton || (view is ImageView && view.isClickable)
            if (!isIconOnlyControl) return@forEachDescendantInclusive

            if (view.contentDescription?.toString().isNullOrBlank()) {
                val resourceName = runCatching {
                    resources.getResourceEntryName(view.id)
                }.getOrDefault(view.javaClass.simpleName)
                missingDescriptions += resourceName
            }
        }
        check(missingDescriptions.isEmpty()) {
            "$screenName has visible icon-only controls without content descriptions: " +
                missingDescriptions.joinToString()
        }
    }

    private fun View.forEachDescendantInclusive(block: (View) -> Unit) {
        block(this)
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).forEachDescendantInclusive(block)
            }
        }
    }

    private fun captureScreen(screen: SmokeScreen) {
        val root = window.decorView.rootView
        check(root.width > 0 && root.height > 0) {
            "${screen.name} has invalid render bounds ${root.width}x${root.height}"
        }
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val screenshotRoot = getExternalFilesDir(null) ?: filesDir
        val directory = File(screenshotRoot, SCREENSHOT_DIRECTORY).apply { mkdirs() }
        val output = File(
            directory,
            "${smokeVariant}-${screen.name.removeSuffix("Fragment").lowercase()}.png"
        )
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "${screen.name} screenshot could not be encoded"
            }
        }
        check(output.isFile && output.length() > MIN_SCREENSHOT_BYTES) {
            "${screen.name} screenshot is empty"
        }
        bitmap.recycle()
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
        const val PASS_MARKER = "RELEASE_SMOKE_PASS"
        const val FAIL_MARKER = "RELEASE_SMOKE_FAIL"
        const val EXTRA_SMOKE_THEME = "aqua_smoke_theme"
        const val EXTRA_SMOKE_VARIANT = "aqua_smoke_variant"
        const val EXTRA_SMOKE_RTL = "aqua_smoke_rtl"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val SCREENSHOT_DIRECTORY = "smoke-screens"
        const val MIN_SCREENSHOT_BYTES = 1024L
    }
}
