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
import androidx.lifecycle.viewmodel.CreationExtras
import com.aqua.aqualight.R
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Saved-state-capable coordinator shared by profile and tank camera/gallery/crop flows. */
class MediaFlowCoordinatorViewModel(
    private val savedStateHandle: SavedStateHandle,
    context: Context,
    private val scope: AppMediaScope,
    private val ownerToken: String,
    private val cropSpec: MediaCropSpec
) : ViewModel() {

    private val appContext = context.applicationContext
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
        AppMediaStorage.cleanupStaleTemporaryFiles(appContext)
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

    fun currentCameraUri(): Uri? {
        return savedStateHandle.get<String>(KEY_CAMERA_URI)?.let(Uri::parse)
    }

    fun createCameraUri(): Uri? {
        cancelCrop()
        cancelCamera()
        val uri = AppMediaStorage.createCameraCaptureUri(
            context = appContext,
            scope = scope,
            ownerToken = ownerToken
        )
        savedStateHandle[KEY_CAMERA_URI] = uri?.toString()
        return uri
    }

    fun buildCropIntent(
        sourceUri: Uri,
        title: String
    ): Intent? {
        cancelCrop()
        val destinationUri = AppMediaStorage.createCropOutputUri(
            context = appContext,
            scope = scope,
            ownerToken = ownerToken
        ) ?: return null

        savedStateHandle[KEY_CROP_SOURCE_URI] = sourceUri.toString()
        savedStateHandle[KEY_CROP_OUTPUT_URI] = destinationUri.toString()

        val options = UCrop.Options().apply {
            setToolbarTitle(title)
            setCircleDimmedLayer(cropSpec.circleDimmedLayer)
            setShowCropGrid(cropSpec.showCropGrid)
            setShowCropFrame(cropSpec.showCropFrame)
            setHideBottomControls(true)
            setCompressionQuality(cropSpec.compressionQuality)
            setFreeStyleCropEnabled(false)

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

    fun acceptCrop(resultUri: Uri): Uri? {
        val promoted = AppMediaStorage.promoteCropOutput(
            context = appContext,
            scope = scope,
            ownerToken = ownerToken,
            outputUri = resultUri
        ) ?: return null

        val previousSelected = _selection.value.selectedUri
        val persisted = _selection.value.persistedUri
        if (
            previousSelected != null &&
            previousSelected != persisted &&
            previousSelected != promoted.toString()
        ) {
            AppMediaStorage.deleteInternalMedia(appContext, previousSelected)
        }

        cleanupPendingSource(keepUri = promoted.toString())
        clearPendingCropState()
        savedStateHandle.remove<String>(KEY_CAMERA_URI)
        updateSelection(_selection.value.copy(selectedUri = promoted.toString()))
        return promoted
    }

    fun selectRemoval() {
        val selected = _selection.value.selectedUri
        val persisted = _selection.value.persistedUri
        if (selected != null && selected != persisted) {
            AppMediaStorage.deleteInternalMedia(appContext, selected)
        }
        updateSelection(_selection.value.copy(selectedUri = null))
    }

    fun commitSelection(deletePersistedMedia: Boolean = true): String? {
        val state = _selection.value
        if (deletePersistedMedia && state.persistedUri != state.selectedUri) {
            AppMediaStorage.deleteInternalMedia(appContext, state.persistedUri)
        }
        updateSelection(state.copy(persistedUri = state.selectedUri))
        return state.selectedUri
    }

    fun rollbackSelection(): String? {
        val state = _selection.value
        if (state.selectedUri != state.persistedUri) {
            AppMediaStorage.deleteInternalMedia(appContext, state.selectedUri)
        }
        updateSelection(state.copy(selectedUri = state.persistedUri))
        return state.persistedUri
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

    fun deleteInternalMedia(uriString: String?) {
        AppMediaStorage.deleteInternalMedia(appContext, uriString)
    }

    fun cancelCamera() {
        val cameraUri = savedStateHandle.get<String>(KEY_CAMERA_URI)
        AppMediaStorage.deleteInternalMedia(appContext, cameraUri)
        savedStateHandle.remove<String>(KEY_CAMERA_URI)
    }

    fun cancelCrop() {
        cleanupPendingSource(keepUri = null)
        AppMediaStorage.deleteInternalMedia(
            appContext,
            savedStateHandle.get<String>(KEY_CROP_OUTPUT_URI)
        )
        clearPendingCropState()
    }

    private fun cleanupPendingSource(keepUri: String?) {
        val source = savedStateHandle.get<String>(KEY_CROP_SOURCE_URI)
        val camera = savedStateHandle.get<String>(KEY_CAMERA_URI)
        if (source != null && source != keepUri && source == camera) {
            AppMediaStorage.deleteInternalMedia(appContext, source)
            savedStateHandle.remove<String>(KEY_CAMERA_URI)
        }
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
        cancelCrop()
        cancelCamera()
        val state = _selection.value
        if (!state.externalLifecycleOwner && state.selectedUri != state.persistedUri) {
            AppMediaStorage.deleteInternalMedia(appContext, state.selectedUri)
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

        fun factory(
            context: Context,
            scope: AppMediaScope,
            ownerToken: String,
            cropSpec: MediaCropSpec
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
                        cropSpec = cropSpec
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

data class MediaCropSpec(
    val aspectRatioX: Float,
    val aspectRatioY: Float,
    val maxWidth: Int,
    val maxHeight: Int,
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
            circleDimmedLayer = false,
            showCropGrid = true,
            showCropFrame = true,
            compressionQuality = 88
        )
    }
}
