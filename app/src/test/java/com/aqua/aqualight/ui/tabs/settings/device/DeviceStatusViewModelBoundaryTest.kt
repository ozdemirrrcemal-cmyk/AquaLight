package com.aqua.aqualight.ui.tabs.settings.device

import com.aqua.aqualight.application.devices.DeviceStatusOperations
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.OwnerDeviceStatusSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceStatusViewModelBoundaryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `status cards are rendered from application DTOs`() {
        val operations = FakeDeviceStatusOperations(
            initialStatuses = listOf(status(lastSeenAtMillis = 45_000L))
        )
        val clock = FakeDeviceStatusClock(nowMillis = 60_000L)

        val viewModel = DeviceStatusViewModel(
            operations = operations,
            clock = clock
        )
        val item = viewModel.uiState.value.devices.single()

        assertEquals(1, operations.startCalls)
        assertFalse(viewModel.uiState.value.isEmpty)
        assertEquals("AquaLight One", item.displayName)
        assertEquals("192.168.1.20", item.ip)
        assertEquals("AQL-0001", item.serialText)
        assertEquals("15s ago", item.lastSeenText)
        assertEquals(true, item.isOnline)
    }

    @Test
    fun `relative last seen text refreshes only from injected clock`() {
        val operations = FakeDeviceStatusOperations(
            initialStatuses = listOf(status(lastSeenAtMillis = 1_000L))
        )
        val clock = FakeDeviceStatusClock(nowMillis = 10_000L)
        val viewModel = DeviceStatusViewModel(operations, clock)

        assertEquals("Just now", viewModel.uiState.value.devices.single().lastSeenText)

        clock.emit(62_000L)

        assertEquals("1m ago", viewModel.uiState.value.devices.single().lastSeenText)
    }

    private fun status(lastSeenAtMillis: Long) = OwnerDeviceStatusSnapshot(
        deviceUid = "device-1",
        displayName = "AquaLight One",
        serialText = "AQL-0001",
        family = OwnerDeviceFamily.LIGHT,
        availability = OwnerDeviceAvailability.REACHABLE,
        ipAddress = "192.168.1.20",
        lastSeenAtMillis = lastSeenAtMillis
    )

    private class FakeDeviceStatusOperations(
        initialStatuses: List<OwnerDeviceStatusSnapshot>
    ) : DeviceStatusOperations {
        override val statuses = MutableStateFlow(initialStatuses)
        var startCalls = 0

        override fun start(scope: CoroutineScope): Job {
            startCalls += 1
            return Job().apply { complete() }
        }
    }

    private class FakeDeviceStatusClock(
        nowMillis: Long
    ) : DeviceStatusClock {
        private val mutableTicks = MutableStateFlow(nowMillis)
        override val ticks: Flow<Long> = mutableTicks

        fun emit(nowMillis: Long) {
            mutableTicks.value = nowMillis
        }
    }

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
}
