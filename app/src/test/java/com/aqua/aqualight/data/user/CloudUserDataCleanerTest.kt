package com.aqua.aqualight.data.user

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudUserDataCleanerTest {

    @Test
    fun `owner feedback documents are deleted`() = runTest {
        val calls = mutableListOf<String>()
        val cleaner = CloudUserDataCleaner(
            feedbackDocumentCleaner = OwnerCloudDataCleaner { calls += it }
        )

        val result = cleaner.clearCloudUserData(" user-123 ")

        assertFalse(result.hasError)
        assertEquals(listOf("user-123"), calls)
    }

    @Test
    fun `document deletion failure is returned`() = runTest {
        val expected = IllegalStateException("firestore unavailable")
        val cleaner = CloudUserDataCleaner(
            feedbackDocumentCleaner = OwnerCloudDataCleaner { throw expected }
        )

        val result = cleaner.clearCloudUserData("user-123")

        assertTrue(result.hasError)
        assertSame(expected, result.error)
    }

    @Test
    fun `invalid uid never reaches cloud cleaner`() = runTest {
        var calls = 0
        val cleaner = CloudUserDataCleaner(
            feedbackDocumentCleaner = OwnerCloudDataCleaner { calls++ }
        )

        val result = cleaner.clearCloudUserData("../other-user")

        assertTrue(result.hasError)
        assertEquals(0, calls)
    }

    @Test
    fun `cleanup remains safe to retry`() = runTest {
        var calls = 0
        val cleaner = CloudUserDataCleaner(
            feedbackDocumentCleaner = OwnerCloudDataCleaner { calls++ }
        )

        assertFalse(cleaner.clearCloudUserData("user-123").hasError)
        assertFalse(cleaner.clearCloudUserData("user-123").hasError)
        assertEquals(2, calls)
    }
}
