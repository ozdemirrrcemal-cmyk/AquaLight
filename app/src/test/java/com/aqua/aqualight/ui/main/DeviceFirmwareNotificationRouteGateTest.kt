package com.aqua.aqualight.ui.main

import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteDecision
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteRequest
import com.aqua.aqualight.application.notifications.DeviceFirmwareNotificationKind
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
            gate.evaluate(availabilityRequest(), OWNER_A)
        )
        session = authenticated(OWNER_B)

        assertEquals(
            DeviceFirmwareNotificationRouteDecision.REJECT,
            gate.evaluate(availabilityRequest(), OWNER_A)
        )
    }

    @Test
    fun deviceDeletionBetweenChecksRejectsStaleIntent() {
        val operations = FakeRouteOperations()
        val gate = gate({ authenticated(OWNER_A) }, operations)

        assertEquals(
            DeviceFirmwareNotificationRouteDecision.OPEN,
            gate.evaluate(availabilityRequest(), OWNER_A)
        )
        operations.decision = DeviceFirmwareNotificationRouteDecision.REJECT

        assertEquals(
            DeviceFirmwareNotificationRouteDecision.REJECT,
            gate.evaluate(availabilityRequest(), OWNER_A)
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
            gate.evaluate(availabilityRequest(), OWNER_A)
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
            gate.evaluate(availabilityRequest(), OWNER_A)
        )
        assertTrue(operations.evaluatedRequests.isEmpty())
    }

    @Test
    fun successfulAvailabilityNavigationAcknowledgesExactAvailability() = runTest {
        val operations = FakeRouteOperations()
        val gate = gate({ authenticated(OWNER_A) }, operations)

        gate.acknowledgeOpened(
            availabilityRequest(deviceUid = " $DEVICE_UID "),
            " $OWNER_A "
        )

        assertEquals(listOf("$OWNER_A:$DEVICE_UID"), operations.dismissedAvailability)
    }

    @Test
    fun operationNavigationDoesNotDismissAvailabilityLedger() = runTest {
        val operations = FakeRouteOperations()
        val gate = gate({ authenticated(OWNER_A) }, operations)

        gate.acknowledgeOpened(
            DeviceFirmwareNotificationRouteRequest(
                deviceUid = DEVICE_UID,
                kind = DeviceFirmwareNotificationKind.OPERATION
            ),
            OWNER_A
        )

        assertTrue(operations.dismissedAvailability.isEmpty())
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

    private fun availabilityRequest(
        deviceUid: String = DEVICE_UID
    ) = DeviceFirmwareNotificationRouteRequest(
        deviceUid = deviceUid,
        kind = DeviceFirmwareNotificationKind.AVAILABILITY,
        targetVersion = TARGET_VERSION
    )

    private class FakeRouteOperations(
        var decision: DeviceFirmwareNotificationRouteDecision =
            DeviceFirmwareNotificationRouteDecision.OPEN
    ) : DeviceFirmwareNotificationRouteOperations {
        val evaluatedRequests = mutableListOf<DeviceFirmwareNotificationRouteRequest>()
        val dismissedAvailability = mutableListOf<String>()

        override fun evaluate(
            request: DeviceFirmwareNotificationRouteRequest
        ): DeviceFirmwareNotificationRouteDecision {
            evaluatedRequests += request
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
        const val TARGET_VERSION = "1.1.0"
    }
}
