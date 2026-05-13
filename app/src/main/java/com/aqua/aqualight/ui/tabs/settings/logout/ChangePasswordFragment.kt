package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentChangePasswordBinding
import com.aqua.aqualight.ui.auth.security.ReAuthenticateFragment
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ChangePasswordFragment :
    Fragment(R.layout.fragment_change_password) {

    private var _binding: FragmentChangePasswordBinding? = null
    private val binding get() = _binding!!

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private var reAuthVerified = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(view, savedInstanceState)

        _binding =
            FragmentChangePasswordBinding.bind(view)

        setupBackButton()

        observeReAuthentication()

        if (!hasPasswordProvider()) {

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

                    changePasswordInternal()
                }
            }
    }

    // ---------------------------------------------------
    // SAVE BUTTON
    // ---------------------------------------------------

    private fun setupSaveButton() {

        binding.btnSavePassword.setOnClickListener {

            if (!validateInputs()) {
                return@setOnClickListener
            }

            if (!reAuthVerified) {

                navigateToReAuthentication()

                return@setOnClickListener
            }

            changePasswordInternal()
        }
    }

    // ---------------------------------------------------
    // PASSWORD PROVIDER
    // ---------------------------------------------------

    private fun hasPasswordProvider(): Boolean {

        val user =
            auth.currentUser ?: return false

        return user.providerData.any {
            it.providerId ==
                    EmailAuthProvider.PROVIDER_ID
        }
    }

    // ---------------------------------------------------
    // GOOGLE ONLY UI
    // ---------------------------------------------------

    private fun showGoogleOnlyInfo() {

        binding.formContainer.visibility =
            View.GONE

        binding.btnSavePassword.visibility =
            View.GONE

        binding.tvPasswordMessage.apply {

            text =
                getString(
                    R.string.change_password_google_only_info
                )

            setTextColor(
                resources.getColor(
                    R.color.settings_text_secondary,
                    null
                )
            )

            visibility = View.VISIBLE

            alpha = 1f
        }
    }

    // ---------------------------------------------------
    // VALIDATE INPUTS
    // ---------------------------------------------------

    private fun validateInputs(): Boolean {

        binding.inputLayoutCurrentPassword.error =
            null

        binding.inputLayoutNewPassword.error =
            null

        binding.inputLayoutConfirmPassword.error =
            null

        binding.tvPasswordMessage.visibility =
            View.GONE

        val currentPassword =
            binding.etCurrentPassword.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val newPassword =
            binding.etNewPassword.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val confirmPassword =
            binding.etConfirmPassword.text
                ?.toString()
                ?.trim()
                .orEmpty()

        var hasError = false

        if (currentPassword.isEmpty()) {

            binding.inputLayoutCurrentPassword.error =
                getString(
                    R.string.change_password_error_current_empty
                )

            hasError = true
        }

        if (newPassword.length < 6) {

            binding.inputLayoutNewPassword.error =
                getString(
                    R.string.change_password_error_new_short
                )

            hasError = true
        }

        if (newPassword != confirmPassword) {

            binding.inputLayoutConfirmPassword.error =
                getString(
                    R.string.change_password_error_not_match
                )

            hasError = true
        }

        return !hasError
    }

    // ---------------------------------------------------
    // RE-AUTH NAVIGATION
    // ---------------------------------------------------

    private fun navigateToReAuthentication() {

        findNavController().navigate(
            R.id.reAuthenticateFragment,
            bundleOf(
                ReAuthenticateFragment.ARG_ACTION to
                        ReAuthenticateFragment.ACTION_CHANGE_PASSWORD
            )
        )
    }

    // ---------------------------------------------------
    // CHANGE PASSWORD
    // ---------------------------------------------------

    private fun changePasswordInternal() {

        val newPassword =
            binding.etNewPassword.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val user =
            auth.currentUser

        if (user == null) {

            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.ERROR,
                title = getString(
                    R.string.change_password_error_title
                ),
                message = getString(
                    R.string.change_password_error_not_logged_in
                )
            )

            return
        }

        setLoading(true)

        user.updatePassword(newPassword)
            .addOnCompleteListener { task ->

                setLoading(false)

                if (task.isSuccessful) {

                    reAuthVerified = false

                    binding.tvPasswordMessage.apply {

                        text =
                            getString(
                                R.string.change_password_success
                            )

                        setTextColor(
                            resources.getColor(
                                R.color.settings_text_secondary,
                                null
                            )
                        )

                        visibility = View.VISIBLE

                        alpha = 1f
                    }

                    binding.etCurrentPassword
                        .text
                        ?.clear()

                    binding.etNewPassword
                        .text
                        ?.clear()

                    binding.etConfirmPassword
                        .text
                        ?.clear()

                } else {

                    DialogManager.showInfoDialog(
                        requireContext(),
                        DialogType.ERROR,
                        title = getString(
                            R.string.change_password_error_title
                        ),
                        message =
                            task.exception?.localizedMessage
                                ?: getString(
                                    R.string.change_password_error_generic
                                )
                    )
                }
            }
    }

    // ---------------------------------------------------
    // LOADING
    // ---------------------------------------------------

    private fun setLoading(
        isLoading: Boolean
    ) {

        binding.btnSavePassword.isEnabled =
            !isLoading

        binding.formContainer.isEnabled =
            !isLoading

        binding.btnSavePassword.alpha =
            if (isLoading) 0.6f else 1f
    }

    // ---------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------

    override fun onDestroyView() {

        _binding = null

        super.onDestroyView()
    }
}