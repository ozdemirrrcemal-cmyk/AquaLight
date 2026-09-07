package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common

/** Freshness of an authoritative value already held by presentation. */
enum class CoolingDataFreshness {
    CURRENT,
    REFRESHING,
    STALE
}

/**
 * Shared lifecycle vocabulary for Cooling data-backed presentation sections.
 *
 * [Empty] is a successful authoritative read with no product data. [Unavailable] means the
 * capability may exist but cannot be read now. [Unsupported] means the connected device does not
 * expose the capability. Existing authoritative values stay in [Content] or [Empty] while a
 * refresh is in flight or temporarily fails, so presentation never needs synthetic fallback data.
 */
sealed interface CoolingDataState<out T, out F> {
    data object Initial : CoolingDataState<Nothing, Nothing>
    data object Loading : CoolingDataState<Nothing, Nothing>

    data class Content<T, F>(
        val value: T,
        val freshness: CoolingDataFreshness = CoolingDataFreshness.CURRENT,
        val refreshFailure: F? = null
    ) : CoolingDataState<T, F>

    data class Empty<T, F>(
        val value: T,
        val freshness: CoolingDataFreshness = CoolingDataFreshness.CURRENT,
        val refreshFailure: F? = null
    ) : CoolingDataState<T, F>

    data object Unavailable : CoolingDataState<Nothing, Nothing>
    data object Unsupported : CoolingDataState<Nothing, Nothing>

    data class OperationError<F>(
        val failure: F
    ) : CoolingDataState<Nothing, F>
}

/** Shared lifecycle vocabulary for Cooling write operations. */
sealed interface CoolingMutationState<out F> {
    data object Idle : CoolingMutationState<Nothing>
    data object Saving : CoolingMutationState<Nothing>
    data object Saved : CoolingMutationState<Nothing>
    data object ValidationError : CoolingMutationState<Nothing>

    data class OperationError<F>(
        val failure: F
    ) : CoolingMutationState<F>
}

val <T, F> CoolingDataState<T, F>.authoritativeValueOrNull: T?
    get() = when (this) {
        is CoolingDataState.Content -> value
        is CoolingDataState.Empty -> value
        CoolingDataState.Initial,
        CoolingDataState.Loading,
        CoolingDataState.Unavailable,
        CoolingDataState.Unsupported,
        is CoolingDataState.OperationError -> null
    }

val <T, F> CoolingDataState<T, F>.isCurrentAuthoritative: Boolean
    get() = when (this) {
        is CoolingDataState.Content -> freshness == CoolingDataFreshness.CURRENT
        is CoolingDataState.Empty -> freshness == CoolingDataFreshness.CURRENT
        CoolingDataState.Initial,
        CoolingDataState.Loading,
        CoolingDataState.Unavailable,
        CoolingDataState.Unsupported,
        is CoolingDataState.OperationError -> false
    }
