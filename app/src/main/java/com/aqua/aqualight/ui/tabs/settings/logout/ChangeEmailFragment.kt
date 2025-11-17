package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
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

        binding.btnSaveEmail.setOnClickListener {
            attemptEmailChange()
        }
    }

    /** -------------------------------
     *  EMAIL CHANGE FLOW
     * ------------------------------- */
    private fun attemptEmailChange() {
        val currentEmail = binding.etCurrentEmail.text.toString().trim()
        val newEmail = binding.etNewEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (currentEmail.isEmpty() || newEmail.isEmpty() || password.isEmpty()) {
            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.WARNING,
                title = "Missing Information",
                message = "Please fill in all fields."
            )
            return
        }

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

        if (currentEmail != user.email) {
            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.WARNING,
                title = "Old Email Incorrect",
                message = "The email you entered does not match your current email."
            )
            return
        }

        reauthenticateAndUpdateEmail(user.email!!, password, newEmail)
    }

    /** STEP 1 — Reauthenticate */
    private fun reauthenticateAndUpdateEmail(
        oldEmail: String,
        password: String,
        newEmail: String
    ) {
        val user = auth.currentUser ?: return

        baseActivity?.showLoading(true)

        val credential = EmailAuthProvider.getCredential(oldEmail, password)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                updateEmail(newEmail)
            }
            .addOnFailureListener {
                baseActivity?.showLoading(false)
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = "Authentication Failed",
                    message = it.localizedMessage ?: "Reauthentication failed."
                )
            }
    }

    /** STEP 2 — Update Email */
    private fun updateEmail(newEmail: String) {
        val user = auth.currentUser ?: return

        user.updateEmail(newEmail)
            .addOnSuccessListener {
                updateLocalProfile(newEmail)
            }
            .addOnFailureListener {
                baseActivity?.showLoading(false)
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = "Email Update Failed",
                    message = it.localizedMessage ?: ""
                )
            }
    }

    /** STEP 3 — Update Local DataStore */
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
                message = "Your email has been successfully updated.",
                onDismiss = { findNavController().popBackStack() },
                autoDismissMillis = 1200L
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}