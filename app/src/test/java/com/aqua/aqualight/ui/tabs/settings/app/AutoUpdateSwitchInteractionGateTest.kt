package com.aqua.aqualight.ui.tabs.settings.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateSwitchInteractionGateTest {

    @Test
    fun programmaticRestoreDoesNotEmitUserChange() {
        val changes = mutableListOf<Boolean>()
        val gate = AutoUpdateSwitchInteractionGate()

        gate.restoreState(true) { restoredEnabled ->
            gate.onCheckedChanged(restoredEnabled, changes::add)
        }

        assertTrue(changes.isEmpty())
    }

    @Test
    fun genuineUserToggleEmitsExactlyOnce() {
        val changes = mutableListOf<Boolean>()
        val gate = AutoUpdateSwitchInteractionGate()

        gate.onCheckedChanged(true, changes::add)

        assertEquals(listOf(true), changes)
    }

    @Test
    fun restoreGuardIsReleasedAfterFailure() {
        val changes = mutableListOf<Boolean>()
        val gate = AutoUpdateSwitchInteractionGate()

        val result = runCatching {
            gate.restoreState(true) {
                error("restore failed")
            }
        }
        gate.onCheckedChanged(false, changes::add)

        assertTrue(result.isFailure)
        assertEquals(listOf(false), changes)
    }
}
