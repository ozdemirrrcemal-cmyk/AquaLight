package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.application.auth.AccountProvider
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentLogoutBinding
import com.aqua.aqualight.ui.auth.security.ReAuthenticateFragment
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.navigation.RootNavigator
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class LogoutFragment :
    Fragment(R.layout.fragment_logout) {

    private var _binding: FragmentLogoutBinding? = null
    private val binding get() = _binding!!

    private val appContainer by lazy {
        requireContext().requireAppContainer()
    }

    private val sessionExitOperations
        get() = appContainer.sessionExitOperations

    private val accountSecurityOperations
        get() = appContainer.accountSecurityOperations

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding =
            FragmentLogoutBinding.inflate(
                inflater,
                container,
                false
            )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        setupHeader()
        setupFeedbackResultListener()
        setupNavigationRows()
        setupButtons()
    }


    private fun setupFeedbackResultListener() {
        childFragmentManager.setFragmentResultListener(
            LOGOUT_CONFIRM_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(FeedbackBottomSheet.RESULT_KEY) !=
                FeedbackBottomSheet.RESULT_PRIMARY
            ) return@setFragmentResultListener
            when (result.getString(FeedbackBottomSheet.RESULT_ACTION_ID)) {
                ACTION_LOGOUT -> performLogout()
                ACTION_DELETE_ACCOUNT -> performDeleteAccount()
            }
        }
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_logout)
            )
        )
    }

    private fun setupNavigationRows() {
        binding.rowChangePassword.setOnClickListener {
            findNavController().navigate(
                LogoutFragmentDirections.actionLogoutFragmentToChangePasswordFragment()
            )
        }

        binding.rowChangeEmail.setOnClickListener {
            findNavController().navigate(
                LogoutFragmentDirections.actionLogoutFragmentToChangeEmailFragment()
            )
        }

        binding.rowSecuritySettings.setOnClickListener {
            findNavController().navigate(
                LogoutFragmentDirections.actionLogoutFragmentToSecuritySettingsFragment()
            )
        }
    }

    private fun setupButtons() {
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        binding.btnDeleteAccount.setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    private fun showLogoutDialog() {
        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.logout_dialog_title),
            message = getString(R.string.logout_dialog_message),
            primaryText = getString(R.string.confirm),
            cancelText = getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.WARNING,
            requestKey = LOGOUT_CONFIRM_REQUEST_KEY,
            actionId = ACTION_LOGOUT
        )
    }

    private fun performLogout() {
        setFragmentGlobalLoading(
            true
        )

        binding.btnLogout.isEnabled =
            false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = sessionExitOperations.logout()

                if (result.hasBlockingError) {
                    DialogManager.showInfoDialog(
                        requireContext(),
                        DialogType.ERROR,
                        title = getString(
                            R.string.logout_dialog_title
                        ),
                        message = result.blockingError
                            ?.localizedMessage
                            ?: getString(
                                R.string.auth_provider_error_message
                            )
                    )
                    return@launch
                }

                navigateToLogin()
            } finally {
                binding.btnLogout.isEnabled =
                    true

                setFragmentGlobalLoading(
                    false
                )
            }
        }
    }

    private fun showDeleteAccountDialog() {
        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.delete_account_dialog_title),
            message = getString(R.string.delete_account_dialog_message),
            primaryText = getString(R.string.confirm),
            cancelText = getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.DANGER,
            requestKey = LOGOUT_CONFIRM_REQUEST_KEY,
            actionId = ACTION_DELETE_ACCOUNT
        )
    }

    private fun performDeleteAccount() {
        when (accountSecurityOperations.provider()) {
            AccountProvider.GOOGLE,
            AccountProvider.PASSWORD -> {
                navigateToReAuthForDeleteAccount()
            }

            AccountProvider.UNKNOWN -> {
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = getString(
                        R.string.auth_provider_error_title
                    ),
                    message = getString(
                        R.string.auth_provider_error_message
                    )
                )
            }
        }
    }

    private fun navigateToReAuthForDeleteAccount() {
        findNavController().navigate(
            LogoutFragmentDirections.actionLogoutFragmentToReAuthenticateFragment(
                argAction = ReAuthenticateFragment.ACTION_DELETE_ACCOUNT
            )
        )
    }

    private fun navigateToLogin() {
        RootNavigator.openAuthGraph(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }

    private companion object {
        const val LOGOUT_CONFIRM_REQUEST_KEY = "logout_confirm_result"
        const val ACTION_LOGOUT = "logout"
        const val ACTION_DELETE_ACCOUNT = "delete_account"
    }
}
