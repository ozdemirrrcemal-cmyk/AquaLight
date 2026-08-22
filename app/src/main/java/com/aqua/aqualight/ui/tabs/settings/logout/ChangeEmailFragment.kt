package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentChangeEmailBinding
import com.aqua.aqualight.ui.auth.state.AuthActionState
import com.aqua.aqualight.ui.auth.viewmodel.ChangeEmailViewModel
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.navigation.RootNavigator
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class ChangeEmailFragment :
    Fragment(R.layout.fragment_change_email) {

    private var _binding: FragmentChangeEmailBinding? = null
    private val binding get() = _binding!!

    private val appContainer by lazy {
        requireContext().requireAppContainer()
    }

    private val viewModel: ChangeEmailViewModel by viewModels {
        appContainer.authViewModelFactory
    }

    private val sessionExitOperations
        get() = appContainer.sessionExitOperations

    private val baseActivity
        get() = activity as? BaseActivity

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentChangeEmailBinding.bind(view)

        setupHeader()
        setupFeedbackResultListener()
        setupScreen()
        observeState()
    }


    private fun setupFeedbackResultListener() {
        childFragmentManager.setFragmentResultListener(
            CHANGE_EMAIL_FEEDBACK_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            when (result.getString(FeedbackBottomSheet.RESULT_ACTION_ID)) {
                ACTION_CLOSE -> findNavController().popBackStack()
                ACTION_FINISH_EMAIL_CHANGE -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        sessionExitOperations.cleanupAfterSensitiveAction()
                        viewModel.resetState()
                        navigateToLoginRoot()
                    }
                }
            }
        }
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_change_email)
            )
        )
    }

    private fun setupScreen() {
        if (viewModel.currentEmail().isBlank()) {
            FeedbackBottomSheet.show(
                fragmentManager = childFragmentManager,
                title = getString(R.string.change_email_user_not_found_title),
                message = getString(R.string.change_email_user_not_found_message),
                primaryText = getString(R.string.ok),
                cancelText = null,
                tone = FeedbackBottomSheet.FeedbackTone.ERROR,
                requestKey = CHANGE_EMAIL_FEEDBACK_REQUEST_KEY,
                actionId = ACTION_CLOSE
            )

            return
        }

        if (viewModel.isGoogleUser()) {
            showGoogleOnlyInfo()
            return
        }

        binding.etCurrentEmail.setText(
            viewModel.currentEmail()
        )

        binding.btnSaveEmail.setOnClickListener {
            attemptEmailChange()
        }
    }

    private fun showGoogleOnlyInfo() {
        binding.etCurrentEmail.setText(
            viewModel.currentEmail()
        )

        binding.inputLayoutCurrentEmail.isEnabled =
            false

        binding.inputLayoutNewEmail.visibility =
            View.GONE

        binding.inputLayoutPassword.visibility =
            View.GONE

        binding.btnSaveEmail.visibility =
            View.GONE

        binding.inputLayoutCurrentEmail.helperText =
            getString(
                R.string.change_email_google_only_info
            )
    }

    private fun attemptEmailChange() {
        val currentEmail =
            binding.etCurrentEmail.text
                .toString()
                .trim()

        val newEmail =
            binding.etNewEmail.text
                .toString()
                .trim()

        val password =
            binding.etPassword.text
                .toString()
                .trim()

        clearErrors()

        if (currentEmail.isEmpty()) {
            binding.inputLayoutCurrentEmail.error =
                getString(
                    R.string.change_email_error_current_required
                )

            return
        }

        if (newEmail.isEmpty()) {
            binding.inputLayoutNewEmail.error =
                getString(
                    R.string.change_email_error_new_required
                )

            return
        }

        if (password.isEmpty()) {
            binding.inputLayoutPassword.error =
                getString(
                    R.string.change_email_error_password_required
                )

            return
        }

        if (
            !Patterns.EMAIL_ADDRESS
                .matcher(newEmail)
                .matches()
        ) {
            binding.inputLayoutNewEmail.error =
                getString(
                    R.string.change_email_error_invalid_format
                )

            return
        }

        if (
            currentEmail.equals(
                newEmail,
                ignoreCase = true
            )
        ) {
            binding.inputLayoutNewEmail.error =
                getString(
                    R.string.change_email_same_email
                )

            return
        }

        viewModel.requestEmailChange(
            currentEmail = currentEmail,
            newEmail = newEmail,
            password = password
        )
    }

    private fun clearErrors() {
        binding.inputLayoutCurrentEmail.error =
            null

        binding.inputLayoutNewEmail.error =
            null

        binding.inputLayoutPassword.error =
            null

        binding.inputLayoutCurrentEmail.helperText =
            null
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.state.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(
        state: AuthActionState
    ) {
        val isLoading = state is AuthActionState.Loading

        setFragmentGlobalLoading(isLoading)
        binding.btnSaveEmail.isEnabled = !isLoading

        when (state) {
            is AuthActionState.EmailVerificationSent -> {
                FeedbackBottomSheet.show(
                    fragmentManager = childFragmentManager,
                    title = getString(R.string.change_email_verification_title),
                    message = getString(
                        R.string.change_email_verification_message,
                        state.newEmail
                    ),
                    primaryText = getString(R.string.ok),
                    cancelText = null,
                    tone = FeedbackBottomSheet.FeedbackTone.SUCCESS,
                    requestKey = CHANGE_EMAIL_FEEDBACK_REQUEST_KEY,
                    actionId = ACTION_FINISH_EMAIL_CHANGE
                )
            }

            is AuthActionState.Message -> {
                DialogManager.showInfoDialog(
                    requireContext(),
                    state.kind.toDialogType(),
                    title = state.title.resolve(requireContext()),
                    message = state.message.resolve(requireContext())
                )
                viewModel.resetState()
            }

            else -> Unit
        }
    }

    private fun AuthActionState.Kind.toDialogType(): DialogType {
        return when (this) {
            AuthActionState.Kind.WARNING -> DialogType.WARNING
            AuthActionState.Kind.ERROR -> DialogType.ERROR
            AuthActionState.Kind.SUCCESS -> DialogType.SUCCESS
        }
    }

    private fun navigateToLoginRoot() {
        RootNavigator.openAuthGraph(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }

    private companion object {
        const val CHANGE_EMAIL_FEEDBACK_REQUEST_KEY = "change_email_feedback_result"
        const val ACTION_CLOSE = "close"
        const val ACTION_FINISH_EMAIL_CHANGE = "finish_email_change"
    }
}
