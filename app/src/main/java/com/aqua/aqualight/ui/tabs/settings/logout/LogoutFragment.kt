package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.navigation.RootNavigator
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.auth.LogoutManager
import com.aqua.aqualight.databinding.FragmentLogoutBinding
import com.aqua.aqualight.ui.auth.security.ReAuthManager
import com.aqua.aqualight.ui.auth.security.ReAuthenticateFragment
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class LogoutFragment :
    Fragment(R.layout.fragment_logout) {

    private var _binding: FragmentLogoutBinding? = null
    private val binding get() = _binding!!

    private val reAuthManager by lazy {
        ReAuthManager()
    }

    private val logoutManager by lazy {
        LogoutManager.create(requireContext())
    }

    private val baseActivity
        get() = activity as? BaseActivity

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
        setupNavigationRows()
        setupButtons()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
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
        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = getString(
                R.string.logout_dialog_title
            ),
            message = getString(
                R.string.logout_dialog_message
            ),
            onConfirm = {
                performLogout()
            }
        )
    }

    private fun performLogout() {
        baseActivity?.showLoading(
            true
        )

        binding.btnLogout.isEnabled =
            false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = logoutManager.logout()

                if (result.hasBlockingError) {
                    DialogManager.showInfoDialog(
                        requireContext(),
                        DialogType.ERROR,
                        title = getString(
                            R.string.logout_dialog_title
                        ),
                        message = result.preferenceCleanupError
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

                baseActivity?.showLoading(
                    false
                )
            }
        }
    }

    private fun showDeleteAccountDialog() {
        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.ERROR,
            title = getString(
                R.string.delete_account_dialog_title
            ),
            message = getString(
                R.string.delete_account_dialog_message
            ),
            onConfirm = {
                performDeleteAccount()
            }
        )
    }

    private fun performDeleteAccount() {
        when {
            reAuthManager.isGoogleUser() -> {
                navigateToReAuthForDeleteAccount()
            }

            reAuthManager.isPasswordUser() -> {
                navigateToReAuthForDeleteAccount()
            }

            else -> {
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
}