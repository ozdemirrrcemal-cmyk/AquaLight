package com.aqua.aqualight.data.auth

/**
 * Generation-controlled process session state.
 *
 * Every asynchronous owner transition receives a lease. Work may commit only
 * while that lease is still current, preventing delayed A -> B work from
 * overwriting a newer B -> A transition.
 */
class OwnerSessionStateMachine {

    data class Transition(
        val generation: Long,
        val previousOwnerUid: String?,
        val targetOwnerUid: String?
    )

    data class Snapshot(
        val generation: Long,
        val activeOwnerUid: String?,
        val pendingOwnerUid: String?
    )

    private val lock = Any()

    private var generation: Long = 0L
    private var activeOwnerUid: String? = null
    private var pendingOwnerUid: String? = null

    fun snapshot(): Snapshot {
        return synchronized(lock) {
            Snapshot(
                generation = generation,
                activeOwnerUid = activeOwnerUid,
                pendingOwnerUid = pendingOwnerUid
            )
        }
    }

    fun begin(
        targetOwnerUid: String?
    ): Transition {
        val normalizedTarget = targetOwnerUid.normalizedOwnerUidOrNull()

        return synchronized(lock) {
            val previousOwnerUid = activeOwnerUid
            generation = nextGeneration(generation)
            activeOwnerUid = null
            pendingOwnerUid = normalizedTarget

            Transition(
                generation = generation,
                previousOwnerUid = previousOwnerUid,
                targetOwnerUid = normalizedTarget
            )
        }
    }

    fun isCurrent(
        transition: Transition
    ): Boolean {
        return synchronized(lock) {
            transition.generation == generation &&
                transition.targetOwnerUid == pendingOwnerUid
        }
    }

    fun commit(
        transition: Transition
    ): Boolean {
        return synchronized(lock) {
            if (
                transition.generation != generation ||
                transition.targetOwnerUid != pendingOwnerUid
            ) {
                false
            } else {
                activeOwnerUid = transition.targetOwnerUid
                pendingOwnerUid = null
                true
            }
        }
    }

    fun abort(
        transition: Transition
    ): Boolean {
        return synchronized(lock) {
            if (
                transition.generation != generation ||
                transition.targetOwnerUid != pendingOwnerUid
            ) {
                false
            } else {
                pendingOwnerUid = null
                activeOwnerUid = null
                true
            }
        }
    }

    fun close(
        expectedOwnerUid: String? = null
    ): Transition? {
        val normalizedExpected = expectedOwnerUid.normalizedOwnerUidOrNull()

        return synchronized(lock) {
            val currentOwnerUid = pendingOwnerUid ?: activeOwnerUid

            if (
                normalizedExpected != null &&
                currentOwnerUid != normalizedExpected
            ) {
                return@synchronized null
            }

            generation = nextGeneration(generation)
            val transition = Transition(
                generation = generation,
                previousOwnerUid = currentOwnerUid,
                targetOwnerUid = null
            )
            activeOwnerUid = null
            pendingOwnerUid = null
            transition
        }
    }

    private fun String?.normalizedOwnerUidOrNull(): String? {
        return this
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    private fun nextGeneration(
        current: Long
    ): Long {
        return if (current == Long.MAX_VALUE) {
            1L
        } else {
            current + 1L
        }
    }
}
