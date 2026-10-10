package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.TankDeviceListItem
import com.aqua.aqualight.application.devices.groups.LightGroupCompatibility
import com.aqua.aqualight.application.devices.groups.TankControlGroupDevice
import com.aqua.aqualight.application.devices.groups.TankControlGroupDevices
import com.aqua.aqualight.databinding.FragmentTankControlGroupCreateBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TankControlGroupRendererInstrumentedTest {
    @Test fun readySelectionReturningToEmptyTankClearsAllSelectedVisuals() = onMain {
        val binding = createBinding()
        val renderer = renderer(binding)
        val lights = listOf(light("left"), light("right"))
        renderer.render(TankControlGroupCreateUiState(
            source = TankControlGroupDevices("Nature Aquarium", lights, isLoading = false),
            selected = lights, hiddenCount = 1, isReady = true
        ))
        assertTrue(binding.btnCreate.isEnabled)
        assertTrue(binding.compatibilityCard.isVisible)
        assertTrue(binding.ivComplete.isVisible)
        assertTrue(binding.tvHiddenCount.isVisible)
        assertFalse(binding.emptyLightIconContainer.isVisible)
        assertEquals(1, binding.headerContainer.childCount)

        renderer.render(TankControlGroupCreateUiState(
            source = TankControlGroupDevices("Other tank", isLoading = false)
        ))
        assertFalse(binding.btnCreate.isEnabled)
        assertFalse(binding.compatibilityCard.isVisible)
        assertFalse(binding.ivComplete.isVisible)
        assertFalse(binding.tvHiddenCount.isVisible)
        assertTrue(binding.emptyLightIconContainer.isVisible)
        assertEquals(binding.root.context.getString(R.string.tank_group_empty_title),
            binding.tvEmptyTitle.text.toString())
    }

    @Test fun createFooterRemainsReachableWithLargeFontsOnShortScreen() = onMain {
        val binding = createBinding(LARGE_FONT_SCALE)
        val lights = listOf(light("left"), light("right"), light("rear"))
        renderer(binding).render(TankControlGroupCreateUiState(
            source = TankControlGroupDevices("Nature Aquarium", lights, isLoading = false), available = lights
        ))
        val density = binding.root.resources.displayMetrics.density
        val width = (SCREEN_WIDTH_DP * density).toInt()
        val height = (SCREEN_HEIGHT_DP * density).toInt()
        binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        binding.root.layout(0, 0, width, height)
        assertEquals(height, binding.createFooter.bottom)
        assertTrue(binding.contentScroll.height > 0)
        assertTrue(binding.btnCreate.height >= binding.root.resources.getDimensionPixelSize(R.dimen.aqua_size_48))
        assertTrue(binding.btnCreate.top >= 0)
    }

    private fun createBinding(scale: Float = 1f): FragmentTankControlGroupCreateBinding {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme)
        val configuration = Configuration(context.resources.configuration).apply { fontScale = scale }
        val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.AppTheme)
        return FragmentTankControlGroupCreateBinding.inflate(LayoutInflater.from(themed))
    }

    private fun renderer(binding: FragmentTankControlGroupCreateBinding): TankControlGroupRenderer {
        val group = TankControlGroupDeviceAdapter(true, { false }, { _, _ -> false })
        val devices = TankControlGroupDeviceAdapter(false, { false }, { _, _ -> false })
        binding.rvGroup.layoutManager = GridLayoutManager(binding.root.context, COLUMNS)
        binding.rvDevices.layoutManager = GridLayoutManager(binding.root.context, COLUMNS)
        binding.rvGroup.adapter = group
        binding.rvDevices.adapter = devices
        return TankControlGroupRenderer(binding, group, devices)
    }

    private fun light(uid: String) = TankControlGroupDevice(
        TankDeviceListItem(uid, uid, uid, OwnerDeviceFamily.LIGHT, OwnerDeviceAvailability.REACHABLE),
        "WRGB Pro Elite 120", LightGroupCompatibility("elite", "elite", "elite_120", "r1", 4)
    )

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private companion object {
        const val LARGE_FONT_SCALE = 1.4f
        const val SCREEN_WIDTH_DP = 360
        const val SCREEN_HEIGHT_DP = 640
        const val COLUMNS = 2
    }
}
