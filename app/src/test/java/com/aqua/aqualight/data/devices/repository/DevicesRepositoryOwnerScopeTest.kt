package com.aqua.aqualight.data.devices.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class DevicesRepositoryOwnerScopeTest {

    @Test
    fun `owner scoped resources close exactly once when repository retires`() {
        val repository = DevicesRepository()
        var closeCalls = 0
        repository.registerOwnerScopedResource(AutoCloseable { closeCalls += 1 })

        repository.stop()
        repository.stop()

        assertEquals(1, closeCalls)
    }
}
