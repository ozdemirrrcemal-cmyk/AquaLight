package com.aqua.aqualight.ui.auth.security

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.auth.AccountProvider
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentReAuthenticateBinding
import com.aqua.aqualight.platform.auth.GoogleIdentityTokenResult
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.navigation.RootNavigator
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class ReAuthenticateFragment :
    Fragment(R.layout.fragment_re_authenticate) {

    private val args: ReAuthenticateFragmentArgs by navArgs()

    companion object {
        const val ARG_ACTION = "arg_action"
        const val ACTION_DELETE_ACCOUNT = "delete_account"
        const val ACTION_CHANGE_PASSWORD = "change_password"
        const val ACTION_CHANGE_EMAIL = "change_email"
    }

    private var _binding: FragmentReAuthenticateBinding? = null
    private val binding get() = _binding!!

    private val appContainer by lazy {
        requireContext().requireAppContainer()
    }

    private val accountSecurityOperations
        get() = appContainer.accountSecurityOperations

    private val googleIdentityClient
        get() = appContainer.googleIdentityClient

    private val baseActivity
        get() = activity as? BaseActivity

    private var isLoading = false
    private var currentAction = ACTION_DELETE_ACCOUNT

    private val googleLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                setFragmentGlobalLoading(false)
                setLoadingState(false)
                return@registerForActivityResult
            }

            when (
                val tokenResult = googleIdentityClient.parseIdToken(result.data)
            ) {
                is GoogleIdentityTokenResult.Success -> {
                    reauthenticateWithGoogleToken(
                        tokenResult.idToken
                    )
                }

                GoogleIdentityTokenResult.MissingToken -> {
                    setFragmentGlobalLoading(false)
                    setLoadingState(false)
                    showGoogleVerificationFailure()
                }

                is GoogleIdentityTokenResult.Failure -> {
                    setFragmentGlobalLoading(false)
                    setLoadingState(false)

                    DialogManager.showInfoDialog(
                        requireContext(),
                        DialogType.ERROR,
                        title = "Google Error",
                        message = tokenResult.error.localizedMessage
                            ?: getString(
                                R.string.re_auth_unknown_error
                            )
                    )
                }
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentReAuthenticateBinding.bind(view)

        currentAction =
            args.argAction

        setupHeader()
        setupUi()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun setupUi() {
        when (accountSecurityOperations.provider()) {
            AccountProvider.GOOGLE -> setupGoogleUi()
            AccountProvider.PASSWORD -> setupPasswordUi()
            AccountProvider.UNKNOWN -> {
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = getString(
                        R.string.auth_provider_error_title
                    ),
                    message = getString(
                        R.string.auth_provider_error_message
                    ),
                    onDismiss = {
                        findNavController().popBackStack()
                    }
                )
            }
        }
    }

    private fun setupGoogleUi() {
        binding.ivGoogle.visibility = View.VISIBLE
        binding.tvTitle.text =
            getString(R.string.re_auth_google_title)
        binding.tvDescription.text =
            getString(R.string.re_auth_google_description)
        binding.passwordLayout.visibility = View.GONE
        binding.btnContinue.text =
            getString(R.string.re_auth_continue_google)
        binding.btnContinue.setOnClickListener {
            startGoogleReAuthentication()
        }
    }

    private fun setupPasswordUi() {
        binding.ivGoogle.visibility = View.GONE
        binding.tvTitle.text =
            getString(R.string.re_auth_confirm_password)
        binding.tvDescription.text =
            getString(R.string.re_auth_password_description)
        binding.passwordLayout.visibility = View.VISIBLE
        binding.btnContinue.text =
            getString(R.string.re_auth_continue)
        binding.etPassword.requestFocus()

        binding.etPassword.doOnTextChanged { _, _, _, _ ->
            binding.passwordLayout.error = null
        }

        binding.etPassword.setOnEditorActionListener { _, _, _ ->
            validatePasswordReAuthentication()
            true
        }

        binding.btnContinue.setOnClickListener {
            validatePasswordReAuthentication()
        }
    }

    private fun startGoogleReAuthentication() {
        if (isLoading) {
            return
        }

        setFragmentGlobalLoading(true)
        setLoadingState(true)

        viewLifecycleOwner.lifecycleScope.launch {
            // Existing behavior launches account selection even when clearing the
            // previous Google session reports a recoverable failure.
            runCatching {
                googleIdentityClient.clearPreviousSession()
            }

            googleLauncher.launch(
                googleIdentityClient.signInIntent()
            )
        }
    }

    private fun reauthenticateWithGoogleToken(
        idToken: String
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                accountSecurityOperations.reauthenticateWithGoogleToken(
                    idToken
                )
            }.onSuccess {
                handleAuthenticatedAction()
            }.onFailure {
                setFragmentGlobalLoading(false)
                setLoadingState(false)
                showGoogleVerificationFailure()
            }
        }
    }

    private fun showGoogleVerificationFailure() {
        DialogManager.showInfoDialog(
            requireContext(),
            DialogType.ERROR,
            title = getString(
                R.string.re_auth_verification_failed_title
            ),
            message = getString(
                R.string.re_auth_google_wrong_account
            )
        )
    }

    private fun validatePasswordReAuthentication() {
        if (isLoading) {
            return
        }

        val password =
            binding.etPassword.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (password.isBlank()) {
            binding.passwordLayout.error =
                getString(
                    R.string.re_auth_password_required
                )
            shakeView(binding.passwordLayout)
            return
        }

        setFragmentGlobalLoading(true)
        setLoadingState(true)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                accountSecurityOperations.reauthenticateWithPassword(
                    password
                )
            }.onSuccess {
                handleAuthenticatedAction()
            }.onFailure {
                setFragmentGlobalLoading(false)
                setLoadingState(false)
                binding.passwordLayout.error =
                    getString(
                        R.string.re_auth_wrong_password
                    )
                shakeView(binding.passwordLayout)
            }
        }
    }

    private fun handleAuthenticatedAction() {
        when (currentAction) {
            ACTION_DELETE_ACCOUNT -> {
                deleteAccount()
            }

            ACTION_CHANGE_PASSWORD,
            ACTION_CHANGE_EMAIL -> {
                setFragmentGlobalLoading(false)
                setLoadingState(false)

                findNavController()
                    .previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(
                        "reauth_success",
                        true
                    )

                findNavController().popBackStack()
            }
        }
    }

    private fun deleteAccount() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result =
                accountSecurityOperations.deleteCurrentAccount()

            setFragmentGlobalLoading(false)
            setLoadingState(false)

            val accountDeleteError =
                result.accountDeleteError

            if (accountDeleteError != null) {
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = getString(
                        R.string.re_auth_delete_failed_title
                    ),
                    message = getAccountDeleteFailureMessage(
                        accountDeleteError
                    )
                )
                return@launch
            }

            if (result.hasPostDeleteCleanupErrors) {
                result.cleanupErrors.forEach { error ->
                    error.printStackTrace()
                }

                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.WARNING,
                    title = getString(
                        R.string.re_auth_delete_cleanup_warning_title
                    ),
                    message = getString(
                        R.string.re_auth_delete_cleanup_warning_message
                    ),
                    onDismiss = ::navigateToLogin
                )
                return@launch
            }

            baseActivity?.showSnackBar(
                getString(
                    R.string.re_auth_delete_success_message
                )
            )

            binding.root.postDelayed(
                {
                    navigateToLogin()
                },
                500
            )
        }
    }

    private fun getAccountDeleteFailureMessage(
        error: Throwable
    ): String {
        return when {
            error.localizedMessage
                ?.contains(
                    "requires recent authentication",
                    ignoreCase = true
                ) == true -> {
                getString(
                    R.string.re_auth_session_expired
                )
            }

            else -> {
                getString(
                    R.string.re_auth_delete_failed_message
                )
            }
        }
    }

    private fun setLoadingState(
        loading: Boolean
    ) {
        isLoading = loading
        binding.btnContinue.isEnabled = !loading
        binding.btnContinue.alpha =
            if (loading) 0.6f else 1f
        binding.btnContinue.text =
            if (loading) {
                getString(R.string.loading)
            } else if (binding.ivGoogle.visibility == View.VISIBLE) {
                getString(
                    R.string.re_auth_continue_google
                )
            } else {
                getString(
                    R.string.re_auth_continue
                )
            }
    }

    private fun shakeView(
        view: View
    ) {
        view.animate()
            .translationX(20f)
            .setDuration(50)
            .withEndAction {
                view.animate()
                    .translationX(-20f)
                    .setDuration(50)
                    .withEndAction {
                        view.animate()
                            .translationX(10f)
                            .setDuration(50)
                            .withEndAction {
                                view.animate()
                                    .translationX(0f)
                                    .duration = 50
                            }
                    }
            }
    }

    private fun navigateToLogin() {
        RootNavigator.openAuthGraph(this)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
