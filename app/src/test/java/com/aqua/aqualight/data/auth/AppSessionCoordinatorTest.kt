package com.aqua.aqualight.data.auth

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSessionCoordinatorTest {

    @Test
    fun startupOpensExactlyOneAuthenticatedOwner() = runBlocking {
        val provider = FakeOwnerProvider("owner-a")
        val resolveCount = AtomicInteger(0)
        val coordinator = coordinator(provider) {
            resolveCount.incrementAndGet()
            provider.currentOwnerUid().toResolution()
        }

        try {
            coordinator.start()
            val state = coordinator.awaitState {
                it is AppSessionCoordinator.State.Authenticated
            }

            assertEquals(
                AppSessionCoordinator.State.Authenticated("owner-a"),
                state
            )
            assertEquals(1, resolveCount.get())
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun repeatedStartDoesNotCreateDuplicateStartupCollectors() = runBlocking {
        val provider = FakeOwnerProvider("owner-a")
        val resolveCount = AtomicInteger(0)
        val coordinator = coordinator(provider) {
            resolveCount.incrementAndGet()
            provider.currentOwnerUid().toResolution()
        }

        try {
            coordinator.start()
            coordinator.start()
            coordinator.start()

            coordinator.awaitState {
                it == AppSessionCoordinator.State.Authenticated("owner-a")
            }

            assertEquals(1, resolveCount.get())
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun rapidAccountSwitchSettlesOnNewestOwner() = runBlocking {
        val provider = FakeOwnerProvider("owner-a")
        val coordinator = coordinator(provider) {
            provider.currentOwnerUid().toResolution()
        }

        try {
            coordinator.start()
            coordinator.awaitState {
                it == AppSessionCoordinator.State.Authenticated("owner-a")
            }

            provider.setOwner(null)
            provider.setOwner("owner-b")

            val finalState = coordinator.awaitState {
                it == AppSessionCoordinator.State.Authenticated("owner-b")
            }

            assertEquals(
                AppSessionCoordinator.State.Authenticated("owner-b"),
                finalState
            )
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun logoutTransitionsExistingGraphAuthorityToUnauthenticated() = runBlocking {
        val provider = FakeOwnerProvider("owner-a")
        val coordinator = coordinator(provider) {
            provider.currentOwnerUid().toResolution()
        }

        try {
            coordinator.start()
            coordinator.awaitState {
                it is AppSessionCoordinator.State.Authenticated
            }

            provider.setOwner(null)

            assertEquals(
                AppSessionCoordinator.State.Unauthenticated,
                coordinator.awaitState {
                    it == AppSessionCoordinator.State.Unauthenticated
                }
            )
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun remoteInvalidationReturnsSessionToUnauthenticated() = runBlocking {
        val provider = FakeOwnerProvider("owner-a")
        val coordinator = coordinator(provider) {
            provider.currentOwnerUid().toResolution()
        }

        try {
            coordinator.start()
            coordinator.awaitState {
                it is AppSessionCoordinator.State.Authenticated
            }

            provider.validationAction = {
                val previousOwner = requireNotNull(provider.currentOwnerUid())
                provider.setOwner(null)
                OwnerTokenValidationResult.Revoked(
                    ownerUid = previousOwner,
                    error = IllegalStateException("revoked")
                )
            }

            coordinator.enterForeground()

            assertEquals(
                AppSessionCoordinator.State.Unauthenticated,
                coordinator.awaitState {
                    it == AppSessionCoordinator.State.Unauthenticated
                }
            )
        } finally {
            coordinator.leaveForeground()
            coordinator.close()
        }
    }

    @Test
    fun foregroundConsumersDriveRuntimeOnlyOnBoundaryTransitions() = runBlocking {
        val provider = FakeOwnerProvider("owner-a")
        val runtimeController = RecordingForegroundRuntimeController()
        val coordinator = coordinator(
            provider = provider,
            foregroundRuntimeController = runtimeController
        ) {
            provider.currentOwnerUid().toResolution()
        }

        try {
            coordinator.enterForeground()
            coordinator.enterForeground()
            assertEquals(listOf(true), runtimeController.transitions)

            coordinator.leaveForeground()
            assertEquals(listOf(true), runtimeController.transitions)

            coordinator.leaveForeground()
            assertEquals(listOf(true, false), runtimeController.transitions)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun processRecreationResolvesCurrentOwnerWithoutOldCoordinatorState() = runBlocking {
        val provider = FakeOwnerProvider("owner-a")
        val first = coordinator(provider) {
            provider.currentOwnerUid().toResolution()
        }

        first.start()
        first.awaitState {
            it == AppSessionCoordinator.State.Authenticated("owner-a")
        }
        first.close()

        provider.setOwner("owner-b")
        val recreated = coordinator(provider) {
            provider.currentOwnerUid().toResolution()
        }

        try {
            recreated.start()
            assertEquals(
                AppSessionCoordinator.State.Authenticated("owner-b"),
                recreated.awaitState {
                    it == AppSessionCoordinator.State.Authenticated("owner-b")
                }
            )
        } finally {
            recreated.close()
        }
    }

    @Test
    fun resolverFailureIsExposedWithoutPublishingWrongOwner() = runBlocking {
        val provider = FakeOwnerProvider("owner-a")
        val expected = IllegalStateException("startup failed")
        val coordinator = coordinator(provider) {
            throw expected
        }

        try {
            coordinator.start()
            val state = coordinator.awaitState {
                it is AppSessionCoordinator.State.Failure
            }

            assertTrue(state is AppSessionCoordinator.State.Failure)
            state as AppSessionCoordinator.State.Failure
            assertEquals("owner-a", state.ownerUid)
            assertTrue(state.error === expected)
        } finally {
            coordinator.close()
        }
    }

    private fun coordinator(
        provider: FakeOwnerProvider,
        foregroundRuntimeController: ForegroundRuntimeController =
            ForegroundRuntimeController { },
        resolve: suspend () -> ForegroundSessionResolution
    ): AppSessionCoordinator {
        return AppSessionCoordinator(
            ownerProvider = provider,
            sessionResolver = ForegroundSessionResolver {
                resolve()
            },
            dispatcher = Dispatchers.Unconfined,
            foregroundRuntimeController = foregroundRuntimeController
        )
    }

    private suspend fun AppSessionCoordinator.awaitState(
        predicate: (AppSessionCoordinator.State) -> Boolean
    ): AppSessionCoordinator.State {
        return withTimeout(5_000L) {
            state.first(predicate)
        }
    }

    private fun String?.toResolution(): ForegroundSessionResolution {
        return if (this == null) {
            ForegroundSessionResolution.Unauthenticated
        } else {
            ForegroundSessionResolution.Authenticated(this)
        }
    }

    private class RecordingForegroundRuntimeController : ForegroundRuntimeController {
        val transitions = CopyOnWriteArrayList<Boolean>()

        override fun setForeground(isForeground: Boolean) {
            transitions += isForeground
        }
    }

    private class FakeOwnerProvider(
        initialOwnerUid: String?
    ) : AuthenticatedOwnerProvider {

        private val _state = MutableStateFlow(
            initialOwnerUid.toState()
        )
        override val state: StateFlow<AuthenticatedOwnerState> = _state.asStateFlow()

        var validationAction: suspend () -> OwnerTokenValidationResult = {
            currentOwnerUid()?.let(OwnerTokenValidationResult::Valid)
                ?: OwnerTokenValidationResult.Unauthenticated
        }

        override fun currentOwner(): AuthenticatedOwner? {
            return (_state.value as? AuthenticatedOwnerState.Authenticated)?.owner
        }

        override suspend fun validateCurrentOwner(): OwnerTokenValidationResult {
            return validationAction()
        }

        fun setOwner(ownerUid: String?) {
            _state.value = ownerUid.toState()
        }

        private fun String?.toState(): AuthenticatedOwnerState {
            return if (this == null) {
                AuthenticatedOwnerState.Unauthenticated
            } else {
                AuthenticatedOwnerState.Authenticated(
                    AuthenticatedOwner(
                        uid = this,
                        email = "$this@example.com",
                        displayName = this,
                        photoUrl = "",
                        isEmailVerified = true
                    )
                )
            }
        }
    }
}
