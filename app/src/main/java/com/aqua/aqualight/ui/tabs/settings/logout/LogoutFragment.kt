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
import com.aqua.aqualight.ui.auth.security.ReAuthenticateFragment
import com.aqua.aqualight.ui.auth.security.ReAuthManager
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

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

    // ---------------------------------------------------------
    // ON CREATE VIEW
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // ON VIEW CREATED
    // ---------------------------------------------------------

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )

        setupNavigation()

        setupButtons()
    }

    // ---------------------------------------------------------
    // NAVIGATION
    // ---------------------------------------------------------

    private fun setupNavigation() {

        binding.appHeader.setupAquaHeader(
            AquaHeaderConfig(
                title = getString(R.string.settings_about_title),
                showBackButton = true,
                onBackClick = {
                    findNavController().popBackStack()
                }
            )
        )

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

    // ---------------------------------------------------------
    // BUTTONS
    // ---------------------------------------------------------

    private fun setupButtons() {

        binding.btnLogout.setOnClickListener {

            showLogoutDialog()
        }

        binding.btnDeleteAccount.setOnClickListener {

            showDeleteAccountDialog()
        }
    }

    // ---------------------------------------------------------
    // LOGOUT DIALOG
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // LOGOUT
    // ---------------------------------------------------------

    private fun performLogout() {

        baseActivity?.showLoading(true)

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

                baseActivity?.showLoading(false)
            }
        }
    }

    // ---------------------------------------------------------
    // DELETE ACCOUNT DIALOG
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // DELETE ACCOUNT
    // ---------------------------------------------------------

    private fun performDeleteAccount() {

        when {

            // Google user
            reAuthManager.isGoogleUser() -> {

                navigateToReAuth()
            }

            // Email/password user
            reAuthManager.isPasswordUser() -> {

                navigateToReAuth()
            }

            // Unsupported provider
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

    // ---------------------------------------------------------
    // REAUTH NAVIGATION
    // ---------------------------------------------------------

    private fun navigateToReAuth() {

        val bundle =
        Bundle().apply {

            putString(
                ReAuthenticateFragment.ARG_ACTION,
                ReAuthenticateFragment.ACTION_DELETE_ACCOUNT
            )
        }

        findNavController().navigate(
            R.id.reAuthenticateFragment,
            bundle
        )
    }

    // ---------------------------------------------------------
    // NAVIGATE LOGIN
    // ---------------------------------------------------------

    private fun navigateToLogin() {

        val rootNav =
        (
            requireActivity()
            .supportFragmentManager
            .findFragmentById(R.id.nav_host)
            as NavHostFragment
        ).navController

        val opts =
        navOptions {

            popUpTo(R.id.nav_app) {
                inclusive = true
            }

            launchSingleTop = true
        }

        rootNav.navigate(
            R.id.authContainerFragment,
            null,
            opts
        )
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }
}