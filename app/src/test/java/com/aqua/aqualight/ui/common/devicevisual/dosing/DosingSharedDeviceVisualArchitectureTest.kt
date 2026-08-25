package com.aqua.aqualight.ui.common.devicevisual.dosing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingSharedDeviceVisualArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `operational pump stays hose free while identity visual owns hoses`() {
        val operational = source(SHARED_VISUAL_ROOT + "DosingPumpDeviceCompose.kt")
        val identity = source(SHARED_VISUAL_ROOT + "DosingDeviceIdentityVisual.kt")
        val pumpSection = source(DOSING_PUMP_ROOT + "DosingPumpSection.kt")

        assertTrue(operational.contains("package com.aqua.aqualight.ui.common.devicevisual.dosing"))
        assertTrue(pumpSection.contains("ui.common.devicevisual.dosing.DosingPumpDevice"))
        assertTrue(identity.contains("DosingIdentityHoses"))
        assertTrue(identity.contains("drawIdentityHose"))
        assertFalse(operational.contains("DosingIdentityHoses"))
        assertFalse(operational.contains("drawIdentityHose"))
    }

    @Test
    fun `device identity mappers choose shared dosing visual from authoritative family`() {
        val deviceListMapper = source(DEVICE_LIST_ROOT + "DeviceCardMapper.kt")
        val compactMapper = source(COMMON_DEVICE_CARD_ROOT + "DeviceCompactSnapshotMapper.kt")
        val settingsMapper = source(SETTINGS_DEVICE_ROOT + "DeviceStatusSnapshotMapper.kt")

        listOf(deviceListMapper, compactMapper, settingsMapper).forEach { mapper ->
            assertTrue(mapper.contains("OwnerDeviceFamily.DOSING"))
            assertTrue(mapper.contains("DeviceCompactVisualKind.DOSING_IDENTITY"))
        }
    }

    @Test
    fun `legacy view identity surfaces render through the shared dosing bridge`() {
        val compactBinder = source(COMMON_DEVICE_CARD_ROOT + "DeviceCompactCardBinder.kt")
        val settingsAdapter = source(SETTINGS_DEVICE_ROOT + "DeviceStatusAdapter.kt")
        val spotlightBinder = source(TANK_DEVICE_ROOT + "DosingDeviceSpotlightCardBinder.kt")

        assertTrue(compactBinder.contains("DosingDeviceVisualViewBinder.bindIdentity"))
        assertTrue(settingsAdapter.contains("DosingDeviceVisualViewBinder.bindIdentity"))
        assertTrue(spotlightBinder.contains("DosingDeviceVisualViewBinder.bindIdentity"))
    }

    @Test
    fun `numeric channel badge is replaced by the same shared pump head`() {
        val channelHeader = source(DOSING_CARD_ROOT + "DosingChannelCardHeader.kt")
        val spotlightBinder = source(TANK_DEVICE_ROOT + "DosingDeviceSpotlightCardBinder.kt")

        assertTrue(channelHeader.contains("DosingPumpHeadMarker"))
        assertTrue(spotlightBinder.contains("DosingDeviceVisualViewBinder.bindPumpHead"))
        assertFalse(channelHeader.contains("channelNumber.toString()"))
        assertFalse(spotlightBinder.contains("tvChannelBadge.text"))
    }

    @Test
    fun `shared dosing visuals remain presentation only and suppression free`() {
        sharedVisualSources().forEach { content ->
            assertFalse(content.contains("import com.aqua.aqualight.data."))
            assertFalse(content.contains("import com.aqua.aqualight.platform."))
            assertFalse(content.contains("import com.aqua.aqualight.application.devices.dosing."))
            assertFalse(content.contains("@file:Suppress("))
            assertFalse(content.contains("@Suppress("))
            assertFalse(content.contains("@SuppressLint("))
        }
    }

    private fun sharedVisualSources(): List<String> = File(repositoryRoot, SHARED_VISUAL_ROOT)
        .walkTopDown()
        .filter { file -> file.isFile && file.extension == "kt" }
        .map(File::readText)
        .toList()

    private fun source(relativePath: String): String = File(repositoryRoot, relativePath).readText()

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }

    private companion object {
        const val SHARED_VISUAL_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/common/devicevisual/dosing/"
        const val COMMON_DEVICE_CARD_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/common/devicecard/"
        const val DEVICE_LIST_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/"
        const val DOSING_CARD_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/presentation/card/"
        const val DOSING_PUMP_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/presentation/pump/"
        const val TANK_DEVICE_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/devices/"
        const val SETTINGS_DEVICE_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/device/"
    }
}
