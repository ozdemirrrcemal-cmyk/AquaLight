package com.aqua.aqualight.ui.tabs.devices.add

import com.aqua.aqualight.R
import com.aqua.aqualight.application.text.AppTextResolver
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattEvent
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProvisioningProgressViewModelBoundaryTest {

    @Test
    fun `missing draft renders expired state without opening device runtime`() {
        val operations = FakeProvisioningOperations(draft = null)
        val viewModel = DeviceProvisioningProgressViewModel(
            operations = operations,
            textResolver = FakeTextResolver
        )

        viewModel.bind("missing-session")

        assertEquals(1, operations.getDraftCalls)
        assertEquals(
            text(R.string.device_provisioning_session_expired_title),
            viewModel.uiState.value.title
        )
        assertFalse(viewModel.uiState.value.canStart)
        assertEquals(0, operations.startGattCalls)
        assertEquals(0, operations.prepareCalls)
    }

    @Test
    fun `existing draft renders ready state through one fake boundary`() {
        val draft = draft()
        val operations = FakeProvisioningOperations(draft)
        val viewModel = DeviceProvisioningProgressViewModel(
            operations = operations,
            textResolver = FakeTextResolver
        )

        viewModel.bind(draft.sessionId)

        val state = viewModel.uiState.value
        assertEquals(1, operations.getDraftCalls)
        assertEquals(draft.deviceTitle, state.deviceName)
        assertEquals(draft.deviceSerial, state.deviceSerial)
        assertEquals(draft.bleAddress, state.bleAddress)
        assertEquals(draft.wifiCredentials.ssid, state.wifiSsid)
        assertTrue(state.canStart)
        assertFalse(state.showProgress)
        assertEquals(0, operations.startGattCalls)
        assertEquals(0, operations.prepareCalls)
    }

    @Test
    fun `binding the same session twice remains idempotent`() {
        val draft = draft()
        val operations = FakeProvisioningOperations(draft)
        val viewModel = DeviceProvisioningProgressViewModel(
            operations = operations,
            textResolver = FakeTextResolver
        )

        viewModel.bind(draft.sessionId)
        viewModel.bind(draft.sessionId)

        assertEquals(1, operations.getDraftCalls)
        assertEquals(0, operations.startGattCalls)
    }

    private fun draft(): AqlProvisioningDraft = AqlProvisioningDraft(
        sessionId = "session-1",
        candidateId = "candidate-1",
        bleAddress = "AA:BB:CC:DD:EE:FF",
        bleName = "AquaLight-Setup",
        claimCode = "claim",
        rawQrPayload = "raw",
        deviceTitle = "AquaLight Test",
        deviceSerial = "AQL-TEST-001",
        deviceModel = "AQL-Light",
        wifiCredentials = AqlWifiCredentials(
            ssid = "Test WiFi",
            password = "password"
        ),
        createdAtMillis = 1L
    )

    private class FakeProvisioningOperations(
        private val draft: AqlProvisioningDraft?
    ) : DeviceProvisioningProgressOperations {
        override val ownerUid: String = "owner-a"
        override val gattEvents: Flow<AqlBleProvisioningGattEvent> = emptyFlow()

        var getDraftCalls = 0
        var startGattCalls = 0
        var prepareCalls = 0

        override fun getDraft(sessionId: String): AqlProvisioningDraft? {
            getDraftCalls += 1
            return draft?.takeIf { it.sessionId == sessionId }
        }

        override fun removeDraft(sessionId: String) = Unit

        override suspend fun resolveQrAddress(
            draft: AqlProvisioningDraft
        ): Result<String> = Result.success(draft.bleAddress)

        override fun startGatt(draft: AqlProvisioningDraft) {
            startGattCalls += 1
        }

        override fun finalizeSetup(handoff: AqlProvisioningRuntimeHandoff) = Unit

        override fun closeGatt() = Unit

        override suspend fun prepareAndConnect(
            draft: AqlProvisioningDraft,
            handoff: AqlProvisioningRuntimeHandoff
        ): Result<DeviceSnapshot> {
            prepareCalls += 1
            return Result.failure(IllegalStateException("Not expected in this test."))
        }

        override suspend fun commitPreparedRegistration(
            snapshot: DeviceSnapshot
        ): Result<DeviceSnapshot> =
            Result.failure(IllegalStateException("Not expected in this test."))

        override suspend fun rollbackProvisioningRegistration(
            deviceUid: DeviceUid
        ): Result<Unit> = Result.success(Unit)

        override suspend fun rollbackProvisioningRegistrationForOwner(
            ownerUid: String,
            deviceUid: DeviceUid
        ): Result<Unit> = Result.success(Unit)

        override fun resolveRoute(
            snapshot: DeviceSnapshot,
            requestedDeviceUid: String
        ): DeviceRoute = error("Not expected in this test.")
    }

    private object FakeTextResolver : AppTextResolver {
        override fun get(resId: Int, vararg args: Any): String =
            text(resId, *args)
    }

    private companion object {
        fun text(resId: Int, vararg args: Any): String = buildString {
            append("text:")
            append(resId)
            if (args.isNotEmpty()) {
                append(":")
                append(args.joinToString(separator = "|"))
            }
        }
    }
}
