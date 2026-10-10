package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import androidx.lifecycle.SavedStateHandle
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

    @Test fun `stale drops cannot add removed devices or mix products`() {
        val operations = FakeOperations(listOf(light("a"), light("b")))
        val vm = TankControlGroupCreateViewModel(operations, SavedStateHandle())
        vm.bind(10L)
        assertTrue(vm.add("a"))
        operations.source.value = TankControlGroupDevices(
            listOf(light("a"), light("b", key.copy(model = "slim_120"))), false
        )
        assertFalse(vm.add("b"))
        operations.source.value = TankControlGroupDevices(listOf(light("a")), false)
        assertFalse(vm.add("b"))
        assertEquals(listOf("a"), vm.uiState.value.selected.map { it.deviceUid })
    }

    @Test fun `saved draft survives loading and is reconciled only after initialization`() {
        val handle = SavedStateHandle()
        val first = TankControlGroupCreateViewModel(FakeOperations(listOf(light("a"), light("b"))), handle)
        first.bind(10L)
        first.add("a")
        first.add("b")
        // SavedStateHandle accepts only primitive lists here, as in framework state restoration.
        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val operations = FakeOperations(emptyList()).apply { source.value = TankControlGroupDevices() }
        val second = TankControlGroupCreateViewModel(operations, restored)
        second.bind(10L)
        assertTrue(second.uiState.value.isLoading)
        assertFalse(second.add("a"))
        operations.source.value = TankControlGroupDevices(listOf(light("a"), light("b", null)), false)
        assertEquals(2, second.uiState.value.selected.size)
        assertFalse(second.uiState.value.isSelectionReady)
        operations.source.value = TankControlGroupDevices(listOf(light("a"), light("b")), false)
        assertTrue(second.uiState.value.isSelectionReady)
        assertFalse(second.uiState.value.selectionAdjusted)
    }

    @Test fun `removal is visible and last removal resets the filtered list`() {
        val operations = FakeOperations(listOf(light("a"), light("b"), light("other", key.copy(model = "other_120"))))
        val vm = TankControlGroupCreateViewModel(operations, SavedStateHandle())
        vm.bind(10L)
        vm.add("a")
        assertEquals(1, vm.uiState.value.hiddenCount)
        assertTrue(vm.remove("a"))
        assertEquals(3, vm.uiState.value.available.size)
        vm.add("a")
        operations.source.value = TankControlGroupDevices(listOf(light("b")), false)
        assertTrue(vm.uiState.value.selectionAdjusted)
        assertTrue(vm.uiState.value.selected.isEmpty())
        assertEquals(1, vm.uiState.value.available.size)
    }

    @Test fun `changing tank resets draft and cancels old observation`() {
        val operations = FakeOperations(listOf(light("a")))
        val vm = TankControlGroupCreateViewModel(operations, SavedStateHandle())
        vm.bind(10L)
        vm.add("a")
        vm.bind(20L)
        assertTrue(vm.uiState.value.selected.isEmpty())
        assertEquals(listOf(10L, 20L), operations.observedTanks)
    }

    @Test fun `load failure disables gestures and retry recovers without losing the draft`() {
        val operations = FakeOperations(listOf(light("a")))
        val vm = TankControlGroupCreateViewModel(operations, SavedStateHandle())
        vm.bind(10L)
        vm.add("a")
        operations.fail = true
        vm.retry()
        assertTrue(vm.uiState.value.loadFailed)
        assertFalse(vm.remove("a"))
        operations.fail = false
        vm.retry()
        assertFalse(vm.uiState.value.loadFailed)
        assertEquals("a", vm.uiState.value.selected.single().deviceUid)
        assertTrue(vm.remove("a"))
    }

    private fun light(uid: String, identity: LightGroupCompatibility? = key) = TankControlGroupDevice(
        TankDeviceListItem(uid, uid, uid, OwnerDeviceFamily.LIGHT, OwnerDeviceAvailability.REACHABLE),
        "Elite 120", identity
    )

    private class FakeOperations(devices: List<TankControlGroupDevice>) : TankControlGroupDeviceOperations {
        val source = MutableStateFlow(TankControlGroupDevices(devices, false))
        val observedTanks = mutableListOf<Long>()
        var fail = false
        override fun start(scope: CoroutineScope): Job = Job().apply { complete() }
        override fun refresh() = Unit
        override fun observe(tankId: Long): Flow<TankControlGroupDevices> {
            observedTanks += tankId
            return if (fail) flow { error("load failed") } else source
        }
    }
}
