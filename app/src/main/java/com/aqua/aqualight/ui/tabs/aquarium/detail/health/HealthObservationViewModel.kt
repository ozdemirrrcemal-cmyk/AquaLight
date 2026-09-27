package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationCursor
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationInput
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationKind
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationOperations
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationQuery
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Fragment-scoped factory binding; route identity and owner-bound operations never change in place. */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthObservationViewModel(
    private val operations: HealthObservationOperations,
    private val savedState: SavedStateHandle
) : ViewModel() {
    val tankId = checkNotNull(savedState.get<Long>("tankId"))
    val kind = enumValueOf<HealthObservationKind>(checkNotNull(savedState["healthKind"]))
    val observationId = savedState.get<Long>("observationId") ?: 0L
    val previousId = savedState.get<Long>("previousObservationId") ?: 0L
    internal val mutations = HealthObservationMutations(operations, viewModelScope)
    internal val requestId = savedState.get<String>("health_request") ?: UUID.randomUUID().toString()
        .also { savedState["health_request"] = it }
    internal var draft: Bundle
        get() = savedState.get<Bundle>("health_draft")?.let(::Bundle) ?: Bundle()
        set(value) { savedState["health_draft"] = Bundle(value) }
    private val refresh = MutableStateFlow(0)

    init { require(tankId > 0L && observationId >= 0L && previousId >= 0L) }

    internal val preparation = refresh.flatMapLatest {
        flow { emit(operations.prepare(tankId, System.currentTimeMillis())) }.asHealthLoadState()
    }.asLiveData()

    internal val history by lazy {
        combine(savedState.getStateFlow<Bundle?>("health_page", null), refresh) { page, _ -> page }
            .flatMapLatest { cursor ->
            val page = cursor?.let {
                HealthObservationCursor(it.getLong("observed"), it.getLong("created"), it.getLong("id"))
            }
            operations.history(HealthObservationQuery(tankId, kind, cursor = page)).asHealthLoadState()
        }.asLiveData()
    }

    internal val record by lazy {
        require(observationId > 0L)
        operations.observation(tankId, observationId).map { it?.takeIf { row -> row.input.observation.kind == kind } }
            .asHealthLoadState().asLiveData()
    }

    internal val previous by lazy {
        require(previousId > 0L)
        operations.observation(tankId, previousId).map { it?.takeIf { row -> row.input.observation.kind == kind } }
            .asHealthLoadState().asLiveData()
    }

    internal fun retryPreparation() { refresh.value += 1 }

    internal fun page(cursor: HealthObservationCursor?) {
        savedState["health_page"] = cursor?.let {
            Bundle().apply {
                putLong("observed", it.observedAtMillis)
                putLong("created", it.createdAtMillis)
                putLong("id", it.observationId)
            }
        }
    }

    internal fun save(input: HealthObservationInput) {
        require(input.identity.tankId == tankId && input.observation.kind == kind && observationId == 0L)
        require(input.identity.requestId == requestId)
        require((input.notes.followUp.previousObservationId ?: 0L) == previousId)
        mutations.save(input)
    }

    internal fun delete() {
        require(observationId > 0L)
        mutations.delete(tankId, observationId)
    }
}

internal sealed interface HealthLoadState<out T> {
    data object Loading : HealthLoadState<Nothing>
    data class Content<T>(val value: T) : HealthLoadState<T>
    data object Failed : HealthLoadState<Nothing>
}

internal fun <T> Flow<T>.asHealthLoadState(): Flow<HealthLoadState<T>> =
    map<T, HealthLoadState<T>> { HealthLoadState.Content(it) }.onStart { emit(HealthLoadState.Loading) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(HealthLoadState.Failed)
        }
