package com.aqua.aqualight.ui.tabs.settings.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.user.UserDataArchiveArtifact
import com.aqua.aqualight.application.user.UserDataArchiveOperations
import com.aqua.aqualight.application.user.UserDataBackupInspection
import com.aqua.aqualight.application.user.UserDataDocumentOperations
import com.aqua.aqualight.application.user.UserDataRestoreResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DataManagementViewModel(
    private val archiveOperations: UserDataArchiveOperations,
    private val documentOperations: UserDataDocumentOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<DataManagementEvent>(Channel.BUFFERED)
    val events: Flow<DataManagementEvent> = eventChannel.receiveAsFlow()

    private var pendingWrite: PendingWrite? = null
    private var pendingRestore: ByteArray? = null

    fun requestBackup() {
        createArtifact(DataManagementAction.BACKUP, archiveOperations::createBackup)
    }

    fun requestPortableExport() {
        createArtifact(DataManagementAction.EXPORT, archiveOperations::createPortableExport)
    }

    fun requestRestoreDocument() {
        if (!beginOperation()) return
        eventChannel.trySend(DataManagementEvent.OpenBackupDocument)
    }

    fun cancelPendingOperation() {
        pendingWrite = null
        pendingRestore = null
        _uiState.finishOperation()
    }

    fun writePendingDocument(documentHandle: String) {
        val pending = pendingWrite ?: run {
            _uiState.finishOperation()
            return
        }
        viewModelScope.launch {
            val result = documentOperations.write(
                documentHandle = documentHandle,
                content = pending.artifact.content
            )
            pendingWrite = null
            _uiState.finishOperation()
            eventChannel.trySend(
                if (result.isSuccess) {
                    DataManagementEvent.OperationSucceeded(pending.action)
                } else {
                    DataManagementEvent.OperationFailed(pending.action)
                }
            )
        }
    }

    fun inspectRestoreDocument(documentHandle: String) {
        viewModelScope.launch {
            val contentResult = documentOperations.read(documentHandle)
            val content = contentResult.getOrNull()
            val inspectionResult = content?.let { bytes -> archiveOperations.inspectBackup(bytes) }
            val inspection = inspectionResult?.getOrNull()
            if (content == null || inspection == null) {
                pendingRestore = null
                _uiState.finishOperation()
                eventChannel.trySend(
                    DataManagementEvent.OperationFailed(DataManagementAction.RESTORE)
                )
                return@launch
            }

            pendingRestore = content
            _uiState.finishOperation()
            eventChannel.trySend(DataManagementEvent.ShowRestorePreview(inspection))
        }
    }

    fun confirmRestore() {
        val content = pendingRestore ?: return
        if (!beginOperation()) return
        viewModelScope.launch {
            val result = archiveOperations.restoreBackup(content)
            pendingRestore = null
            _uiState.finishOperation()
            val restored = result.getOrNull()
            eventChannel.trySend(
                if (restored != null) {
                    DataManagementEvent.RestoreSucceeded(restored)
                } else {
                    DataManagementEvent.OperationFailed(DataManagementAction.RESTORE)
                }
            )
        }
    }

    override fun onCleared() {
        eventChannel.close()
        super.onCleared()
    }

    private fun createArtifact(
        action: DataManagementAction,
        creator: suspend () -> Result<UserDataArchiveArtifact>
    ) {
        if (!beginOperation()) return
        viewModelScope.launch {
            val artifact = creator().getOrNull()
            if (artifact == null) {
                _uiState.finishOperation()
                eventChannel.trySend(DataManagementEvent.OperationFailed(action))
                return@launch
            }
            pendingWrite = PendingWrite(action, artifact)
            eventChannel.trySend(
                DataManagementEvent.CreateDocument(
                    suggestedFileName = artifact.suggestedFileName,
                    mimeType = artifact.mimeType
                )
            )
        }
    }

    private fun beginOperation(): Boolean {
        if (_uiState.value.busy) return false
        _uiState.update { state -> state.copy(busy = true) }
        return true
    }

    private data class PendingWrite(
        val action: DataManagementAction,
        val artifact: UserDataArchiveArtifact
    )
}


private fun MutableStateFlow<DataManagementUiState>.finishOperation() {
    update { state -> state.copy(busy = false) }
}

data class DataManagementUiState(
    val busy: Boolean = false
)

enum class DataManagementAction {
    BACKUP,
    RESTORE,
    EXPORT
}

sealed interface DataManagementEvent {
    data class CreateDocument(
        val suggestedFileName: String,
        val mimeType: String
    ) : DataManagementEvent

    data object OpenBackupDocument : DataManagementEvent

    data class ShowRestorePreview(
        val inspection: UserDataBackupInspection
    ) : DataManagementEvent

    data class OperationSucceeded(
        val action: DataManagementAction
    ) : DataManagementEvent

    data class OperationFailed(
        val action: DataManagementAction
    ) : DataManagementEvent

    data class RestoreSucceeded(
        val result: UserDataRestoreResult
    ) : DataManagementEvent
}
