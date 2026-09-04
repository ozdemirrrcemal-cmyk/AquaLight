package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGeneration
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeDomainBootstrapCoordinatorTest {
    @Test
    fun `validated catalog families resolve exact owning domain bootstrap plans`() {
        val products = AqlCommercialDeviceCatalog.products
        val lightWithThermal = products.first { product ->
            product.family == DeviceFamily.LIGHT &&
                AqlDeviceFeatureKey.LIGHT_FAN_CONTROL in product.profile.supportedFeatures
        }
        val lightWithoutThermal = products.first { product ->
            product.family == DeviceFamily.LIGHT &&
                AqlDeviceFeatureKey.LIGHT_FAN_CONTROL !in product.profile.supportedFeatures
        }
        val cooling = products.first { it.family == DeviceFamily.COOLING }
        val timer = products.first { it.family == DeviceFamily.TIMER }
        val dosing = products.first { it.family == DeviceFamily.DOSING }

        assertEquals(
            listOf(
                DeviceRuntimeDomainBootstrapStep.LIGHT_STATUS,
                DeviceRuntimeDomainBootstrapStep.LIGHT_THERMAL_STATUS
            ),
            plan(lightWithThermal).steps
        )
        assertEquals(
            listOf(DeviceRuntimeDomainBootstrapStep.LIGHT_STATUS),
            plan(lightWithoutThermal).steps
        )
        assertEquals(
            listOf(DeviceRuntimeDomainBootstrapStep.COOLING_STATUS),
            plan(cooling).steps
        )
        assertEquals(
            listOf(DeviceRuntimeDomainBootstrapStep.TIMER_STATUS),
            plan(timer).steps
        )
        assertEquals(
            listOf(DeviceRuntimeDomainBootstrapStep.DOSING_STATUS),
            plan(dosing).steps
        )
    }

    @Test
    fun `coordinator executes owning domain steps sequentially and once per generation`() = runTest {
        val coordinator = DeviceRuntimeDomainBootstrapCoordinator()
        val plan = DeviceRuntimeDomainBootstrapPlan(
            deviceUid = DEVICE_UID,
            connectionGeneration = CONNECTION_GENERATION,
            metadataGeneration = METADATA_GENERATION,
            steps = listOf(
                DeviceRuntimeDomainBootstrapStep.LIGHT_STATUS,
                DeviceRuntimeDomainBootstrapStep.LIGHT_THERMAL_STATUS
            )
        )
        val calls = mutableListOf<DeviceRuntimeDomainBootstrapStep>()

        val first = coordinator.run(plan) { step ->
            calls += step
            success(step)
        }
        val duplicate = coordinator.run(plan) { step ->
            calls += step
            success(step)
        }

        assertTrue(first is DeviceRuntimeDomainBootstrapResult.Completed)
        assertTrue(duplicate is DeviceRuntimeDomainBootstrapResult.AlreadyStarted)
        assertEquals(plan.steps, calls)
    }

    @Test
    fun `failure stops later owning domain steps and never reports completion`() = runTest {
        val coordinator = DeviceRuntimeDomainBootstrapCoordinator()
        val plan = DeviceRuntimeDomainBootstrapPlan(
            deviceUid = DEVICE_UID,
            connectionGeneration = CONNECTION_GENERATION,
            metadataGeneration = METADATA_GENERATION,
            steps = listOf(
                DeviceRuntimeDomainBootstrapStep.LIGHT_STATUS,
                DeviceRuntimeDomainBootstrapStep.LIGHT_THERMAL_STATUS
            )
        )
        val calls = mutableListOf<DeviceRuntimeDomainBootstrapStep>()

        val result = coordinator.run(plan) { step ->
            calls += step
            if (step == DeviceRuntimeDomainBootstrapStep.LIGHT_STATUS) {
                DeviceRuntimeCommandOutcome.ProtocolError(
                    deviceUid = DEVICE_UID,
                    module = "light",
                    action = "status.get",
                    messageId = "bootstrap-failure",
                    generation = CONNECTION_GENERATION,
                    reason = "malformed fixture"
                )
            } else {
                success(step)
            }
        }

        assertTrue(result is DeviceRuntimeDomainBootstrapResult.Failed)
        assertEquals(listOf(DeviceRuntimeDomainBootstrapStep.LIGHT_STATUS), calls)
    }

    @Test
    fun `cleared or replaced generation cannot complete stale bootstrap`() = runTest {
        val coordinator = DeviceRuntimeDomainBootstrapCoordinator()
        val plan = DeviceRuntimeDomainBootstrapPlan(
            deviceUid = DEVICE_UID,
            connectionGeneration = CONNECTION_GENERATION,
            metadataGeneration = METADATA_GENERATION,
            steps = listOf(DeviceRuntimeDomainBootstrapStep.COOLING_STATUS)
        )

        val result = coordinator.run(plan) { step ->
            coordinator.clear(DEVICE_UID)
            success(step)
        }

        assertTrue(result is DeviceRuntimeDomainBootstrapResult.Stale)
    }

    private fun plan(
        product: com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
    ): DeviceRuntimeDomainBootstrapPlan = DeviceRuntimeDomainBootstrapPlanResolver.resolve(
        deviceUid = DEVICE_UID,
        connectionGeneration = CONNECTION_GENERATION,
        metadataGeneration = METADATA_GENERATION,
        product = product
    )

    private fun success(
        step: DeviceRuntimeDomainBootstrapStep
    ): DeviceRuntimeCommandOutcome.Success<Unit> = DeviceRuntimeCommandOutcome.Success(
        deviceUid = DEVICE_UID,
        module = "bootstrap",
        action = step.name,
        messageId = "message-${step.name}",
        generation = CONNECTION_GENERATION,
        statusCode = 200,
        value = Unit
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-BOOTSTRAP-TEST")
        val CONNECTION_GENERATION = DeviceRuntimeConnectionGeneration(7L)
        val METADATA_GENERATION = DeviceRuntimeMetadataGeneration(3L)
    }
}
