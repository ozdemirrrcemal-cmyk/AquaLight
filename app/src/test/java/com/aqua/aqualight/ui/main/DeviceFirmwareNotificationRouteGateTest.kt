package com.aqua.aqualight.ui.main

import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteDecision
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteOperations
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareNotificationRouteGateTest {

    @Test
    fun ownerChangeBetweenChecksRejectsStaleIntent() {
        var session = authenticated(OWNER_A)
        val operations = FakeRouteOperations()
        val gate = gate({ session }, operations)

        assertEquals(
            DeviceFirmwareNotificationRouteDecision.OPEN,
            gate.evaluate(DEVICE_UID, OWNER_A)
        )
        session = authenticated(OWNER_B)

        assertEquals(
            DeviceFirmwareNotificationRouteDecision.REJECT,
            gate.evaluate(DEVICE_UID, OWNER_A)
        )
    }

    @Test
    fun deviceDeletionBetweenChecksRejectsStaleIntent() {
        val operations = FakeRouteOperations()
        val gate = gate({ authenticated(OWNER_A) }, operations)

        assertEquals(
            DeviceFirmwareNotificationRouteDecision.OPEN,
            gate.evaluate(DEVICE_UID, OWNER_A)
        )
        operations.decision = DeviceFirmwareNotificationRouteDecision.REJECT

        assertEquals(
            DeviceFirmwareNotificationRouteDecision.REJECT,
            gate.evaluate(DEVICE_UID, OWNER_A)
        )
    }

    @Test
    fun repositoryNotReadyDefersWithoutDroppingIntent() {
        val operations = FakeRouteOperations(
            decision = DeviceFirmwareNotificationRouteDecision.DEFER
        )
        val gate = gate({ authenticated(OWNER_A) }, operations)

        assertEquals(
            DeviceFirmwareNotificationRouteDecision.DEFER,
            gate.evaluate(DEVICE_UID, OWNER_A)
        )
    }

    @Test
    fun signedOutSessionDefersBeforeDeviceLookup() {
        val operations = FakeRouteOperations()
        val gate = gate(
            sessionSnapshot = {
                MainNavigationSessionSnapshot(
                    isAuthenticated = false,
                    activeOwnerUid = ""
                )
            },
            operations = operations
        )

        assertEquals(
            DeviceFirmwareNotificationRouteDecision.DEFER,
            gate.evaluate(DEVICE_UID, OWNER_A)
        )
        assertTrue(operations.evaluatedDeviceUids.isEmpty())
    }

    @Test
    fun successfulNavigationAcknowledgementDismissesExactAvailability() = runTest {
        val operations = FakeRouteOperations()
        val gate = gate({ authenticated(OWNER_A) }, operations)

        gate.acknowledgeOpened(" $DEVICE_UID ", " $OWNER_A ")

        assertEquals(listOf("$OWNER_A:$DEVICE_UID"), operations.dismissedAvailability)
    }

    private fun gate(
        sessionSnapshot: () -> MainNavigationSessionSnapshot,
        operations: FakeRouteOperations
    ): DeviceFirmwareNotificationRouteGate {
        return DeviceFirmwareNotificationRouteGate(
            sessionSnapshot = sessionSnapshot,
            routeOperations = operations
        )
    }

    private fun authenticated(ownerUid: String): MainNavigationSessionSnapshot {
        return MainNavigationSessionSnapshot(
            isAuthenticated = true,
            activeOwnerUid = ownerUid
        )
    }

    private class FakeRouteOperations(
        var decision: DeviceFirmwareNotificationRouteDecision =
            DeviceFirmwareNotificationRouteDecision.OPEN
    ) : DeviceFirmwareNotificationRouteOperations {
        val evaluatedDeviceUids = mutableListOf<String>()
        val dismissedAvailability = mutableListOf<String>()

        override fun evaluate(deviceUid: String): DeviceFirmwareNotificationRouteDecision {
            evaluatedDeviceUids += deviceUid
            return decision
        }

        override suspend fun dismissOpenedAvailability(ownerUid: String, deviceUid: String) {
            dismissedAvailability += "$ownerUid:$deviceUid"
        }
    }

    private companion object {
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
        const val DEVICE_UID = "device-a"
    }
}
