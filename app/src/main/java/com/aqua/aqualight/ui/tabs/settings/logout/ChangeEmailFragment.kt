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
     *        STEP 0 — VALIDATION + GOOGLE LOGIN BLOCK
     * --------------------------------------------------------- */
    private fun attemptEmailChange() {
        val currentEmail = binding.etCurrentEmail.text.toString().trim()
        val newEmail = binding.etNewEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        val user = auth.currentUser

        // Safety
        if (user == null) {
            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.ERROR,
                title = "User Not Found",
                message = "Please log in again."
            )
            return
        }

        // ❌ GOOGLE LOGIN BLOCK
        val providerId = user.providerData.firstOrNull()?.providerId
        if (providerId == "google.com") {
            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.WARNING,
                title = "Cannot Change Email",
                message = "Email addresses of Google accounts cannot be changed inside the app."
            )
            return
        }

        // Inline input errors reset
        binding.inputLayoutCurrentEmail.error = null
        binding.inputLayoutNewEmail.error = null
        binding.inputLayoutPassword.error = null

        // Empty fields?
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

        // Email format check
        if (!Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            binding.inputLayoutNewEmail.error = "Invalid email format."
            return
        }

        // Current email mismatch
        if (currentEmail != user.email) {
            binding.inputLayoutCurrentEmail.error = "Current email does not match your account."
            return
        }

        reauthenticateAndUpdateEmail(currentEmail, password, newEmail)
    }

    /** ---------------------------------------------------------
     *              STEP 1 — REAUTHENTICATION
     * --------------------------------------------------------- */
    private fun reauthenticateAndUpdateEmail(oldEmail: String, password: String, newEmail: String) {
        val user = auth.currentUser ?: return

        baseActivity?.showLoading(true)

        val credential = EmailAuthProvider.getCredential(oldEmail, password)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                updateEmail(newEmail)
            }
            .addOnFailureListener {
                baseActivity?.showLoading(false)
                binding.inputLayoutPassword.error = "Incorrect password."
            }
    }

    /** ---------------------------------------------------------
     *              STEP 2 — FIREBASE EMAIL UPDATE
     * --------------------------------------------------------- */
    private fun updateEmail(newEmail: String) {
        val user = auth.currentUser ?: return

        user.updateEmail(newEmail)
            .addOnSuccessListener {
                sendVerificationEmail(newEmail)
            }
            .addOnFailureListener {
                baseActivity?.showLoading(false)
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = "Email Update Failed",
                    message = it.localizedMessage ?: "Unknown error."
                )
            }
    }

    /** ---------------------------------------------------------
     *      STEP 3 — SEND VERIFICATION TO NEW EMAIL
     * --------------------------------------------------------- */
    private fun sendVerificationEmail(newEmail: String) {
        val user = auth.currentUser ?: return

        user.sendEmailVerification()
            .addOnCompleteListener {
                updateLocalProfile(newEmail)
            }
    }

    /** ---------------------------------------------------------
     *      STEP 4 — UPDATE LOCAL DATASTORE & FINISH
     * --------------------------------------------------------- */
    private fun updateLocalProfile(newEmail: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.saveProfile(
                email = newEmail,
                username = null,
                fullName = null,
                photoUrl = null
            )

            baseActivity?.showLoading(false)

            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.SUCCESS,
                title = "Email Updated",
                message = "Your email has been updated.\nA verification link has been sent.",
                onDismiss = { findNavController().popBackStack() },
                autoDismissMillis = 1400L
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}