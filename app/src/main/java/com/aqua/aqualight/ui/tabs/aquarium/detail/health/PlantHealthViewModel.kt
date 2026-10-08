package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.aquarium.health.PlantHealthOperations
import com.aqua.aqualight.application.aquarium.health.PlantObservationSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PlantHealthViewModel(private val operations: PlantHealthOperations) : ViewModel() {
    private val refresh = MutableStateFlow(0L)
    private val deletionState = MutableStateFlow<PlantDeletionState>(PlantDeletionState.Idle)
    val deletion = deletionState.asStateFlow()

    fun retry() { refresh.value++ }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observations(tankId: Long, plantId: Long) = refresh.flatMapLatest {
        flow { emitAll(operations.observationsForPlant(tankId, plantId)) }
            .map<List<PlantObservationSnapshot>, PlantObservationsState> { PlantObservationsState.Ready(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(PlantObservationsState.Failed)
            }
    }

    fun delete(tankId: Long, plantId: Long, id: Long) {
        if (deletionState.value == PlantDeletionState.Working) return
        deletionState.value = PlantDeletionState.Working
        viewModelScope.launch {
            try {
                operations.deleteObservation(tankId, plantId, id)
                deletionState.value = PlantDeletionState.Deleted
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                deletionState.value = PlantDeletionState.Failed
            }
        }
    }
}

sealed interface PlantObservationsState {
    data class Ready(val records: List<PlantObservationSnapshot>) : PlantObservationsState
    data object Failed : PlantObservationsState
}

enum class PlantDeletionState { Idle, Working, Deleted, Failed }
