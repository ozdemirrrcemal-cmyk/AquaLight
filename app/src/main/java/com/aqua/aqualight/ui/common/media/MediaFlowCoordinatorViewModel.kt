package com.aqua.aqualight.ui.common.media

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.aqua.aqualight.R
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import com.aqua.aqualight.platform.media.FeedbackMediaFailureKind
import com.aqua.aqualight.platform.media.FeedbackMediaProcessingResult
import com.aqua.aqualight.platform.media.FeedbackMediaProcessor
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Saved-state-capable coordinator shared by profile and tank camera/gallery/crop flows. */
class MediaFlowCoordinatorViewModel(
    private val savedStateHandle: SavedStateHandle,
    context: Context,
    private val scope: AppMediaScope,
    private val ownerToken: String,
    private val ownerUid: String,
    private val cropSpec: MediaCropSpec,
    private val mediaProcessor: FeedbackMediaProcessor,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val appContext = context.applicationContext
    private val preparationMutex = Mutex()
    private val terminalCleanupScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _selection = MutableStateFlow(
        MediaSelectionState(
            initialized = savedStateHandle[KEY_INITIALIZED] ?: false,
            persistedUri = savedStateHandle[KEY_PERSISTED_URI],
            selectedUri = savedStateHandle[KEY_SELECTED_URI],
            externalLifecycleOwner = savedStateHandle[KEY_EXTERNAL_OWNER] ?: false
        )
    )
    val selection: StateFlow<MediaSelectionState> = _selection.asStateFlow()

    init {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        viewModelScope.launch(dispatcher) {
            AppMediaStorage.cleanupStaleTemporaryFiles(appContext)
            mediaProcessor.cleanupExpired()
        }
    }

    fun initializeSelection(
        persistedUri: String?,
        externalLifecycleOwner: Boolean = false
    ) {
        if (_selection.value.initialized) return
        val normalized = persistedUri?.takeIf(String::isNotBlank)
        updateSelection(
            MediaSelectionState(
                initialized = true,
                persistedUri = normalized,
                selectedUri = normalized,
                externalLifecycleOwner = externalLifecycleOwner
            )
        )
    }

    fun currentCameraUri(): Uri? =
        savedStateHandle.get<String>(KEY_CAMERA_URI)?.let(Uri::parse)

    suspend fun createCameraUri(): Uri? = preparationMutex.withLock {
        cancelCropLocked()
        cancelCameraLocked()
        val uri = withContext(dispatcher) {
            AppMediaStorage.createCameraCaptureUri(
                context = appContext,
                scope = scope,
                ownerToken = ownerToken
            )
        }
        savedStateHandle[KEY_CAMERA_URI] = uri?.toString()
        uri
    }

    /**
     * Every camera/gallery source is first copied, bounds-checked, sampled, resized and compressed by
     * the bounded processor. UCrop therefore never receives an untrusted multi-megapixel provider.
     */
    suspend fun prepareCropIntent(
        sourceUri: Uri,
        title: String
    ): MediaCropPreparationResult = preparationMutex.withLock {
        clearPreparedSourceLocked()
        val previousOutput = savedStateHandle.get<String>(KEY_CROP_OUTPUT_URI)
        clearPendingCropState()
        withContext(dispatcher) {
            AppMediaStorage.deleteInternalMedia(appContext, previousOutput)
        }

        when (val processed = mediaProcessor.process(sourceUri)) {
            is FeedbackMediaProcessingResult.Failure -> {
                MediaCropPreparationResult.Failure(processed.kind)
            }

            is FeedbackMediaProcessingResult.Success -> {
                val safeSourceUri = withContext(dispatcher) {
                    AppMediaStorage.toContentUriForOwnedPath(
                        context = appContext,
                        path = processed.media.path
                    )
                }
                if (safeSourceUri == null) {
                    mediaProcessor.delete(processed.media.path)
                    return@withLock MediaCropPreparationResult.StorageFailure
                }

                val destinationUri = withContext(dispatcher) {
                    AppMediaStorage.createCropOutputUri(
                        context = appContext,
                        scope = scope,
                        ownerToken = ownerToken
                    )
                }
                if (destinationUri == null) {
                    mediaProcessor.delete(processed.media.path)
                    return@withLock MediaCropPreparationResult.StorageFailure
                }

                savedStateHandle[KEY_PREPARED_SOURCE_PATH] = processed.media.path
                savedStateHandle[KEY_CROP_SOURCE_URI] = safeSourceUri.toString()
                savedStateHandle[KEY_CROP_OUTPUT_URI] = destinationUri.toString()

                MediaCropPreparationResult.Ready(
                    buildCropIntent(
                        sourceUri = safeSourceUri,
                        destinationUri = destinationUri,
                        title = title
                    )
                )
            }
        }
    }

    suspend fun acceptCrop(resultUri: Uri): Uri? = preparationMutex.withLock {
        val promoted = withContext(dispatcher) {
            AppMediaStorage.promoteCropOutput(
                context = appContext,
                scope = scope,
                ownerToken = ownerToken,
                ownerUid = ownerUid,
                outputUri = resultUri
            )
        } ?: return@withLock null

        val previousSelected = _selection.value.selectedUri
        val persisted = _selection.value.persistedUri
        if (
            previousSelected != null &&
            previousSelected != persisted &&
            previousSelected != promoted.toString()
        ) {
            withContext(dispatcher) {
                AppMediaStorage.rollbackPendingMedia(appContext, previousSelected)
            }
        }

        cleanupPendingSourceLocked(keepUri = promoted.toString())
        clearPreparedSourceLocked()
        clearPendingCropState()
        savedStateHandle.remove<String>(KEY_CAMERA_URI)
        updateSelection(_selection.value.copy(selectedUri = promoted.toString()))
        promoted
    }

    suspend fun selectRemoval() = preparationMutex.withLock {
        val selected = _selection.value.selectedUri
        val persisted = _selection.value.persistedUri
        if (selected != null && selected != persisted) {
            withContext(dispatcher) {
                AppMediaStorage.rollbackPendingMedia(appContext, selected)
            }
        }
        updateSelection(_selection.value.copy(selectedUri = null))
    }

    /**
     * When deletePersistedMedia is false, a repository has already committed or rolled back all
     * filesystem ownership. The coordinator only acknowledges the authoritative domain state.
     */
    suspend fun commitSelection(deletePersistedMedia: Boolean = true): String? =
        preparationMutex.withLock {
            val state = _selection.value
            if (deletePersistedMedia) {
                withContext(dispatcher) {
                    AppMediaStorage.commitPendingMedia(appContext, state.selectedUri)
                    if (state.persistedUri != state.selectedUri) {
                        AppMediaStorage.deleteInternalMedia(appContext, state.persistedUri)
                    }
                }
            }
            updateSelection(state.copy(persistedUri = state.selectedUri))
            state.selectedUri
        }

    suspend fun rollbackSelection(): String? = preparationMutex.withLock {
        val state = _selection.value
        if (state.selectedUri != state.persistedUri) {
            withContext(dispatcher) {
                AppMediaStorage.rollbackPendingMedia(appContext, state.selectedUri)
            }
        }
        updateSelection(state.copy(selectedUri = state.persistedUri))
        state.persistedUri
    }

    fun markExternallyOwnedSelection(uriString: String?) {
        val normalized = uriString?.takeIf(String::isNotBlank)
        updateSelection(
            _selection.value.copy(
                initialized = true,
                persistedUri = normalized,
                selectedUri = normalized,
                externalLifecycleOwner = true
            )
        )
    }

    suspend fun deleteInternalMedia(uriString: String?) = withContext(dispatcher) {
        AppMediaStorage.rollbackPendingMedia(appContext, uriString)
        Unit
    }

    suspend fun cancelCamera() = preparationMutex.withLock {
        cancelCameraLocked()
    }

    suspend fun cancelCrop() = preparationMutex.withLock {
        cancelCropLocked()
    }

    private suspend fun cancelCameraLocked() {
        val cameraUri = savedStateHandle.get<String>(KEY_CAMERA_URI)
        savedStateHandle.remove<String>(KEY_CAMERA_URI)
        withContext(dispatcher) {
            AppMediaStorage.deleteInternalMedia(appContext, cameraUri)
        }
    }

    private suspend fun cancelCropLocked() {
        val outputUri = savedStateHandle.get<String>(KEY_CROP_OUTPUT_URI)
        val cameraUri = savedStateHandle.get<String>(KEY_CAMERA_URI)
        val sourceUri = savedStateHandle.get<String>(KEY_CROP_SOURCE_URI)
        val preparedPath = savedStateHandle.get<String>(KEY_PREPARED_SOURCE_PATH)
        clearPendingCropState()
        savedStateHandle.remove<String>(KEY_PREPARED_SOURCE_PATH)
        if (sourceUri == cameraUri) {
            savedStateHandle.remove<String>(KEY_CAMERA_URI)
        }
        withContext(dispatcher) {
            AppMediaStorage.deleteInternalMedia(appContext, outputUri)
            if (sourceUri == cameraUri) {
                AppMediaStorage.deleteInternalMedia(appContext, cameraUri)
            }
            mediaProcessor.delete(preparedPath)
        }
    }

    private fun buildCropIntent(
        sourceUri: Uri,
        destinationUri: Uri,
        title: String
    ): Intent {
        val options = UCrop.Options().apply {
            setToolbarTitle(title)
            setCircleDimmedLayer(cropSpec.circleDimmedLayer)
            setShowCropGrid(cropSpec.showCropGrid)
            setShowCropFrame(cropSpec.showCropFrame)
            setHideBottomControls(true)
            setCompressionQuality(cropSpec.compressionQuality)
            setFreeStyleCropEnabled(false)
            setMaxBitmapSize(cropSpec.maxSourceBitmapSize)

            val toolbarColor = ContextCompat.getColor(appContext, R.color.crop_toolbar_bg)
            setToolbarColor(toolbarColor)
            setToolbarWidgetColor(Color.WHITE)
            setRootViewBackgroundColor(toolbarColor)
            setActiveControlsWidgetColor(toolbarColor)
            setToolbarCancelDrawable(R.drawable.ic_back)
        }

        return UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(cropSpec.aspectRatioX, cropSpec.aspectRatioY)
            .withMaxResultSize(cropSpec.maxWidth, cropSpec.maxHeight)
            .withOptions(options)
            .getIntent(appContext)
            .apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
    }

    private suspend fun cleanupPendingSourceLocked(keepUri: String?) {
        val source = savedStateHandle.get<String>(KEY_CROP_SOURCE_URI)
        val camera = savedStateHandle.get<String>(KEY_CAMERA_URI)
        if (camera != null && camera != keepUri) {
            savedStateHandle.remove<String>(KEY_CAMERA_URI)
            withContext(dispatcher) {
                AppMediaStorage.deleteInternalMedia(appContext, camera)
            }
        }
        if (source == camera) savedStateHandle.remove<String>(KEY_CROP_SOURCE_URI)
    }

    private suspend fun clearPreparedSourceLocked() {
        val path = savedStateHandle.get<String>(KEY_PREPARED_SOURCE_PATH)
        savedStateHandle.remove<String>(KEY_PREPARED_SOURCE_PATH)
        mediaProcessor.delete(path)
    }

    private fun clearPendingCropState() {
        savedStateHandle.remove<String>(KEY_CROP_SOURCE_URI)
        savedStateHandle.remove<String>(KEY_CROP_OUTPUT_URI)
    }

    private fun updateSelection(state: MediaSelectionState) {
        _selection.value = state
        savedStateHandle[KEY_INITIALIZED] = state.initialized
        savedStateHandle[KEY_PERSISTED_URI] = state.persistedUri
        savedStateHandle[KEY_SELECTED_URI] = state.selectedUri
        savedStateHandle[KEY_EXTERNAL_OWNER] = state.externalLifecycleOwner
    }

    override fun onCleared() {
        val outputUri = savedStateHandle.get<String>(KEY_CROP_OUTPUT_URI)
        val cameraUri = savedStateHandle.get<String>(KEY_CAMERA_URI)
        val preparedPath = savedStateHandle.get<String>(KEY_PREPARED_SOURCE_PATH)
        terminalCleanupScope.launch {
            try {
                // Only transient camera/crop artifacts belong to the coordinator lifecycle.
                // A promoted selection may already be referenced by a successful repository write;
                // pending-media reconciliation is the sole authority for that candidate.
                AppMediaStorage.deleteInternalMedia(appContext, outputUri)
                AppMediaStorage.deleteInternalMedia(appContext, cameraUri)
                mediaProcessor.delete(preparedPath)
            } finally {
                terminalCleanupScope.cancel()
            }
        }
        super.onCleared()
    }

    companion object {
        private const val KEY_INITIALIZED = "media.initialized"
        private const val KEY_PERSISTED_URI = "media.persistedUri"
        private const val KEY_SELECTED_URI = "media.selectedUri"
        private const val KEY_EXTERNAL_OWNER = "media.externalOwner"
        private const val KEY_CAMERA_URI = "media.cameraUri"
        private const val KEY_CROP_SOURCE_URI = "media.cropSourceUri"
        private const val KEY_CROP_OUTPUT_URI = "media.cropOutputUri"
        private const val KEY_PREPARED_SOURCE_PATH = "media.preparedSourcePath"

        fun factory(
            context: Context,
            scope: AppMediaScope,
            ownerToken: String,
            ownerUid: String,
            cropSpec: MediaCropSpec,
            mediaProcessor: FeedbackMediaProcessor
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    require(modelClass == MediaFlowCoordinatorViewModel::class.java) {
                        "Unsupported ViewModel: ${modelClass.name}"
                    }
                    val viewModel = MediaFlowCoordinatorViewModel(
                        savedStateHandle = extras.createSavedStateHandle(),
                        context = context,
                        scope = scope,
                        ownerToken = ownerToken,
                        ownerUid = ownerUid,
                        cropSpec = cropSpec,
                        mediaProcessor = mediaProcessor
                    )
                    @Suppress("UNCHECKED_CAST")
                    return viewModel as T
                }
            }
        }
    }
}

data class MediaSelectionState(
    val initialized: Boolean = false,
    val persistedUri: String? = null,
    val selectedUri: String? = null,
    val externalLifecycleOwner: Boolean = false
) {
    val hasPendingChange: Boolean
        get() = initialized && persistedUri != selectedUri
}

sealed interface MediaCropPreparationResult {
    data class Ready(val intent: Intent) : MediaCropPreparationResult
    data class Failure(val kind: FeedbackMediaFailureKind) : MediaCropPreparationResult
    data object StorageFailure : MediaCropPreparationResult
}

data class MediaCropSpec(
    val aspectRatioX: Float,
    val aspectRatioY: Float,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxSourceBitmapSize: Int,
    val circleDimmedLayer: Boolean,
    val showCropGrid: Boolean,
    val showCropFrame: Boolean,
    val compressionQuality: Int
) {
    companion object {
        val PROFILE = MediaCropSpec(
            aspectRatioX = 1f,
            aspectRatioY = 1f,
            maxWidth = 1_024,
            maxHeight = 1_024,
            maxSourceBitmapSize = 2_048,
            circleDimmedLayer = true,
            showCropGrid = true,
            showCropFrame = false,
            compressionQuality = 88
        )

        val TANK = MediaCropSpec(
            aspectRatioX = 16f,
            aspectRatioY = 9f,
            maxWidth = 1_600,
            maxHeight = 900,
            maxSourceBitmapSize = 3_200,
            circleDimmedLayer = false,
            showCropGrid = true,
            showCropFrame = true,
            compressionQuality = 88
        )
    }
}
