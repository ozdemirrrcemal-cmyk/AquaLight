package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentLogoutBinding
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import androidx.navigation.fragment.NavHostFragment

class LogoutFragment : Fragment(R.layout.fragment_logout) {

    private var _binding: FragmentLogoutBinding? = null
    private val binding get() = _binding!!

    private val auth get() = FirebaseAuth.getInstance()
    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }
    private val baseActivity get() = activity as? BaseActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔙 Back
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        // 🟦 Settings rows
        binding.rowChangePassword.setOnClickListener {
            findNavController().navigate(R.id.action_logoutFragment_to_changePasswordFragment)
        }

        binding.rowChangeEmail.setOnClickListener {
            findNavController().navigate(R.id.action_logoutFragment_to_changeEmailFragment)
        }

        binding.rowSecuritySettings.setOnClickListener {
            findNavController().navigate(R.id.action_logoutFragment_to_securitySettingsFragment)
        }

        // 🚪 Logout
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        // 🗑 Delete Account
        binding.btnDeleteAccount.setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    /** ---------------------------------------------------------
     *  🔹 LOGOUT — confirmation dialog (WARNING icon)
     * --------------------------------------------------------- */
    private fun showLogoutDialog() {
        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.WARNING, // ⚠️ Yellow warning icon
            title = "Logout",
            message = "Are you sure you want to log out of your account?",
            onConfirm = { performLogout() }
        )
    }

    private fun performLogout() {
    baseActivity?.showLoading(true)  // ⭐ Loading start

    auth.signOut()

    viewLifecycleOwner.lifecycleScope.launch {
        userPrefs.logout()

        baseActivity?.showLoading(false)  // ⭐ Loading stop
        navigateToLogin()
    }
}

    /** ---------------------------------------------------------
     *  🔥 DELETE ACCOUNT — confirmation dialog (ERROR icon)
     * --------------------------------------------------------- */
    private fun showDeleteAccountDialog() {
        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.ERROR, // ❌ Red danger icon
            title = "Delete Account",
            message = "Are you sure you want to permanently delete your account?\nThis action cannot be undone.",
            onConfirm = { performDeleteAccount() }
        )
    }

    private fun performDeleteAccount() {
        val user = auth.currentUser ?: return

        baseActivity?.showLoading(true)

        user.delete().addOnCompleteListener { task ->
            baseActivity?.showLoading(false)

            if (task.isSuccessful) {
                viewLifecycleOwner.lifecycleScope.launch {
                    userPrefs.clearAllUserData() // Fully reset all DataStore values
                    navigateToLogin()
                }
            } else {
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = "Account Deletion Failed",
                    message = task.exception?.localizedMessage ?: "Unknown error occurred."
                )
            }
        }
    }

    /** ---------------------------------------------------------
     *  🔄 Navigation to Login Screen
     * --------------------------------------------------------- */
        private fun navigateToLogin() {
        // MainActivity'deki root NavHost (R.id.nav_host) üzerinden git
        val rootNav = (requireActivity().supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment).navController

        val opts = navOptions {
            // app graph'i temizle
            popUpTo(R.id.nav_app) { inclusive = true }
            launchSingleTop = true
        }

        // Auth container'a dön → bunun startDestination'ı zaten loginFragment
        rootNav.navigate(R.id.authContainerFragment, null, opts)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}