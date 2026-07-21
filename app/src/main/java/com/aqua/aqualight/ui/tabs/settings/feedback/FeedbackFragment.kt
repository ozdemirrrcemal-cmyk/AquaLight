package com.aqua.aqualight.ui.tabs.settings.feedback

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentFeedbackBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import kotlinx.coroutines.launch

class FeedbackFragment : Fragment(R.layout.fragment_feedback) {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FeedbackViewModel by viewModels {
        val container = requireContext().requireAppContainer()
        FeedbackViewModel.factory(
            submissionUseCase = container.feedbackSubmissionOperations
        )
    }

    private lateinit var originalButtonText: CharSequence
    private var suppressInputCallbacks = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFeedbackBinding.bind(view)
        originalButtonText = binding.btnSend.text

        setupHeader()
        setupCategoryDropdown()
        applyInitialFormState(viewModel.uiState.value)
        setupValidationWatchers()
        setupSendButton()
        setupLottie()
        observeViewModel()
    }

    private fun setupHeader() = with(binding) {
        appHeader.setupAquaHeader(fragment = this@FeedbackFragment)
        tvSubInfo.text = getString(R.string.feedback_subinfo)
        tvFooter.text = getString(R.string.feedback_footer)
    }

    private fun setupCategoryDropdown() = with(binding) {
        val categories = resources.getStringArray(R.array.feedback_categories).toList()
        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            R.layout.item_feedback_category,
            categories
        )
        autoCategory.setAdapter(adapter)
        autoCategory.setDropDownBackgroundDrawable(
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_dropdown_popup)
        )
        autoCategory.setOnClickListener { autoCategory.showDropDown() }
    }

    private fun applyInitialFormState(state: FeedbackUiState) {
        withInputCallbacksSuppressed {
            binding.autoCategory.setText(state.category, false)
            binding.etEmail.setText(state.email)
            binding.etMessage.setText(state.message)
        }
        renderState(state)
    }

    private fun setupValidationWatchers() = with(binding) {
        autoCategory.addTextChangedListener {
            if (!suppressInputCallbacks) {
                viewModel.updateCategory(it?.toString().orEmpty())
            }
        }
        etEmail.addTextChangedListener {
            if (!suppressInputCallbacks) {
                viewModel.updateEmail(it?.toString().orEmpty())
            }
        }
        etMessage.addTextChangedListener {
            if (!suppressInputCallbacks) {
                viewModel.updateMessage(it?.toString().orEmpty())
            }
        }
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener { viewModel.submit() }
    }

    private fun setupLottie() {
        binding.lottieSuccess.addAnimatorListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (_binding == null) return
                    withInputCallbacksSuppressed {
                        binding.etMessage.isEnabled = true
                        binding.etMessage.setText(viewModel.uiState.value.message)
                        binding.etMessage.clearFocus()
                        binding.inputLayoutMessage.hint = getString(R.string.feedback_hint_message)
                        binding.inputLayoutMessage.error = null
                        binding.lottieSuccess.isVisible = false
                    }
                }
            }
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect(::renderState)
                }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
    }

    private fun renderState(state: FeedbackUiState) = with(binding) {
        inputLayoutCategory.error = if (state.categoryError) {
            getString(R.string.feedback_error_category_required)
        } else {
            null
        }
        inputLayoutEmail.error = if (state.emailError) {
            getString(R.string.feedback_error_email_invalid)
        } else {
            null
        }
        inputLayoutMessage.error = if (state.messageError) {
            getString(R.string.feedback_error_message_length)
        } else {
            null
        }

        btnSend.isEnabled = !state.isSubmitting
        btnSend.text = if (state.isSubmitting) {
            getString(R.string.feedback_sending)
        } else {
            originalButtonText
        }
        progressBarSending.isVisible = state.isSubmitting
        setFragmentGlobalLoading(state.isSubmitting)
    }

    private fun handleEvent(event: FeedbackUiEvent) {
        when (event) {
            FeedbackUiEvent.SubmissionSucceeded -> {
                withInputCallbacksSuppressed {
                    binding.autoCategory.setText("", false)
                    binding.etEmail.setText("")
                    binding.etMessage.setText("")
                }
                showSuccessUI()
                showSnackBar(
                    getString(R.string.feedback_sent_success),
                    BaseActivity.SnackType.SUCCESS
                )
            }

            is FeedbackUiEvent.SubmissionFailed -> {
                val messageRes = when (event.kind) {
                    FeedbackSubmissionFailureKind.AUTHENTICATION ->
                        R.string.feedback_error_auth_required
                    FeedbackSubmissionFailureKind.VALIDATION ->
                        R.string.feedback_error_validation
                    FeedbackSubmissionFailureKind.PERSISTENCE,
                    FeedbackSubmissionFailureKind.GENERIC ->
                        R.string.feedback_error_generic
                }
                showSnackBar(getString(messageRes), BaseActivity.SnackType.ERROR)
            }
        }
    }

    private fun showSuccessUI() = with(binding) {
        withInputCallbacksSuppressed {
            inputLayoutMessage.hint = ""
            etMessage.isEnabled = false
            etMessage.setText(getString(R.string.feedback_success_text))
        }
        lottieSuccess.isVisible = true
        lottieSuccess.progress = 0f
        lottieSuccess.playAnimation()
    }

    private inline fun withInputCallbacksSuppressed(block: () -> Unit) {
        suppressInputCallbacks = true
        try {
            block()
        } finally {
            suppressInputCallbacks = false
        }
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType
    ) {
        (activity as? BaseActivity)?.showSnackBar(message, type)
    }

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }
}
