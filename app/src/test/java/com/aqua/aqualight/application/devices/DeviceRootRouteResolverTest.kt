package com.aqua.aqualight.application.devices

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRootRouteResolverTest {

    @Test
    fun `each family resolves only its exact destinations`() {
        val light = mapOf(
            DeviceRootMenuFeature.LIGHT_MANUAL to DeviceRootRoute.LIGHT_MANUAL,
            DeviceRootMenuFeature.LIGHT_QUICK_SETUP to DeviceRootRoute.LIGHT_QUICK_SETUP,
            DeviceRootMenuFeature.LIGHT_PROGRAMS to DeviceRootRoute.LIGHT_PROGRAMS,
            DeviceRootMenuFeature.LIGHT_PRESETS to DeviceRootRoute.LIGHT_PRESETS,
            DeviceRootMenuFeature.LIGHT_SIMULATION to DeviceRootRoute.LIGHT_SIMULATION,
            DeviceRootMenuFeature.COOLING_FANS to DeviceRootRoute.LIGHT_FAN_CONTROL,
            DeviceRootMenuFeature.COOLING_TEMPERATURE to
                DeviceRootRoute.LIGHT_TEMPERATURE_PROTECTION,
            DeviceRootMenuFeature.DEVICE_SETTINGS to DeviceRootRoute.DEVICE_SETTINGS
        )
        val timer = mapOf(
            DeviceRootMenuFeature.TIMER_CHANNELS to DeviceRootRoute.TIMER_CHANNELS,
            DeviceRootMenuFeature.TIMER_SCHEDULES to DeviceRootRoute.TIMER_SCHEDULES,
            DeviceRootMenuFeature.DEVICE_SETTINGS to DeviceRootRoute.DEVICE_SETTINGS
        )
        val dosing = mapOf(
            DeviceRootMenuFeature.DOSING_CHANNELS to DeviceRootRoute.DOSING_CHANNELS,
            DeviceRootMenuFeature.DOSING_CALIBRATION to DeviceRootRoute.DOSING_CALIBRATION,
            DeviceRootMenuFeature.DOSING_SCHEDULES to DeviceRootRoute.DOSING_SCHEDULES,
            DeviceRootMenuFeature.DEVICE_SETTINGS to DeviceRootRoute.DEVICE_SETTINGS
        )
        val cooling = mapOf(
            DeviceRootMenuFeature.COOLING_FANS to DeviceRootRoute.COOLING_CONTROL,
            DeviceRootMenuFeature.COOLING_TEMPERATURE to DeviceRootRoute.COOLING_TEMPERATURE,
            DeviceRootMenuFeature.DEVICE_SETTINGS to DeviceRootRoute.DEVICE_SETTINGS
        )

        assertExactRoutes(OwnerDeviceFamily.LIGHT, light)
        assertExactRoutes(OwnerDeviceFamily.TIMER, timer)
        assertExactRoutes(OwnerDeviceFamily.DOSING, dosing)
        assertExactRoutes(OwnerDeviceFamily.COOLING, cooling)

        DeviceRootMenuFeature.entries.forEach { feature ->
            assertNull(DeviceRootRouteResolver.resolve(OwnerDeviceFamily.UNKNOWN, feature))
        }
    }

    @Test
    fun `second stage authorization requires valid catalog state and allowed route`() {
        val allowed = DeviceRootRoute.DOSING_CHANNELS
        val operations = FakeRootOperations(
            DeviceRootSnapshot(
                deviceUid = "dosing-device",
                title = "Dose Pro 2",
                availability = OwnerDeviceAvailability.REACHABLE,
                family = OwnerDeviceFamily.DOSING,
                catalogState = DeviceRootCatalogState.VALID,
                allowedRoutes = setOf(allowed)
            )
        )

        assertTrue(operations.authorizeRoute("dosing-device", allowed))
        assertFalse(
            operations.authorizeRoute(
                "dosing-device",
                DeviceRootRoute.TIMER_CHANNELS
            )
        )

        operations.snapshot = operations.snapshot?.copy(
            catalogState = DeviceRootCatalogState.INVALID
        )
        assertFalse(operations.authorizeRoute("dosing-device", allowed))

        operations.snapshot = null
        assertFalse(operations.authorizeRoute("dosing-device", allowed))
    }

    private fun assertExactRoutes(
        family: OwnerDeviceFamily,
        expected: Map<DeviceRootMenuFeature, DeviceRootRoute>
    ) {
        DeviceRootMenuFeature.entries.forEach { feature ->
            assertEquals(
                expected[feature],
                DeviceRootRouteResolver.resolve(family, feature)
            )
        }
    }

    private class FakeRootOperations(
        var snapshot: DeviceRootSnapshot?
    ) : DeviceRootOperations {
        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = flowOf(snapshot)

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshot

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
    }
}
