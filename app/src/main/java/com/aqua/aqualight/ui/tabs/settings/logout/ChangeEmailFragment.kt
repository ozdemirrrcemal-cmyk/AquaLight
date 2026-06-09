package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentChangeEmailBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ChangeEmailFragment :
    Fragment(R.layout.fragment_change_email) {

    private var _binding: FragmentChangeEmailBinding? = null
    private val binding get() = _binding!!

    private val auth get() = FirebaseAuth.getInstance()

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    private val baseActivity
        get() = activity as? BaseActivity

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentChangeEmailBinding.bind(view)

        setupHeader()
        setupScreen()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun setupScreen() {
        val user =
            auth.currentUser

        if (user == null) {
            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.ERROR,
                title = getString(
                    R.string.change_email_user_not_found_title
                ),
                message = getString(
                    R.string.change_email_user_not_found_message
                ),
                onDismiss = {
                    findNavController()
                        .popBackStack()
                }
            )

            return
        }

        val isGoogleUser =
            user.providerData.any {
                it.providerId == "google.com"
            }

        if (isGoogleUser) {
            showGoogleOnlyInfo()
            return
        }

        binding.btnSaveEmail.setOnClickListener {
            attemptEmailChange()
        }
    }

    private fun showGoogleOnlyInfo() {
        val user =
            auth.currentUser ?: return

        binding.etCurrentEmail.setText(
            user.email ?: ""
        )

        binding.inputLayoutCurrentEmail.isEnabled =
            false

        binding.inputLayoutNewEmail.visibility =
            View.GONE

        binding.inputLayoutPassword.visibility =
            View.GONE

        binding.btnSaveEmail.visibility =
            View.GONE

        binding.inputLayoutCurrentEmail.helperText =
            getString(
                R.string.change_email_google_only_info
            )
    }

    private fun attemptEmailChange() {
        val currentEmail =
            binding.etCurrentEmail.text
                .toString()
                .trim()

        val newEmail =
            binding.etNewEmail.text
                .toString()
                .trim()

        val password =
            binding.etPassword.text
                .toString()
                .trim()

        val user =
            auth.currentUser

        if (user == null) {
            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.ERROR,
                title = getString(
                    R.string.change_email_user_not_found_title
                ),
                message = getString(
                    R.string.change_email_user_not_found_message
                )
            )

            return
        }

        clearErrors()

        if (currentEmail.isEmpty()) {
            binding.inputLayoutCurrentEmail.error =
                getString(
                    R.string.change_email_error_current_required
                )

            return
        }

        if (newEmail.isEmpty()) {
            binding.inputLayoutNewEmail.error =
                getString(
                    R.string.change_email_error_new_required
                )

            return
        }

        if (password.isEmpty()) {
            binding.inputLayoutPassword.error =
                getString(
                    R.string.change_email_error_password_required
                )

            return
        }

        if (
            !Patterns.EMAIL_ADDRESS
                .matcher(newEmail)
                .matches()
        ) {
            binding.inputLayoutNewEmail.error =
                getString(
                    R.string.change_email_error_invalid_format
                )

            return
        }

        if (
            currentEmail.equals(
                newEmail,
                ignoreCase = true
            )
        ) {
            binding.inputLayoutNewEmail.error =
                getString(
                    R.string.change_email_same_email
                )

            return
        }

        if (currentEmail != user.email) {
            binding.inputLayoutCurrentEmail.error =
                getString(
                    R.string.change_email_old_incorrect
                )

            return
        }

        reauthenticateAndVerifyBeforeUpdate(
            currentEmail,
            password,
            newEmail
        )
    }

    private fun clearErrors() {
        binding.inputLayoutCurrentEmail.error =
            null

        binding.inputLayoutNewEmail.error =
            null

        binding.inputLayoutPassword.error =
            null

        binding.inputLayoutCurrentEmail.helperText =
            null
    }

    private fun reauthenticateAndVerifyBeforeUpdate(
        oldEmail: String,
        password: String,
        newEmail: String
    ) {
        val user =
            auth.currentUser ?: return

        baseActivity?.showLoading(
            true
        )

        binding.btnSaveEmail.isEnabled =
            false

        val credential =
            EmailAuthProvider.getCredential(
                oldEmail,
                password
            )

        user.reauthenticate(
            credential
        )
            .addOnSuccessListener {
                verifyBeforeUpdateEmail(
                    newEmail
                )
            }
            .addOnFailureListener {
                baseActivity?.showLoading(
                    false
                )

                binding.btnSaveEmail.isEnabled =
                    true

                binding.inputLayoutPassword.error =
                    getString(
                        R.string.change_email_error_incorrect_password
                    )
            }
    }

    private fun verifyBeforeUpdateEmail(
        newEmail: String
    ) {
        val user =
            auth.currentUser ?: return

        user.verifyBeforeUpdateEmail(
            newEmail
        )
            .addOnSuccessListener {
                baseActivity?.showLoading(
                    false
                )

                binding.btnSaveEmail.isEnabled =
                    true

                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.SUCCESS,
                    title = getString(
                        R.string.change_email_verification_title
                    ),
                    message = getString(
                        R.string.change_email_verification_message,
                        newEmail
                    ),
                    onDismiss = {
                        auth.signOut()

                        viewLifecycleOwner.lifecycleScope.launch {
                            userPrefs.logout()

                            navigateToLoginRoot()
                        }
                    }
                )
            }
            .addOnFailureListener { e ->
                baseActivity?.showLoading(
                    false
                )

                binding.btnSaveEmail.isEnabled =
                    true

                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = getString(
                        R.string.change_email_update_failed_title
                    ),
                    message =
                        e.localizedMessage
                            ?: getString(
                                R.string.change_email_update_failed
                            )
                )
            }
    }

    private fun navigateToLoginRoot() {
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