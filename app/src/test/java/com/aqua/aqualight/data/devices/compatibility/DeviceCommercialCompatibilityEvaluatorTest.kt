package com.aqua.aqualight.data.devices.compatibility

import com.aqua.aqualight.application.devices.DefaultDeviceAccessPolicy
import com.aqua.aqualight.application.devices.DeviceAccessDecision
import com.aqua.aqualight.application.devices.DeviceCompatibilitySnapshot
import com.aqua.aqualight.application.devices.DeviceCompatibilityStatus
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCommercialCompatibilityEvaluatorTest {

    @Test
    fun `all seven products share one compatible commercial evaluator`() {
        val evaluations = AqlCommercialDeviceCatalog.products.map { product ->
            DeviceCommercialCompatibilityEvaluator.evaluate(product.toSnapshot())
        }

        assertEquals(7, evaluations.size)
        assertTrue(evaluations.all { evaluation ->
            evaluation is DeviceCommercialCompatibilityEvaluation.Compatible
        })
    }

    @Test
    fun `removing a base family feature blocks only through base compatibility`() {
        val cases = listOf(
            "LIGHT_RGB_PRO_SLIM" to AqlDeviceFeatureKey.LIGHT_CONTROL,
            "TIMER_RELAY_PRO_2" to AqlDeviceFeatureKey.TIMER_CONTROL,
            "DOSING_DOSE_PRO_2" to AqlDeviceFeatureKey.DOSING_CONTROL,
            "COOLING_COOL_PRO_1F" to AqlDeviceFeatureKey.COOLING_CONTROL
        )

        cases.forEach { (productKey, baseFeature) ->
            val product = product(productKey)
            val snapshot = product.toSnapshot().copy(
                supportedFeatures = product.profile.supportedFeatures
                    .filterNot { feature -> feature == baseFeature }
                    .map { feature -> feature.wireValue }
            )

            val evaluation = DeviceCommercialCompatibilityEvaluator.evaluate(snapshot)
                as DeviceCommercialCompatibilityEvaluation.Incompatible

            assertEquals(
                DeviceCommercialCompatibilityIssue.BASE_CONTRACT_INCOMPATIBLE,
                evaluation.issue
            )
        }
    }

    @Test
    fun `old Light without managed plan keeps base root while Quick Setup stays unavailable`() {
        val product = product("LIGHT_WRGB_PRO_ELITE")
        val snapshot = product.toSnapshot().copy(
            supportedFeatures = product.profile.supportedFeatures
                .filterNot { feature -> feature == AqlDeviceFeatureKey.LIGHT_MANAGED_AUTO_PLAN }
                .map { feature -> feature.wireValue }
        )

        val evaluation = DeviceCommercialCompatibilityEvaluator.evaluate(snapshot)
            as DeviceCommercialCompatibilityEvaluation.Compatible

        assertFalse(DeviceRootMenuFeature.LIGHT_QUICK_SETUP in evaluation.menuFeatures)
        assertTrue(DeviceRootMenuFeature.LIGHT_MANUAL in evaluation.menuFeatures)
    }

    @Test
    fun `unknown additive feature token leaves base compatibility intact`() {
        val product = product("DOSING_DOSE_PRO_2")
        val snapshot = product.toSnapshot().copy(
            supportedFeatures = product.profile.supportedFeatures.map { it.wireValue } +
                "DOSING_FUTURE_OPTIONAL_V9"
        )

        val evaluation = DeviceCommercialCompatibilityEvaluator.evaluate(snapshot)

        assertTrue(evaluation is DeviceCommercialCompatibilityEvaluation.Compatible)
    }

    @Test
    fun `newer device api becomes app update required without becoming offline`() {
        val product = product("TIMER_RELAY_PRO_2")
        val evaluation = DeviceCommercialCompatibilityEvaluator.evaluate(
            product.toSnapshot().copy(apiVersion = "2")
        ) as DeviceCommercialCompatibilityEvaluation.Incompatible

        assertEquals(
            DeviceCommercialCompatibilityIssue.APPLICATION_UPDATE_REQUIRED,
            evaluation.issue
        )
    }

    @Test
    fun `feature access policy distinguishes unavailable from firmware-required`() {
        val compatibility = DeviceCompatibilitySnapshot(
            deviceUid = "AQL-LIGHT-TEST",
            family = OwnerDeviceFamily.LIGHT,
            status = DeviceCompatibilityStatus.COMPATIBLE,
            menuFeatures = setOf(DeviceRootMenuFeature.LIGHT_MANUAL),
            firmwareUpdateRequiredFeatures = setOf(DeviceRootMenuFeature.LIGHT_QUICK_SETUP)
        )

        val quickSetup = DefaultDeviceAccessPolicy.evaluateFeature(
            compatibility,
            DeviceRootMenuFeature.LIGHT_QUICK_SETUP
        )
        val presets = DefaultDeviceAccessPolicy.evaluateFeature(
            compatibility,
            DeviceRootMenuFeature.LIGHT_PRESETS
        )

        assertEquals(
            DeviceMenuUnavailableReason.FIRMWARE_UPDATE_REQUIRED,
            (quickSetup as DeviceAccessDecision.Blocked).reason
        )
        assertEquals(
            DeviceMenuUnavailableReason.FEATURE_UNAVAILABLE,
            (presets as DeviceAccessDecision.Blocked).reason
        )
    }

    private fun product(productKey: String): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { it.productKey.value == productKey }

    private fun AqlCommercialCatalogProduct.toSnapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = DeviceUid("compat-${model.value}"),
            customName = "Fixture $displayName"
        ),
        product = DeviceProduct(
            brand = "AquaLight",
            productId = productId.value,
            productKey = productKey.value,
            family = family,
            familyRaw = family.wireValue,
            line = line.value,
            model = model.value,
            displayName = displayName,
            skuId = skuId.value,
            skuCode = skuCode.value,
            hardwareRevision = hardwareRevision.value
        ),
        firmwareVersion = "1.0.0",
        apiVersion = "1",
        protocolVersion = "1",
        capabilities = DeviceCapabilities(
            light = profile.capabilities.light,
            manualLight = profile.capabilities.manualLight,
            lightProgram = profile.capabilities.lightProgram,
            lightPresets = profile.capabilities.lightPresets,
            lightSimulation = profile.capabilities.lightSimulation,
            fan = profile.capabilities.fan,
            cooling = profile.capabilities.cooling,
            temperature = profile.capabilities.temperature,
            standaloneTimer = profile.capabilities.standaloneTimer,
            dosing = profile.capabilities.dosing,
            timeSync = profile.capabilities.timeSync,
            ota = profile.capabilities.ota
        ),
        limits = DeviceLimits(
            lightChannelCount = limits.lightChannelCount,
            fanOutputCount = limits.fanOutputCount,
            temperatureSensorCount = limits.temperatureSensorCount,
            timerChannelCount = limits.timerChannelCount,
            dosingChannelCount = limits.dosingChannelCount
        ),
        supportedFeatures = profile.supportedFeatures.map { feature -> feature.wireValue },
        supportedScreens = profile.supportedScreens.map { screen -> screen.wireValue },
        runtimeMetadataGeneration = 1L
    )
}
