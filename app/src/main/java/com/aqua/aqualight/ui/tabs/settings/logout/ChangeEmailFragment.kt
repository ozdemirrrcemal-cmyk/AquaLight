package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentChangeEmailBinding
import com.aqua.aqualight.ui.auth.security.ReAuthenticateFragment
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

    private val baseActivity get() = activity as? BaseActivity

    private var reAuthVerified = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(view, savedInstanceState)

        _binding =
            FragmentChangeEmailBinding.bind(view)

        setupBackButton()

        observeReAuthentication()

        val user = auth.currentUser

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

                    findNavController().popBackStack()
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

        setupSaveButton()
    }

    // ---------------------------------------------------
    // BACK BUTTON
    // ---------------------------------------------------

    private fun setupBackButton() {

        binding.btnBack.setOnClickListener {

            findNavController().popBackStack()
        }
    }

    // ---------------------------------------------------
    // OBSERVE RE-AUTH
    // ---------------------------------------------------

    private fun observeReAuthentication() {

        val savedStateHandle =
            findNavController()
                .currentBackStackEntry
                ?.savedStateHandle

        savedStateHandle
            ?.getLiveData<Boolean>(
                "reauth_success"
            )
            ?.observe(viewLifecycleOwner) { success ->

                if (success == true) {

                    reAuthVerified = true

                    savedStateHandle.remove<Boolean>(
                        "reauth_success"
                    )

                    verifyBeforeUpdateEmail()
                }
            }
    }

    // ---------------------------------------------------
    // SAVE BUTTON
    // ---------------------------------------------------

    private fun setupSaveButton() {

        binding.btnSaveEmail.setOnClickListener {

            if (!validateInputs()) {
                return@setOnClickListener
            }

            if (!reAuthVerified) {

                navigateToReAuthentication()

                return@setOnClickListener
            }

            verifyBeforeUpdateEmail()
        }
    }

    // ---------------------------------------------------
    // GOOGLE ONLY UI
    // ---------------------------------------------------

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

    // ---------------------------------------------------
    // VALIDATION
    // ---------------------------------------------------

    private fun validateInputs(): Boolean {

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

        binding.inputLayoutCurrentEmail.error =
            null

        binding.inputLayoutNewEmail.error =
            null

        binding.inputLayoutPassword.error =
            null

        binding.inputLayoutCurrentEmail.helperText =
            null

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

            return false
        }

        if (currentEmail.isEmpty()) {

            binding.inputLayoutCurrentEmail.error =
                getString(
                    R.string.change_email_error_current_required
                )

            return false
        }

        if (newEmail.isEmpty()) {

            binding.inputLayoutNewEmail.error =
                getString(
                    R.string.change_email_error_new_required
                )

            return false
        }

        if (password.isEmpty()) {

            binding.inputLayoutPassword.error =
                getString(
                    R.string.change_email_error_password_required
                )

            return false
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

            return false
        }

        if (currentEmail != user.email) {

            binding.inputLayoutCurrentEmail.error =
                getString(
                    R.string.change_email_old_incorrect
                )

            return false
        }

        return true
    }

    // ---------------------------------------------------
    // RE-AUTH NAVIGATION
    // ---------------------------------------------------

    private fun navigateToReAuthentication() {

        findNavController().navigate(
            R.id.reAuthenticateFragment,
            bundleOf(
                ReAuthenticateFragment.ARG_ACTION to
                        ReAuthenticateFragment.ACTION_CHANGE_EMAIL
            )
        )
    }

    // ---------------------------------------------------
    // EMAIL UPDATE
    // ---------------------------------------------------

    private fun verifyBeforeUpdateEmail() {

        val user =
            auth.currentUser ?: return

        val newEmail =
            binding.etNewEmail.text
                .toString()
                .trim()

        setLoading(true)

        user.verifyBeforeUpdateEmail(newEmail)
            .addOnSuccessListener {

                setLoading(false)

                reAuthVerified = false

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

                setLoading(false)

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

    // ---------------------------------------------------
    // LOADING
    // ---------------------------------------------------

    private fun setLoading(
        isLoading: Boolean
    ) {

        baseActivity?.showLoading(isLoading)

        binding.btnSaveEmail.isEnabled =
            !isLoading

        binding.btnSaveEmail.alpha =
            if (isLoading) 0.6f else 1f
    }

    // ---------------------------------------------------
    // LOGIN NAVIGATION
    // ---------------------------------------------------

    private fun navigateToLoginRoot() {

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

    // ---------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------

    override fun onDestroyView() {

        _binding = null

        super.onDestroyView()
    }
}