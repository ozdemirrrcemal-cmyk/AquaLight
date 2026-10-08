package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.aquarium.health.PlantHealthOperations
import com.aqua.aqualight.application.aquarium.health.PlantObservationInput
import com.aqua.aqualight.application.aquarium.health.PlantObservationRules
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns the immutable save request and pending photos across view and process recreation. */
class PlantObservationDraftViewModel(
    private val operations: PlantHealthOperations,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val mutex = Mutex()
    private val revisionState = MutableStateFlow(0L)
    val revision = revisionState.asStateFlow()
    private val saveState = MutableStateFlow<PlantSaveState>(
        savedState.get<Long>(SAVED_ID)?.let { PlantSaveState.Saved(it) }
            ?: if (savedState.get<Boolean>(ATTEMPTED) == true) PlantSaveState.Failed else PlantSaveState.Idle
    )
    val save = saveState.asStateFlow()
    val symptoms: Set<String> get() = savedState.get<ArrayList<String>>(SIGNS).orEmpty().toSet()
    val note: String get() = savedState[NOTE] ?: ""
    val photos: List<String> get() = savedState.get<ArrayList<String>>(PHOTOS).orEmpty()
    val locked: Boolean get() = savedState[ATTEMPTED] ?: false
    val canSave: Boolean get() = PlantObservationRules.canSave(symptoms, note)

    init {
        if (!savedState.contains(REQUEST)) savedState[REQUEST] = UUID.randomUUID().toString()
    }

    fun toggle(key: String) {
        if (locked) return
        savedState[SIGNS] = ArrayList(PlantObservationRules.toggle(symptoms, key))
        revisionState.value++
    }

    fun updateNote(value: String) {
        if (locked || value == note) return
        savedState[NOTE] = value
        revisionState.value++
    }

    suspend fun replacePhoto(index: Int, uri: String?) = mutex.withLock {
        if (locked) return@withLock
        require(index in 0 until PlantObservationRules.MAX_PHOTOS)
        val slots = MutableList(PlantObservationRules.MAX_PHOTOS) { photos.getOrElse(it) { "" } }
        val old = slots[index]
        slots[index] = uri.orEmpty()
        savedState[PHOTOS] = ArrayList(slots)
        revisionState.value++
        if (old.isNotBlank() && old != uri) operations.discardDraftPhotos(listOf(old))
    }

    fun submit(tankId: Long, plantId: Long) {
        if (!canSave || saveState.value == PlantSaveState.Working || saveState.value is PlantSaveState.Saved) return
        savedState[ATTEMPTED] = true
        saveState.value = PlantSaveState.Working
        val input = PlantObservationInput(
            requireNotNull(savedState[REQUEST]), tankId, plantId,
            symptoms.toList(), note, photos.filter(String::isNotBlank)
        )
        viewModelScope.launch { persist(input) }
    }

    private suspend fun persist(input: PlantObservationInput) = mutex.withLock {
        runCatching { operations.createObservation(input) }.onSuccess { id ->
            savedState[SAVED_ID] = id
            saveState.value = PlantSaveState.Saved(id)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            // Only unlock edits after the authoritative store confirms no request was written.
            // Ambiguous failures keep the payload fixed so retry can finish the same record.
            val exists = runCatching { operations.containsRequest(input.requestId) }
                .onFailure { if (it is CancellationException) throw it }.getOrNull()
            if (exists == false) savedState[ATTEMPTED] = false
            saveState.value = PlantSaveState.Failed
        }
    }

    override fun onCleared() {
        val pending = photos.filter(String::isNotBlank)
        val handler = CoroutineExceptionHandler { _, _ ->
            android.util.Log.e("PlantDraftCleanup", "Pending photos retained for media recovery.")
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO + handler).launch {
            mutex.withLock { operations.discardDraftPhotos(pending) }
        }
        super.onCleared()
    }

    private companion object {
        const val SIGNS = "plantSigns"
        const val NOTE = "plantNote"
        const val PHOTOS = "plantPhotos"
        const val REQUEST = "plantRequest"
        const val ATTEMPTED = "plantAttempted"
        const val SAVED_ID = "plantSavedId"
    }
}

sealed interface PlantSaveState {
    data object Idle : PlantSaveState
    data object Working : PlantSaveState
    data object Failed : PlantSaveState
    data class Saved(val id: Long) : PlantSaveState
}
