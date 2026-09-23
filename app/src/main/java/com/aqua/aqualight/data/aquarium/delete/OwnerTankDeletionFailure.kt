package com.aqua.aqualight.data.aquarium.delete

import java.util.concurrent.CancellationException

internal fun requireTankDeletionOwnerUid(
    value: String
): String {
    val owner = value.trim()
    require(owner.isNotBlank()) {
        "Tank deletion requires a non-blank owner uid."
    }
    return owner
}

internal fun tankDeletionCleanupIssue(
    tankId: Long,
    stage: OwnerTankDataCleaner.CleanupStage,
    error: Throwable
) = OwnerTankDataCleaner.CleanupIssue(
    tankId = tankId,
    stage = stage,
    error = error
)

internal fun Throwable?.combineTankDeletion(
    other: Throwable?
): Throwable? {
    if (other == null) {
        return this
    }
    val current = this
    return if (current == null) {
        other
    } else {
        current.addSuppressed(other)
        current
    }
}

internal fun Throwable.throwIfTankDeletionCancellation() {
    if (this is CancellationException) {
        throw this
    }
}
