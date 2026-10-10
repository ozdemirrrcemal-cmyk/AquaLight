package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.TankDeviceListItem
import com.aqua.aqualight.application.devices.groups.LightGroupCompatibility
import com.aqua.aqualight.application.devices.groups.TankControlGroupDevice
import com.aqua.aqualight.application.devices.groups.TankControlGroupDeviceOperations
import com.aqua.aqualight.application.devices.groups.TankControlGroupDevices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TankControlGroupCreateViewModelTest {
    private val key = LightGroupCompatibility("elite", "elite-id", "elite_120", "r1", 4)
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `stale drops reject removed devices and different models`() {
        val operations = FakeOperations(listOf(light("a"), light("b")))
        val vm = TankControlGroupCreateViewModel(operations)
        vm.bind(10L)
        assertTrue(vm.move("a", true))
        operations.emit(listOf(light("a"), light("b", key.copy(model = "slim_120"))))
        assertFalse(vm.move("b", true))
        operations.emit(listOf(light("a")))
        assertFalse(vm.move("b", true))
        assertEquals(listOf("a"), vm.uiState.value.selected.map { it.deviceUid })
    }

    @Test fun `loading preserves draft and ready data reconciles actual assignment removal`() {
        val operations = FakeOperations(listOf(light("a"), light("b")))
        val vm = TankControlGroupCreateViewModel(operations)
        vm.bind(10L)
        vm.move("a", true)
        vm.move("b", true)
        assertTrue(vm.uiState.value.isReady)
        operations.source.value = TankControlGroupDevices()
        assertFalse(vm.canMove("a", false))
        operations.emit(listOf(light("a"), light("b", null)))
        assertEquals(2, vm.uiState.value.selected.size)
        assertFalse(vm.uiState.value.isReady)
        operations.emit(listOf(light("a"), light("b")))
        assertTrue(vm.uiState.value.isReady)
        operations.emit(listOf(light("b")))
        assertEquals(listOf("b"), vm.uiState.value.selected.map { it.deviceUid })
        assertFalse(vm.uiState.value.isReady)
    }

    @Test fun `last removal resets filter and rebinding same tank preserves rotation draft`() {
        val operations = FakeOperations(listOf(light("a"), light("b"), light("c", key.copy(model = "slim"))))
        val vm = TankControlGroupCreateViewModel(operations)
        vm.bind(10L)
        vm.move("a", true)
        vm.bind(10L)
        assertEquals(1, vm.uiState.value.selected.size)
        assertEquals(1, operations.observedTanks.size)
        assertEquals(1, vm.uiState.value.hiddenCount)
        assertTrue(vm.move("a", false))
        assertEquals(3, vm.uiState.value.available.size)
        assertEquals(0, vm.uiState.value.hiddenCount)
    }

    @Test fun `changing tank cancels prior observation and resets selection`() {
        val operations = FakeOperations(listOf(light("a")))
        val vm = TankControlGroupCreateViewModel(operations)
        vm.bind(10L)
        vm.move("a", true)
        vm.bind(20L)
        assertTrue(vm.uiState.value.selected.isEmpty())
        assertEquals(listOf(10L, 20L), operations.observedTanks)
        assertEquals(1, operations.source.subscriptionCount.value)
    }

    @Test fun `missing tank and observation errors disable selection`() {
        val operations = FakeOperations(listOf(light("a")))
        val vm = TankControlGroupCreateViewModel(operations)
        vm.bind(10L)
        operations.source.value = operations.source.value.copy(tankExists = false)
        assertFalse(vm.move("a", true))
        operations.fail = true
        vm.bind(20L)
        assertTrue(vm.uiState.value.loadFailed)
        assertFalse(vm.uiState.value.isReady)
        assertFalse(vm.canMove("a", true))
    }

    private fun light(uid: String, identity: LightGroupCompatibility? = key) = TankControlGroupDevice(
        TankDeviceListItem(uid, uid, uid, OwnerDeviceFamily.LIGHT, OwnerDeviceAvailability.UNREACHABLE),
        "Elite 120", identity
    )

    private class FakeOperations(devices: List<TankControlGroupDevice>) : TankControlGroupDeviceOperations {
        val source = MutableStateFlow(TankControlGroupDevices("Tank", devices, isLoading = false))
        val observedTanks = mutableListOf<Long>()
        var fail = false
        fun emit(devices: List<TankControlGroupDevice>) {
            source.value = TankControlGroupDevices("Tank", devices, isLoading = false)
        }
        override fun start(scope: CoroutineScope): Job = Job().apply { complete() }
        override fun observe(tankId: Long): Flow<TankControlGroupDevices> {
            observedTanks += tankId
            return if (fail) flow { error("load failed") } else source
        }
    }
}
