package com.aqua.aqualight.smoke

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
 * CI-only Activity packaged in releaseSmoke and debug test variants.
 * It exercises real Fragment lifecycle/rendering without opening Firebase, BLE or WebSocket
 * infrastructure. Production Release does not package this source set.
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
    private val smokeLocaleTag: String by lazy {
        intent.getStringExtra(EXTRA_SMOKE_LOCALE)
            .orEmpty()
            .ifBlank { DEFAULT_LOCALE }
    }
    private val smokeFontScaleLabel: String by lazy {
        intent.getStringExtra(EXTRA_SMOKE_FONT_SCALE)
            .orEmpty()
            .ifBlank { DEFAULT_FONT_SCALE_LABEL }
    }
    private val requestedScreenName: String? by lazy {
        intent.getStringExtra(EXTRA_SMOKE_SCREEN)
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }
    private val holdForAccessibilityAudit: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_HOLD_FOR_ACCESSIBILITY, false)
    }
    private val smokeModeKey: String by lazy {
        listOf(
            smokeLocaleTag.lowercase().replace('-', '_'),
            smokeTheme,
            "font${smokeFontScaleLabel.replace('.', '_')}"
        ).joinToString("-")
    }
    private val activeScreens: List<SmokeScreen> by lazy {
        val screens = allSmokeScreens()
        val requested = requestedScreenName ?: return@lazy screens
        listOf(
            requireNotNull(screens.firstOrNull { screen -> screen.name == requested }) {
                "Unknown smoke screen: $requested"
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(
            if (smokeTheme == THEME_DARK) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(smokeLocaleTag)
        )
        (application as AquaApp).replaceAppContainerForProcess(
            ReleaseSmokeAppContainer(applicationContext)
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
                activeScreens.forEach { screen ->
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
                    val fragmentView = requireNotNull(fragment.view) {
                        "${screen.name} did not create a view"
                    }
                    check(
                        fragment.lifecycle.currentState.isAtLeast(
                            Lifecycle.State.STARTED
                        )
                    ) {
                        "${screen.name} did not reach STARTED"
                    }
                    captureScreen(screen)

                    if (holdForAccessibilityAudit) {
                        fragmentView.contentDescription = "$ACCESSIBILITY_READY_PREFIX:${screen.name}"
                        return@launch
                    }
                }
            }.onSuccess {
                if (!holdForAccessibilityAudit) {
                    renderResult("$PASS_MARKER:$smokeModeKey")
                }
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

        return NavGraph(graphNavigator).apply {
            id = SMOKE_GRAPH_ID
            activeScreens.forEach { screen ->
                addDestination(
                    fragmentNavigator.createDestination().apply {
                        id = screen.destinationId
                        setClassName(screen.fragmentClass.name)
                    }
                )
            }
            setStartDestination(activeScreens.first().destinationId)
        }
    }

    private fun allSmokeScreens(): List<SmokeScreen> = listOf(
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
            "$smokeModeKey-${screen.name.removeSuffix("Fragment").lowercase()}.png"
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
        const val ACCESSIBILITY_READY_PREFIX = "ACCESSIBILITY_READY"
        const val EXTRA_SMOKE_THEME = "aqua_smoke_theme"
        const val EXTRA_SMOKE_LOCALE = "aqua_smoke_locale"
        const val EXTRA_SMOKE_FONT_SCALE = "aqua_smoke_font_scale"
        const val EXTRA_SMOKE_SCREEN = "aqua_smoke_screen"
        const val EXTRA_HOLD_FOR_ACCESSIBILITY = "aqua_hold_for_accessibility"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val DEFAULT_LOCALE = "en"
        const val DEFAULT_FONT_SCALE_LABEL = "1.0"
        const val SCREENSHOT_DIRECTORY = "smoke-screens"
        const val MIN_SCREENSHOT_BYTES = 1024L
    }
}
