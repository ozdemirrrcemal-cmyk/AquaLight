package com.aqua.aqualight.data.user

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudUserDataCleanerTest {

    @Test
    fun `storage owner tree is deleted before indexed documents`() = runTest {
        val calls = mutableListOf<String>()
        val cleaner = CloudUserDataCleaner(
            feedbackObjectCleaner = OwnerCloudDataCleaner { calls += "objects" },
            feedbackDocumentCleaner = OwnerCloudDataCleaner { calls += "documents" }
        )

        val result = cleaner.clearCloudUserData(" user-123 ")

        assertFalse(result.hasError)
        assertEquals(listOf("objects", "documents"), calls)
    }

    @Test
    fun `orphan object cleanup failure prevents account data index deletion`() = runTest {
        val expected = IllegalStateException("storage unavailable")
        var documentCleanerCalled = false
        val cleaner = CloudUserDataCleaner(
            feedbackObjectCleaner = OwnerCloudDataCleaner { throw expected },
            feedbackDocumentCleaner = OwnerCloudDataCleaner { documentCleanerCalled = true }
        )

        val result = cleaner.clearCloudUserData("user-123")

        assertTrue(result.hasError)
        assertSame(expected, result.error)
        assertFalse(documentCleanerCalled)
    }

    @Test
    fun `document deletion failure is returned after storage cleanup`() = runTest {
        val expected = IllegalStateException("firestore unavailable")
        var objectCleanerCalled = false
        val cleaner = CloudUserDataCleaner(
            feedbackObjectCleaner = OwnerCloudDataCleaner { objectCleanerCalled = true },
            feedbackDocumentCleaner = OwnerCloudDataCleaner { throw expected }
        )

        val result = cleaner.clearCloudUserData("user-123")

        assertTrue(objectCleanerCalled)
        assertSame(expected, result.error)
    }

    @Test
    fun `invalid uid cannot address another storage path`() = runTest {
        var calls = 0
        val cleaner = CloudUserDataCleaner(
            feedbackObjectCleaner = OwnerCloudDataCleaner { calls++ },
            feedbackDocumentCleaner = OwnerCloudDataCleaner { calls++ }
        )

        val result = cleaner.clearCloudUserData("../other-user")

        assertTrue(result.hasError)
        assertEquals(0, calls)
    }

    @Test
    fun `cleanup remains safe to retry`() = runTest {
        var objectCalls = 0
        var documentCalls = 0
        val cleaner = CloudUserDataCleaner(
            feedbackObjectCleaner = OwnerCloudDataCleaner { objectCalls++ },
            feedbackDocumentCleaner = OwnerCloudDataCleaner { documentCalls++ }
        )

        assertFalse(cleaner.clearCloudUserData("user-123").hasError)
        assertFalse(cleaner.clearCloudUserData("user-123").hasError)

        assertEquals(2, objectCalls)
        assertEquals(2, documentCalls)
    }
}
