package com.aqua.aqualight.ui.tabs.settings.feedback

import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailure
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import com.aqua.aqualight.application.feedback.FeedbackSubmissionUseCase
import com.aqua.aqualight.platform.media.FeedbackMediaFailureKind
import com.aqua.aqualight.platform.media.FeedbackMediaProcessingResult
import com.aqua.aqualight.platform.media.FeedbackMediaProcessor
import com.aqua.aqualight.platform.media.ProcessedFeedbackMedia
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FeedbackViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val submissionUseCase: FeedbackSubmissionUseCase,
    private val mediaProcessor: FeedbackMediaProcessor,
    private val appVersionProvider: () -> String = { BuildConfig.VERSION_NAME },
    private val localeTagProvider: () -> String = { Locale.getDefault().toLanguageTag() }
) : ViewModel() {

    private val restoredMedia = mediaProcessor.restore(
        path = savedStateHandle[KEY_SCREENSHOT_PATH],
        displayName = savedStateHandle[KEY_SCREENSHOT_NAME],
        width = savedStateHandle[KEY_SCREENSHOT_WIDTH],
        height = savedStateHandle[KEY_SCREENSHOT_HEIGHT],
        byteCount = savedStateHandle[KEY_SCREENSHOT_BYTES]
    )

    private val _uiState = MutableStateFlow(
        FeedbackUiState(
            category = savedStateHandle.get<String>(KEY_CATEGORY).orEmpty(),
            email = savedStateHandle.get<String>(KEY_EMAIL).orEmpty(),
            message = savedStateHandle.get<String>(KEY_MESSAGE).orEmpty(),
            screenshot = restoredMedia
        )
    )
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<FeedbackUiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var mediaJob: Job? = null
    private var submitJob: Job? = null

    init {
        if (restoredMedia == null) {
            clearPersistedScreenshot()
        }
        viewModelScope.launch {
            mediaProcessor.cleanupExpired()
            submissionUseCase.cleanupOrphans()
        }
    }

    fun updateCategory(value: String) {
        savedStateHandle[KEY_CATEGORY] = value
        _uiState.update { it.copy(category = value, categoryError = false) }
    }

    fun updateEmail(value: String) {
        savedStateHandle[KEY_EMAIL] = value
        _uiState.update { it.copy(email = value, emailError = false) }
    }

    fun updateMessage(value: String) {
        savedStateHandle[KEY_MESSAGE] = value
        _uiState.update { it.copy(message = value, messageError = false) }
    }

    fun selectScreenshot(uri: Uri) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isProcessingMedia = true) }

        mediaJob = viewModelScope.launch {
            try {
                when (val result = mediaProcessor.process(uri)) {
                    is FeedbackMediaProcessingResult.Success -> {
                        val previous = _uiState.value.screenshot
                        if (previous?.path != result.media.path) {
                            mediaProcessor.delete(previous?.path)
                        }
                        persistScreenshot(result.media)
                        _uiState.update { it.copy(screenshot = result.media) }
                        eventChannel.send(FeedbackUiEvent.ScreenshotSelected)
                    }
                    is FeedbackMediaProcessingResult.Failure -> {
                        eventChannel.send(FeedbackUiEvent.MediaProcessingFailed(result.kind))
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                eventChannel.send(
                    FeedbackUiEvent.MediaProcessingFailed(FeedbackMediaFailureKind.IO)
                )
            } finally {
                _uiState.update { it.copy(isProcessingMedia = false) }
            }
        }
    }

    fun clearScreenshot() {
        if (_uiState.value.isBusy) return
        val current = _uiState.value.screenshot
        clearPersistedScreenshot()
        _uiState.update { it.copy(screenshot = null) }
        viewModelScope.launch { mediaProcessor.delete(current?.path) }
    }

    fun submit() {
        if (_uiState.value.isBusy) return

        val current = _uiState.value
        val categoryError = current.category.isBlank()
        val messageError = current.message.trim().length < MIN_MESSAGE_LENGTH
        val emailError = current.email.isNotBlank() &&
            !Patterns.EMAIL_ADDRESS.matcher(current.email.trim()).matches()

        if (categoryError || messageError || emailError) {
            _uiState.update {
                it.copy(
                    categoryError = categoryError,
                    messageError = messageError,
                    emailError = emailError
                )
            }
            return
        }

        // Lock synchronously before scheduling work so double taps and media deletion cannot race upload.
        _uiState.update { it.copy(isSubmitting = true) }
        submitJob = viewModelScope.launch {
            try {
                val state = _uiState.value
                val result = try {
                    submissionUseCase.submit(
                        request = FeedbackSubmissionRequest(
                            category = state.category.trim(),
                            email = state.email.trim(),
                            message = state.message.trim(),
                            appVersion = appVersionProvider(),
                            localeTag = localeTagProvider()
                        ),
                        screenshotFile = state.screenshot?.file
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    FeedbackSubmissionResult.Failure(
                        FeedbackSubmissionFailure(
                            kind = FeedbackSubmissionFailureKind.GENERIC,
                            cause = error
                        )
                    )
                }

                when (result) {
                    is FeedbackSubmissionResult.Success -> {
                        val submittedMedia = _uiState.value.screenshot
                        resetFormState()
                        try {
                            mediaProcessor.delete(submittedMedia?.path)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Throwable) {
                            // Expiry cleanup is the deterministic fallback for a local delete failure.
                        }
                        eventChannel.send(FeedbackUiEvent.SubmissionSucceeded)
                    }
                    is FeedbackSubmissionResult.Failure -> {
                        eventChannel.send(FeedbackUiEvent.SubmissionFailed(result.failure.kind))
                    }
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun persistScreenshot(media: ProcessedFeedbackMedia) {
        savedStateHandle[KEY_SCREENSHOT_PATH] = media.path
        savedStateHandle[KEY_SCREENSHOT_NAME] = media.displayName
        savedStateHandle[KEY_SCREENSHOT_WIDTH] = media.width
        savedStateHandle[KEY_SCREENSHOT_HEIGHT] = media.height
        savedStateHandle[KEY_SCREENSHOT_BYTES] = media.byteCount
    }

    private fun clearPersistedScreenshot() {
        savedStateHandle.remove<String>(KEY_SCREENSHOT_PATH)
        savedStateHandle.remove<String>(KEY_SCREENSHOT_NAME)
        savedStateHandle.remove<Int>(KEY_SCREENSHOT_WIDTH)
        savedStateHandle.remove<Int>(KEY_SCREENSHOT_HEIGHT)
        savedStateHandle.remove<Long>(KEY_SCREENSHOT_BYTES)
    }

    private fun resetFormState() {
        savedStateHandle[KEY_CATEGORY] = ""
        savedStateHandle[KEY_EMAIL] = ""
        savedStateHandle[KEY_MESSAGE] = ""
        clearPersistedScreenshot()
        _uiState.value = FeedbackUiState()
    }

    override fun onCleared() {
        val submissionWasInFlight = _uiState.value.isSubmitting
        mediaJob?.cancel()
        submitJob?.cancel()
        if (!submissionWasInFlight) {
            _uiState.value.screenshot?.file?.takeIf { it.exists() }?.delete()
        }
        super.onCleared()
    }

    companion object {
        private const val MIN_MESSAGE_LENGTH = 10
        private const val KEY_CATEGORY = "feedback.category"
        private const val KEY_EMAIL = "feedback.email"
        private const val KEY_MESSAGE = "feedback.message"
        private const val KEY_SCREENSHOT_PATH = "feedback.screenshot.path"
        private const val KEY_SCREENSHOT_NAME = "feedback.screenshot.name"
        private const val KEY_SCREENSHOT_WIDTH = "feedback.screenshot.width"
        private const val KEY_SCREENSHOT_HEIGHT = "feedback.screenshot.height"
        private const val KEY_SCREENSHOT_BYTES = "feedback.screenshot.bytes"

        fun factory(
            submissionUseCase: FeedbackSubmissionUseCase,
            mediaProcessor: FeedbackMediaProcessor,
            appVersionProvider: () -> String = { BuildConfig.VERSION_NAME },
            localeTagProvider: () -> String = { Locale.getDefault().toLanguageTag() }
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    require(modelClass == FeedbackViewModel::class.java) {
                        "Unsupported ViewModel: ${modelClass.name}"
                    }
                    val viewModel = FeedbackViewModel(
                        savedStateHandle = extras.createSavedStateHandle(),
                        submissionUseCase = submissionUseCase,
                        mediaProcessor = mediaProcessor,
                        appVersionProvider = appVersionProvider,
                        localeTagProvider = localeTagProvider
                    )
                    @Suppress("UNCHECKED_CAST")
                    return viewModel as T
                }
            }
        }
    }
}

data class FeedbackUiState(
    val category: String = "",
    val email: String = "",
    val message: String = "",
    val screenshot: ProcessedFeedbackMedia? = null,
    val isProcessingMedia: Boolean = false,
    val isSubmitting: Boolean = false,
    val categoryError: Boolean = false,
    val emailError: Boolean = false,
    val messageError: Boolean = false
) {
    val isBusy: Boolean
        get() = isProcessingMedia || isSubmitting
}

sealed interface FeedbackUiEvent {
    data object ScreenshotSelected : FeedbackUiEvent
    data object SubmissionSucceeded : FeedbackUiEvent

    data class MediaProcessingFailed(
        val kind: FeedbackMediaFailureKind
    ) : FeedbackUiEvent

    data class SubmissionFailed(
        val kind: FeedbackSubmissionFailureKind
    ) : FeedbackUiEvent
}
