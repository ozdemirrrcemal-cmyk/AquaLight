package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentLogoutBinding
import com.aqua.aqualight.ui.auth.security.ReAuthManager
import com.aqua.aqualight.ui.auth.security.ReAuthenticateFragment
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class LogoutFragment :
    Fragment(R.layout.fragment_logout) {

    private var _binding: FragmentLogoutBinding? = null
    private val binding get() = _binding!!

    private val auth
        get() = FirebaseAuth.getInstance()

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    private val reAuthManager by lazy {
        ReAuthManager()
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
                R.id.action_logoutFragment_to_reAuthenticateFragment
            )
        }

        binding.rowChangeEmail.setOnClickListener {
            findNavController().navigate(
                R.id.action_logoutFragment_to_changeEmailFragment
            )
        }

        binding.rowSecuritySettings.setOnClickListener {
            findNavController().navigate(
                R.id.action_logoutFragment_to_securitySettingsFragment
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
                auth.signOut()

                userPrefs.logout()

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
        val bundle =
            Bundle().apply {
                putString(
                    ReAuthenticateFragment.ARG_ACTION,
                    ReAuthenticateFragment.ACTION_DELETE_ACCOUNT
                )
            }

        findNavController().navigate(
            R.id.action_logoutFragment_to_reAuthenticateFragment,
            bundle
        )
    }

    private fun navigateToLogin() {
        val rootNav =
            (
                requireActivity()
                    .supportFragmentManager
                    .findFragmentById(R.id.nav_host) as NavHostFragment
                ).navController

        val options =
            navOptions {
                popUpTo(R.id.nav_app) {
                    inclusive =
                        true
                }

                launchSingleTop =
                    true
            }

        rootNav.navigate(
            R.id.authContainerFragment,
            null,
            options
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }
}