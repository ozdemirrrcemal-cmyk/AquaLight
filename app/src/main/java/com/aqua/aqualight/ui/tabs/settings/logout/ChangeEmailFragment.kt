package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentChangeEmailBinding
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ChangeEmailFragment : Fragment(R.layout.fragment_change_email) {

    private var _binding: FragmentChangeEmailBinding? = null
    private val binding get() = _binding!!

    private val auth get() = FirebaseAuth.getInstance()
    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }
    private val baseActivity get() = activity as? BaseActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentChangeEmailBinding.bind(view)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnSaveEmail.setOnClickListener { attemptEmailChange() }
    }

    /** ---------------------------------------------------------
     *   0️⃣ VALIDATION + GOOGLE ACCOUNT BLOCK
     * --------------------------------------------------------- */
    private fun attemptEmailChange() {
        val currentEmail = binding.etCurrentEmail.text.toString().trim()
        val newEmail = binding.etNewEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        val user = auth.currentUser

        if (user == null) {
            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.ERROR,
                title = getString(R.string.change_email_user_not_found_title),
                message = getString(R.string.change_email_user_not_found_message)
            )
            return
        }

        // Google hesabı ise: uygulama içinden email değiştirilmez
        val isGoogleUser = user.providerData.any { it.providerId == "google.com" }
        if (isGoogleUser) {
            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.WARNING,
                title = getString(R.string.change_email_google_block_title),
                message = getString(R.string.change_email_google_block_message)
            )
            return
        }

        // Inline error reset
        binding.inputLayoutCurrentEmail.error = null
        binding.inputLayoutNewEmail.error = null
        binding.inputLayoutPassword.error = null

        // Boş alan kontrolleri
        if (currentEmail.isEmpty()) {
            binding.inputLayoutCurrentEmail.error =
                getString(R.string.change_email_error_current_required)
            return
        }
        if (newEmail.isEmpty()) {
            binding.inputLayoutNewEmail.error =
                getString(R.string.change_email_error_new_required)
            return
        }
        if (password.isEmpty()) {
            binding.inputLayoutPassword.error =
                getString(R.string.change_email_error_password_required)
            return
        }

        // Email formatı
        if (!Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            binding.inputLayoutNewEmail.error =
                getString(R.string.change_email_error_invalid_format)
            return
        }

        // Mevcut email ile hesabın email'i eşleşiyor mu?
        if (currentEmail != user.email) {
            binding.inputLayoutCurrentEmail.error =
                getString(R.string.change_email_old_incorrect)
            return
        }

        reauthenticateAndVerifyBeforeUpdate(currentEmail, password, newEmail)
    }

    /** ---------------------------------------------------------
     *   1️⃣ REAUTHENTICATE
     * --------------------------------------------------------- */
    private fun reauthenticateAndVerifyBeforeUpdate(
        oldEmail: String,
        password: String,
        newEmail: String
    ) {
        val user = auth.currentUser ?: return

        baseActivity?.showLoading(true)

        val credential = EmailAuthProvider.getCredential(oldEmail, password)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                // Reauth OK → yeni email için verify linki yolla
                verifyBeforeUpdateEmail(newEmail)
            }
            .addOnFailureListener {
                baseActivity?.showLoading(false)
                binding.inputLayoutPassword.error =
                    getString(R.string.change_email_error_incorrect_password)
            }
    }

    /** ---------------------------------------------------------
     *   2️⃣ EMAIL CHANGE (verifyBeforeUpdateEmail + FORCE LOGOUT)
     * --------------------------------------------------------- */
    private fun verifyBeforeUpdateEmail(newEmail: String) {
        val user = auth.currentUser ?: return

        user.verifyBeforeUpdateEmail(newEmail)
            .addOnSuccessListener {
                baseActivity?.showLoading(false)

                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.SUCCESS,
                    title = getString(R.string.change_email_verification_title),
                    message = getString(
                        R.string.change_email_verification_message,
                        newEmail
                    ),
                    onDismiss = {
                        // ✅ 1) Oturumu kapat
                        auth.signOut()

                        // ✅ 2) Local session'ı sıfırla ve login graph'e dön
                        viewLifecycleOwner.lifecycleScope.launch {
                            userPrefs.logout()
                            navigateToLoginRoot()
                        }
                    }
                )
            }
            .addOnFailureListener { e ->
                baseActivity?.showLoading(false)
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = getString(R.string.change_email_update_failed_title),
                    message = e.localizedMessage
                        ?: getString(R.string.change_email_update_failed)
                )
            }
    }

    /** ---------------------------------------------------------
     *   3️⃣ Root nav üzerinden Login graph'e dön
     * --------------------------------------------------------- */
    private fun navigateToLoginRoot() {
        val rootNav = (requireActivity().supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment).navController

        val opts = navOptions {
            popUpTo(R.id.nav_app) { inclusive = true } // app graph'i temizle
            launchSingleTop = true
        }

        // Auth container'a dön → startDestination zaten loginFragment
        rootNav.navigate(R.id.authContainerFragment, null, opts)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}