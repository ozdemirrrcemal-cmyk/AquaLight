package com.aqua.aqualight.ui.tabs.settings.feedback

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailure
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionPolicy
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.application.feedback.FeedbackSubmissionResult
import com.aqua.aqualight.application.feedback.FeedbackSubmissionUseCase
import java.util.Locale
import java.util.UUID
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
    private val localeTagProvider: () -> String = { Locale.getDefault().toLanguageTag() },
    private val submissionIdProvider: () -> String = { UUID.randomUUID().toString() }
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
        if (value != _uiState.value.category) invalidateSubmissionIdentity()
        savedStateHandle[KEY_CATEGORY] = value
        _uiState.update { it.copy(category = value, categoryError = false) }
    }

    fun updateEmail(value: String) {
        if (value != _uiState.value.email) invalidateSubmissionIdentity()
        savedStateHandle[KEY_EMAIL] = value
        _uiState.update { it.copy(email = value, emailError = false) }
    }

    fun updateMessage(value: String) {
        if (value != _uiState.value.message) invalidateSubmissionIdentity()
        savedStateHandle[KEY_MESSAGE] = value
        _uiState.update {
            it.copy(
                message = value,
                messageError = false,
                messageTooLongError = false
            )
        }
    }

    fun submit() {
        if (_uiState.value.isSubmitting) return

        val current = _uiState.value
        val normalizedCategory = current.category.trim()
        val normalizedEmail = current.email.trim()
        val normalizedMessage = current.message.trim()
        val categoryError = normalizedCategory.isEmpty() ||
            normalizedCategory.length > FeedbackSubmissionPolicy.CATEGORY_MAX_LENGTH
        val messageTooShort =
            normalizedMessage.length < FeedbackSubmissionPolicy.MESSAGE_MIN_LENGTH
        val messageTooLong =
            normalizedMessage.length > FeedbackSubmissionPolicy.MESSAGE_MAX_LENGTH
        val messageError = messageTooShort || messageTooLong
        val emailError = !FeedbackSubmissionPolicy.isEmailValid(normalizedEmail)

        if (categoryError || messageError || emailError) {
            _uiState.update {
                it.copy(
                    categoryError = categoryError,
                    messageError = messageError,
                    messageTooLongError = messageTooLong,
                    emailError = emailError
                )
            }
            return
        }

        val request = FeedbackSubmissionRequest(
            submissionId = submissionIdentity(),
            category = normalizedCategory,
            email = normalizedEmail,
            message = normalizedMessage,
            appVersion = appVersionProvider(),
            localeTag = localeTagProvider()
        )

        // Lock synchronously so rapid taps cannot enqueue duplicate submissions.
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val event = try {
                val result = try {
                    submissionUseCase.submit(request)
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
                        FeedbackUiEvent.SubmissionSucceeded
                    }
                    is FeedbackSubmissionResult.Failure -> {
                        FeedbackUiEvent.SubmissionFailed(result.failure.kind)
                    }
                }
            } finally {
                // Clear loading before the event so the snackbar cannot render under the overlay.
                _uiState.update { it.copy(isSubmitting = false) }
            }
            eventChannel.send(event)
        }
    }

    private fun submissionIdentity(): String {
        val restored = savedStateHandle.get<String>(KEY_SUBMISSION_ID)
            ?.takeIf(FeedbackSubmissionPolicy::isSubmissionIdValid)
        if (restored != null) return restored
        return submissionIdProvider().also { generated ->
            check(FeedbackSubmissionPolicy.isSubmissionIdValid(generated)) {
                "Feedback submission id provider returned an invalid UUID."
            }
            savedStateHandle[KEY_SUBMISSION_ID] = generated
        }
    }

    private fun invalidateSubmissionIdentity() {
        savedStateHandle.remove<String>(KEY_SUBMISSION_ID)
    }

    private fun resetFormState() {
        savedStateHandle[KEY_CATEGORY] = ""
        savedStateHandle[KEY_EMAIL] = ""
        savedStateHandle[KEY_MESSAGE] = ""
        invalidateSubmissionIdentity()
        _uiState.value = FeedbackUiState()
    }

    companion object {
        private const val KEY_CATEGORY = "feedback.category"
        private const val KEY_EMAIL = "feedback.email"
        private const val KEY_MESSAGE = "feedback.message"
        private const val KEY_SUBMISSION_ID = "feedback.submissionId"

        fun factory(
            submissionUseCase: FeedbackSubmissionUseCase,
            appVersionProvider: () -> String = { BuildConfig.VERSION_NAME },
            localeTagProvider: () -> String = { Locale.getDefault().toLanguageTag() },
            submissionIdProvider: () -> String = { UUID.randomUUID().toString() }
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
                        localeTagProvider = localeTagProvider,
                        submissionIdProvider = submissionIdProvider
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
    val messageError: Boolean = false,
    val messageTooLongError: Boolean = false
)

sealed interface FeedbackUiEvent {
    data object SubmissionSucceeded : FeedbackUiEvent

    data class SubmissionFailed(
        val kind: FeedbackSubmissionFailureKind
    ) : FeedbackUiEvent
}
