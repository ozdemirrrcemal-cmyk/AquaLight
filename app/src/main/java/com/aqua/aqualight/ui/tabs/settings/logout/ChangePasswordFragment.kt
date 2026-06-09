package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentChangePasswordBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordFragment :
    Fragment(R.layout.fragment_change_password) {

    private var _binding: FragmentChangePasswordBinding? = null
    private val binding get() = _binding!!

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
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
            FragmentChangePasswordBinding.bind(view)

        setupHeader()

        if (!hasPasswordProvider()) {
            showGoogleOnlyInfo()
            return
        }

        binding.btnSavePassword.setOnClickListener {
            changePassword()
        }
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun hasPasswordProvider(): Boolean {
        val user =
            auth.currentUser ?: return false

        return user.providerData.any {
            it.providerId == EmailAuthProvider.PROVIDER_ID
        }
    }

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
                ContextCompat.getColor(
                    requireContext(),
                    R.color.settings_text_secondary
                )
            )

            visibility =
                View.VISIBLE

            alpha =
                1f
        }
    }

    private fun changePassword() {
        if (!hasPasswordProvider()) {
            showGoogleOnlyInfo()
            return
        }

        clearErrors()

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

        var hasError =
            false

        if (currentPassword.isEmpty()) {
            binding.inputLayoutCurrentPassword.error =
                getString(
                    R.string.change_password_error_current_empty
                )

            hasError =
                true
        }

        if (newPassword.length < 6) {
            binding.inputLayoutNewPassword.error =
                getString(
                    R.string.change_password_error_new_short
                )

            hasError =
                true
        }

        if (newPassword != confirmPassword) {
            binding.inputLayoutConfirmPassword.error =
                getString(
                    R.string.change_password_error_not_match
                )

            hasError =
                true
        }

        if (currentPassword == newPassword) {
            binding.inputLayoutNewPassword.error =
                getString(
                    R.string.change_password_error_same_password
                )

            hasError =
                true
        }

        if (hasError) return

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

        val email =
            user.email

        if (email.isNullOrEmpty()) {
            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.ERROR,
                title = getString(
                    R.string.change_password_error_title
                ),
                message = getString(
                    R.string.change_password_error_no_email
                )
            )

            return
        }

        baseActivity?.showLoading(
            true
        )

        setLoading(
            true
        )

        val credential =
            EmailAuthProvider.getCredential(
                email,
                currentPassword
            )

        user.reauthenticate(
            credential
        )
            .addOnCompleteListener { reauthTask ->

                if (!reauthTask.isSuccessful) {
                    baseActivity?.showLoading(
                        false
                    )

                    setLoading(
                        false
                    )

                    binding.inputLayoutCurrentPassword.error =
                        getString(
                            R.string.change_password_error_current_wrong
                        )

                    return@addOnCompleteListener
                }

                user.updatePassword(
                    newPassword
                )
                    .addOnCompleteListener { updateTask ->

                        baseActivity?.showLoading(
                            false
                        )

                        setLoading(
                            false
                        )

                        if (updateTask.isSuccessful) {
                            baseActivity?.showSnackBar(
                                getString(
                                    R.string.change_password_success
                                )
                            )

                            binding.etCurrentPassword.text
                                ?.clear()

                            binding.etNewPassword.text
                                ?.clear()

                            binding.etConfirmPassword.text
                                ?.clear()

                        } else {
                            DialogManager.showInfoDialog(
                                requireContext(),
                                DialogType.ERROR,
                                title = getString(
                                    R.string.change_password_error_title
                                ),
                                message =
                                    updateTask.exception
                                        ?.localizedMessage
                                        ?: getString(
                                            R.string.change_password_error_generic
                                        )
                            )
                        }
                    }
            }
    }

    private fun clearErrors() {
        binding.inputLayoutCurrentPassword.error =
            null

        binding.inputLayoutNewPassword.error =
            null

        binding.inputLayoutConfirmPassword.error =
            null

        binding.tvPasswordMessage.visibility =
            View.GONE
    }

    private fun setLoading(
        isLoading: Boolean
    ) {
        binding.btnSavePassword.isEnabled =
            !isLoading

        binding.etCurrentPassword.isEnabled =
            !isLoading

        binding.etNewPassword.isEnabled =
            !isLoading

        binding.etConfirmPassword.isEnabled =
            !isLoading

        binding.btnSavePassword.alpha =
            if (isLoading) 0.6f else 1f
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }
}