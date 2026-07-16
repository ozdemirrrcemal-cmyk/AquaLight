package com.aqua.aqualight.ui.tabs.devices.add

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.provisioning.ProvisioningCandidateSnapshot
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDiscoveryOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningQrPayload
import com.aqua.aqualight.application.devices.provisioning.ProvisioningScanStartResult
import com.aqua.aqualight.application.text.AppTextResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ProvisioningDiscoveryViewModelBoundaryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `nearby scan renders application candidates through one discovery boundary`() {
        val operations = FakeProvisioningDiscoveryOperations()
        val viewModel = DeviceAddViewModel(
            discoveryOperations = operations,
            textResolver = FakeTextResolver
        )

        viewModel.startBleScan()
        operations.candidateState.value = listOf(candidate())

        val rendered = viewModel.uiState.value.candidates.single()
        assertEquals(1, operations.startCalls)
        assertEquals("device-1", rendered.id)
        assertEquals("AquaLight One", rendered.title)
        assertEquals("AQL-0001", rendered.serial)
        assertEquals("AA:BB:CC:DD:EE:FF", rendered.bleAddress)

        viewModel.onCandidateClicked(rendered)
    }

    @Test
    fun `verified QR opens WiFi with application payload and candidate`() = runTest {
        val operations = FakeProvisioningDiscoveryOperations().apply {
            parsedPayload = qrPayload()
            awaitedCandidate = candidate()
        }
        val viewModel = DeviceQrScanViewModel(
            discoveryOperations = operations,
            textResolver = FakeTextResolver
        )

        viewModel.onQrDetected(rawValue = "raw-qr", hasBlePermissions = true)
        val event = viewModel.events.first() as DeviceQrScanEvent.OpenWifiProvisioning

        assertEquals(1, operations.parseCalls)
        assertEquals(1, operations.startCalls)
        assertEquals(1, operations.awaitCalls)
        assertEquals("device-1", event.result.deviceUid)
        assertEquals("AA:BB:CC:DD:EE:FF", event.result.bleAddress)
        assertEquals("claim-1", event.result.claimCode)
        assertEquals("raw-qr", event.result.rawQrPayload)
    }

    @Test
    fun `registered QR without setup candidate remains blocked`() {
        val operations = FakeProvisioningDiscoveryOperations().apply {
            parsedPayload = qrPayload()
            awaitedCandidate = null
            registered = true
            nearbyCandidates = false
        }
        val viewModel = DeviceQrScanViewModel(
            discoveryOperations = operations,
            textResolver = FakeTextResolver
        )

        viewModel.onQrDetected(rawValue = "raw-qr", hasBlePermissions = true)

        assertEquals(
            text(R.string.device_qr_preflight_already_added_title),
            viewModel.uiState.value.title
        )
        assertEquals(DeviceQrScanPrimaryAction.SCAN_AGAIN, viewModel.uiState.value.primaryAction)
        assertTrue(operations.registrationChecks.contains("device-1"))
    }

    private class FakeProvisioningDiscoveryOperations : ProvisioningDiscoveryOperations {
        val candidateState = MutableStateFlow<List<ProvisioningCandidateSnapshot>>(emptyList())
        override val candidates: Flow<List<ProvisioningCandidateSnapshot>> = candidateState

        var startResult: ProvisioningScanStartResult = ProvisioningScanStartResult.Started
        var parsedPayload: ProvisioningQrPayload? = null
        var awaitedCandidate: ProvisioningCandidateSnapshot? = null
        var nearbyCandidates: Boolean = false
        var registered: Boolean = false
        var startCalls: Int = 0
        var parseCalls: Int = 0
        var awaitCalls: Int = 0
        val registrationChecks = mutableListOf<String>()

        override fun startScan(): ProvisioningScanStartResult {
            startCalls += 1
            return startResult
        }

        override fun stopScan() = Unit

        override fun clearCandidates() {
            candidateState.value = emptyList()
        }

        override fun parseQr(rawValue: String): Result<ProvisioningQrPayload> {
            parseCalls += 1
            val payload = parsedPayload
            return if (payload == null) {
                Result.failure(IllegalArgumentException("invalid QR"))
            } else {
                Result.success(payload)
            }
        }

        override suspend fun awaitQrCandidate(
            payload: ProvisioningQrPayload,
            timeoutMillis: Long
        ): ProvisioningCandidateSnapshot? {
            awaitCalls += 1
            return awaitedCandidate
        }

        override fun hasCandidates(): Boolean = nearbyCandidates

        override fun isRegistered(deviceUid: String): Boolean {
            registrationChecks += deviceUid
            return registered
        }
    }

    private object FakeTextResolver : AppTextResolver {
        override fun get(resId: Int, vararg args: Any): String = text(resId, *args)
    }

    private fun candidate() = ProvisioningCandidateSnapshot(
        address = "AA:BB:CC:DD:EE:FF",
        bleName = "AQL-SETUP-0001",
        rssi = -41,
        deviceUid = "device-1",
        displayTitle = "AquaLight One",
        model = "AQL-Light",
        displaySerial = "AQL-0001",
        displayStatus = "Ready",
        rawAdvertisementPayload = "advertisement"
    )

    private fun qrPayload() = ProvisioningQrPayload(
        deviceUid = "device-1",
        serialNumber = "AQL-0001",
        productId = "product-1",
        model = "AQL-Light",
        displayName = "AquaLight One",
        hardwareRevision = "rev-a",
        skuCode = "sku-1",
        provisioningId = "provisioning-1",
        claimCode = "claim-1",
        bleName = "AQL-SETUP-0001",
        rawPayload = "raw-qr"
    )

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
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
