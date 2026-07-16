package com.aqua.aqualight.data.user

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class UserDataScopeTest {

    @Test
    fun explicitOwnerSurvivesDispatcherSwitches() = runBlocking {
        val observed = UserDataScope.withOwnerUid("owner-a") {
            withContext(Dispatchers.Default) {
                UserDataScope.currentUid()
            }
        }

        assertEquals("owner-a", observed)
    }

    @Test
    fun currentOwnerOperationCapturesAndPropagatesOneOwner() = runBlocking {
        val observed = UserDataScope.withOwnerUid("owner-a") {
            withCurrentOwnerScope { capturedOwnerUid ->
                val ownerAfterDispatcherSwitch = withContext(Dispatchers.Default) {
                    UserDataScope.currentUid()
                }
                capturedOwnerUid to ownerAfterDispatcherSwitch
            }
        }

        assertEquals("owner-a" to "owner-a", observed)
    }

    @Test
    fun concurrentBackgroundOwnersDoNotLeakIntoEachOther() = runBlocking {
        val ownerA = async(Dispatchers.Default) {
            UserDataScope.withOwnerUid("owner-a") {
                delay(25L)
                UserDataScope.currentUid()
            }
        }
        val ownerB = async(Dispatchers.Default) {
            UserDataScope.withOwnerUid("owner-b") {
                delay(10L)
                UserDataScope.currentUid()
            }
        }

        assertEquals("owner-a", ownerA.await())
        assertEquals("owner-b", ownerB.await())
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankExplicitOwnerIsRejected() = runBlocking {
        UserDataScope.withOwnerUid("   ") {
            Unit
        }
    }
}
