package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
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
                title = "User Not Found",
                message = "Please log in again."
            )
            return
        }

        // Google hesabı ise: uygulama içinden email değiştirilmez
        val isGoogleUser = user.providerData.any { it.providerId == "google.com" }
        if (isGoogleUser) {
            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.WARNING,
                title = "Cannot Change Email",
                message = "This account was created with Google Sign-In.\nEmail cannot be changed inside the app."
            )
            return
        }

        // Inline error reset
        binding.inputLayoutCurrentEmail.error = null
        binding.inputLayoutNewEmail.error = null
        binding.inputLayoutPassword.error = null

        // Boş alan kontrolleri
        if (currentEmail.isEmpty()) {
            binding.inputLayoutCurrentEmail.error = "Enter your current email."
            return
        }
        if (newEmail.isEmpty()) {
            binding.inputLayoutNewEmail.error = "Enter your new email."
            return
        }
        if (password.isEmpty()) {
            binding.inputLayoutPassword.error = "Enter your password."
            return
        }

        // Email formatı
        if (!Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            binding.inputLayoutNewEmail.error = "Invalid email format."
            return
        }

        // Mevcut email ile hesabın email'i eşleşiyor mu?
        if (currentEmail != user.email) {
            binding.inputLayoutCurrentEmail.error = "Current email does not match your account."
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
                binding.inputLayoutPassword.error = "Incorrect password."
            }
    }

    /** ---------------------------------------------------------
     *   2️⃣ IDENTITY PLATFORM UYUMLU EMAIL DEĞİŞİMİ
     *       user.updateEmail() YERİNE
     *       user.verifyBeforeUpdateEmail() KULLANIYORUZ
     * --------------------------------------------------------- */
    private fun verifyBeforeUpdateEmail(newEmail: String) {
        val user = auth.currentUser ?: return

        user.verifyBeforeUpdateEmail(newEmail)
            .addOnSuccessListener {
                // Burada email HEMEN değişmez; kullanıcıya mail gider,
                // linke tıklayınca backend'de güncellenir.
                baseActivity?.showLoading(false)

                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.SUCCESS,
                    title = "Verification Sent",
                    message = "We have sent a verification link to $newEmail.\n" +
                              "Please confirm it from your inbox. Your email will be updated after verification."
                )
            }
            .addOnFailureListener { e ->
                baseActivity?.showLoading(false)
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = "Email Update Failed",
                    message = e.localizedMessage ?: "Unknown error."
                )
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}