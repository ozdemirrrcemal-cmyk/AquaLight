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
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentLogoutBinding
import com.aqua.aqualight.ui.auth.security.ReAuthManager
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class LogoutFragment : Fragment(R.layout.fragment_logout) {

    private var _binding: FragmentLogoutBinding? = null
    private val binding get() = _binding!!

    private val auth get() = FirebaseAuth.getInstance()

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    private val reAuthManager by lazy {
        ReAuthManager()
    }

    private val baseActivity get() = activity as? BaseActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentLogoutBinding.inflate(
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

        super.onViewCreated(view, savedInstanceState)

        setupNavigation()

        setupButtons()
    }

    // ---------------------------------------------------------
    // NAVIGATION
    // ---------------------------------------------------------

    private fun setupNavigation() {

        binding.btnBack.setOnClickListener {

            findNavController().popBackStack()
        }

        binding.rowChangePassword.setOnClickListener {

            findNavController().navigate(
                R.id.action_logoutFragment_to_changePasswordFragment
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
    // LOGOUT
    // ---------------------------------------------------------

    private fun showLogoutDialog() {

        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = "Logout",
            message = "Are you sure you want to log out of your account?",
            onConfirm = {
                performLogout()
            }
        )
    }

    private fun performLogout() {

        baseActivity?.showLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {

            try {

                auth.signOut()

                userPrefs.logout()

                navigateToLogin()

            } finally {

                baseActivity?.showLoading(false)
            }
        }
    }

    // ---------------------------------------------------------
    // DELETE ACCOUNT
    // ---------------------------------------------------------

    private fun showDeleteAccountDialog() {

        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.ERROR,
            title = "Delete Account",
            message = "Are you sure you want to permanently delete your account?\nThis action cannot be undone.",
            onConfirm = {
                performDeleteAccount()
            }
        )
    }

    private fun performDeleteAccount() {

        when {

            // -------------------------------------------------
            // GOOGLE USER
            // -------------------------------------------------

            reAuthManager.isGoogleUser() -> {

    findNavController().navigate(
        R.id.reAuthenticateFragment
    )
}

            // -------------------------------------------------
            // EMAIL/PASSWORD USER
            // -------------------------------------------------

            // -------------------------------------------------
// EMAIL/PASSWORD USER
// -------------------------------------------------

reAuthManager.isPasswordUser() -> {

    findNavController().navigate(
        R.id.reAuthenticateFragment
    )
}

            // -------------------------------------------------
            // UNKNOWN PROVIDER
            // -------------------------------------------------

            else -> {

                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = "Authentication Error",
                    message = "Unsupported authentication provider."
                )
            }
        }
    }

    // ---------------------------------------------------------
    // NAVIGATE LOGIN
    // ---------------------------------------------------------

    private fun navigateToLogin() {

        val rootNav =
            (requireActivity()
                .supportFragmentManager
                .findFragmentById(R.id.nav_host) as NavHostFragment)
                .navController

        val opts = navOptions {

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

        _binding = null

        super.onDestroyView()
    }
}