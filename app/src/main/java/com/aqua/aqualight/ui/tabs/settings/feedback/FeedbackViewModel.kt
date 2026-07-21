package com.aqua.aqualight.ui.tabs.settings.feedback

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
import java.util.Locale
import kotlinx.coroutines.CancellationException
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
    private val appVersionProvider: () -> String = { BuildConfig.VERSION_NAME },
    private val localeTagProvider: () -> String = { Locale.getDefault().toLanguageTag() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FeedbackUiState(
            category = savedStateHandle.get<String>(KEY_CATEGORY).orEmpty(),
            email = savedStateHandle.get<String>(KEY_EMAIL).orEmpty(),
            message = savedStateHandle.get<String>(KEY_MESSAGE).orEmpty()
        )
    )
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<FeedbackUiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

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

    fun submit() {
        if (_uiState.value.isSubmitting) return

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

        // Lock synchronously so rapid taps cannot enqueue duplicate submissions.
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val result = try {
                    submissionUseCase.submit(
                        FeedbackSubmissionRequest(
                            category = state.category.trim(),
                            email = state.email.trim(),
                            message = state.message.trim(),
                            appVersion = appVersionProvider(),
                            localeTag = localeTagProvider()
                        )
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
                        resetFormState()
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

    private fun resetFormState() {
        savedStateHandle[KEY_CATEGORY] = ""
        savedStateHandle[KEY_EMAIL] = ""
        savedStateHandle[KEY_MESSAGE] = ""
        _uiState.value = FeedbackUiState()
    }

    companion object {
        private const val MIN_MESSAGE_LENGTH = 10
        private const val KEY_CATEGORY = "feedback.category"
        private const val KEY_EMAIL = "feedback.email"
        private const val KEY_MESSAGE = "feedback.message"

        fun factory(
            submissionUseCase: FeedbackSubmissionUseCase,
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
    val isSubmitting: Boolean = false,
    val categoryError: Boolean = false,
    val emailError: Boolean = false,
    val messageError: Boolean = false
)

sealed interface FeedbackUiEvent {
    data object SubmissionSucceeded : FeedbackUiEvent

    data class SubmissionFailed(
        val kind: FeedbackSubmissionFailureKind
    ) : FeedbackUiEvent
}
