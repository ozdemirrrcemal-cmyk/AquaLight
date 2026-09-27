package com.aqua.aqualight.data.auth

/** Isolated committed session using the production transition/write barrier. */
internal class OwnerSessionTestFixture(ownerUid: String) {
    private val state = OwnerSessionStateMachine()
    val barrier = OwnerSessionMutationBarrier(state)
    val lease = activate(ownerUid)

    suspend fun reopen(ownerUid: String): OwnerSessionWriteLease = barrier.withTransition {
        state.close()
        activate(ownerUid)
    }

    suspend fun close() = barrier.withTransition { state.close() }

    private fun activate(ownerUid: String): OwnerSessionWriteLease {
        val transition = state.begin(ownerUid)
        check(state.commit(transition))
        return barrier.bind(ownerUid, transition.generation)
    }
}
