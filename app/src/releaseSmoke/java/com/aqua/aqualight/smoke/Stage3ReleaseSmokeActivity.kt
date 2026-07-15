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
 * It exercises the real Fragment lifecycle and the Stage 3 ViewModel factory
 * callsites without opening Firebase, BLE or WebSocket infrastructure.
 */
class Stage3ReleaseSmokeActivity : BaseActivity() {

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
    }

    override fun onPostResume() {
        super.onPostResume()
        if (smokeStarted) return
        smokeStarted = true

        lifecycleScope.launch {
            runCatching {
                smokeScreens().forEach { screen ->
                    val fragment = screen.create()
                    supportFragmentManager.commitNow {
                        replace(SMOKE_CONTAINER_ID, fragment, screen.name)
                    }

                    delay(SCREEN_SETTLE_MILLIS)

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

    private fun smokeScreens(): List<SmokeScreen> = listOf(
        SmokeScreen("AquariumFragment") { AquariumFragment() },
        SmokeScreen("AquariumMaintenanceFragment") {
            AquariumMaintenanceFragment()
        },
        SmokeScreen("DevicesFragment") { DevicesFragment() },
        SmokeScreen("SettingsFragment") { SettingsFragment() }
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
        val create: () -> Fragment
    )

    private companion object {
        const val SMOKE_CONTAINER_ID = 0x5A030001
        const val SCREEN_SETTLE_MILLIS = 500L
        const val PASS_MARKER = "STAGE3_RELEASE_SMOKE_PASS"
        const val FAIL_MARKER = "STAGE3_RELEASE_SMOKE_FAIL"
    }
}
