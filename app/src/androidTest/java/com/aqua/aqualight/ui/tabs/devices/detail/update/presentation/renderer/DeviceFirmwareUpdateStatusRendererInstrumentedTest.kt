package com.aqua.aqualight.ui.tabs.devices.detail.update.presentation.renderer

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.databinding.FragmentDeviceFirmwareUpdateBinding
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateMode
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateUiState
import com.aqua.aqualight.ui.tabs.devices.detail.update.controller.DeviceFirmwareUpdateMotionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceFirmwareUpdateStatusRendererInstrumentedTest {

    @Test
    fun `hero transition clears stale target version views`() = withRenderer { fixture ->
        fixture.renderer.renderHero(availableState(), modeChanged = false)

        assertTrue(fixture.binding.targetVersionGroup.isVisible)
        assertTrue(fixture.binding.ivVersionArrow.isVisible)

        fixture.renderer.renderHero(
            DeviceFirmwareUpdateUiState(mode = DeviceFirmwareUpdateMode.CHECKING),
            modeChanged = false
        )

        val unavailable = fixture.context.getString(R.string.common_not_available_em_dash)
        val fallbackName = fixture.context.getString(
            R.string.device_settings_update_for_device,
            fixture.context.getString(R.string.device_settings_update_device_fallback)
        )
        assertFalse(fixture.binding.targetVersionGroup.isVisible)
        assertFalse(fixture.binding.ivVersionArrow.isVisible)
        assertEquals(unavailable, fixture.binding.tvInstalledVersion.text.toString())
        assertEquals(unavailable, fixture.binding.tvTargetVersion.text.toString())
        assertEquals(fallbackName, fixture.binding.tvUpdateDeviceName.text.toString())
    }

    @Test
    fun `available action replaces loading state completely`() = withRenderer { fixture ->
        fixture.renderer.renderAction(
            DeviceFirmwareUpdateUiState(mode = DeviceFirmwareUpdateMode.LOADING)
        )

        assertTrue(fixture.binding.progressUpdateAction.isVisible)
        assertFalse(fixture.binding.btnUpdateAction.isEnabled)
        assertTrue(fixture.binding.btnUpdateAction.text.isEmpty())
        assertEquals(
            fixture.context.getString(R.string.device_settings_update_action_loading),
            fixture.binding.btnUpdateAction.contentDescription.toString()
        )

        fixture.renderer.renderAction(availableState())

        val action = fixture.context.getString(R.string.device_settings_update_now_action)
        assertFalse(fixture.binding.progressUpdateAction.isVisible)
        assertTrue(fixture.binding.btnUpdateAction.isEnabled)
        assertEquals(action, fixture.binding.btnUpdateAction.text.toString())
        assertEquals(action, fixture.binding.btnUpdateAction.contentDescription.toString())
        assertTrue(fixture.binding.tvUpdateActionHint.isVisible)
        assertEquals(
            fixture.context.getString(R.string.device_settings_update_action_hint_available),
            fixture.binding.tvUpdateActionHint.text.toString()
        )
    }

    @Test
    fun `terminal failure replaces recoverable retry action`() = withRenderer { fixture ->
        fixture.renderer.renderAction(failedState(recoverable = true))

        assertEquals(
            fixture.context.getString(R.string.device_settings_retry_update_action),
            fixture.binding.btnUpdateAction.text.toString()
        )

        fixture.renderer.renderAction(failedState(recoverable = false))

        assertTrue(fixture.binding.btnUpdateAction.isEnabled)
        assertEquals(
            fixture.context.getString(R.string.device_settings_update_close_action),
            fixture.binding.btnUpdateAction.text.toString()
        )
        assertFalse(fixture.binding.tvUpdateActionHint.isVisible)
    }
}

private data class RendererFixture(
    val context: Context,
    val binding: FragmentDeviceFirmwareUpdateBinding,
    val renderer: DeviceFirmwareUpdateStatusRenderer
)

private fun withRenderer(block: (RendererFixture) -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        block(createRendererFixture())
    }
}

private fun createRendererFixture(): RendererFixture {
    val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.AppTheme
    )
    val binding = FragmentDeviceFirmwareUpdateBinding.inflate(
        LayoutInflater.from(context)
    )
    val fragment = RendererTestFragment(context)
    val motion = DeviceFirmwareUpdateMotionController(fragment, binding)
    return RendererFixture(
        context = context,
        binding = binding,
        renderer = DeviceFirmwareUpdateStatusRenderer(fragment, binding, motion)
    )
}

private class RendererTestFragment(
    private val rendererContext: Context
) : Fragment() {
    override fun getContext(): Context = rendererContext
}

private fun availableState(): DeviceFirmwareUpdateUiState = DeviceFirmwareUpdateUiState(
    mode = DeviceFirmwareUpdateMode.AVAILABLE,
    deviceName = "Aqua Light",
    currentVersion = "1.4.0",
    targetVersion = "1.5.0"
)

private fun failedState(recoverable: Boolean): DeviceFirmwareUpdateUiState =
    DeviceFirmwareUpdateUiState(
        mode = DeviceFirmwareUpdateMode.FAILED,
        failure = DeviceOtaFailure(
            reason = DeviceOtaFailureReason.CONNECTION,
            recoverable = recoverable
        )
    )
