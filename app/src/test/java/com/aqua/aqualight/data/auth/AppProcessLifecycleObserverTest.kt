package com.aqua.aqualight.data.auth

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class AppProcessLifecycleObserverTest {

    @Test
    fun processStartAndStopDriveOneForegroundAuthority() {
        val controller = RecordingForegroundLifecycleController()
        val observer = AppProcessLifecycleObserver(controller)
        val owner = TestLifecycleOwner()

        observer.onStart(owner)
        observer.onStop(owner)

        assertEquals(listOf(true, false), controller.transitions)
    }

    private class RecordingForegroundLifecycleController :
        AppForegroundLifecycleController {

        val transitions = mutableListOf<Boolean>()

        override fun enterForeground() {
            transitions += true
        }

        override fun leaveForeground() {
            transitions += false
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }
}
