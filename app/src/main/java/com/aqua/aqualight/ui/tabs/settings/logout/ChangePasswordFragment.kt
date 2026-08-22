package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentChangePasswordBinding
import com.aqua.aqualight.ui.auth.state.AuthActionState
import com.aqua.aqualight.ui.auth.viewmodel.ChangePasswordViewModel
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class ChangePasswordFragment :
    Fragment(R.layout.fragment_change_password) {

    private var _binding: FragmentChangePasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChangePasswordViewModel by viewModels {
        requireContext().requireAppContainer().authViewModelFactory
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
        setupScreen()
        observeState()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_change_password)
            )
        )
    }

    private fun setupScreen() {
        if (!viewModel.hasPasswordProvider()) {
            showGoogleOnlyInfo()
            return
        }

        binding.btnSavePassword.setOnClickListener {
            changePassword()
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
                    R.color.aqua_card_text_secondary
                )
            )

            visibility =
                View.VISIBLE

            alpha =
                1f
        }
    }

    private fun changePassword() {
        clearErrors()

        val currentPassword =
            binding.etCurrentPassword.text
                ?.toString()
                .orEmpty()

        val newPassword =
            binding.etNewPassword.text
                ?.toString()
                .orEmpty()

        val confirmPassword =
            binding.etConfirmPassword.text
                ?.toString()
                .orEmpty()

        var hasError = false

        if (currentPassword.trim().isEmpty()) {
            binding.inputLayoutCurrentPassword.error =
                getString(
                    R.string.change_password_error_current_empty
                )

            hasError = true
        }

        if (newPassword.trim().length < 6) {
            binding.inputLayoutNewPassword.error =
                getString(
                    R.string.change_password_error_new_short
                )

            hasError = true
        }

        if (newPassword.trim() != confirmPassword.trim()) {
            binding.inputLayoutConfirmPassword.error =
                getString(
                    R.string.change_password_error_not_match
                )

            hasError = true
        }

        if (
            currentPassword.trim().isNotEmpty() &&
            currentPassword.trim() == newPassword.trim()
        ) {
            binding.inputLayoutNewPassword.error =
                getString(
                    R.string.change_password_error_same_password
                )

            hasError = true
        }

        if (hasError) return

        viewModel.changePassword(
            currentPassword = currentPassword,
            newPassword = newPassword,
            confirmPassword = confirmPassword
        )
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.state.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(
        state: AuthActionState
    ) {
        val isLoading = state is AuthActionState.Loading

        setFragmentGlobalLoading(isLoading)
        setLoading(isLoading)

        when (state) {
            AuthActionState.PasswordChanged -> {
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

                viewModel.resetState()
            }

            is AuthActionState.Message -> {
                DialogManager.showInfoDialog(
                    requireContext(),
                    state.kind.toDialogType(),
                    title = state.title.resolve(requireContext()),
                    message = state.message.resolve(requireContext())
                )
                viewModel.resetState()
            }

            else -> Unit
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

    private fun AuthActionState.Kind.toDialogType(): DialogType {
        return when (this) {
            AuthActionState.Kind.WARNING -> DialogType.WARNING
            AuthActionState.Kind.ERROR -> DialogType.ERROR
            AuthActionState.Kind.SUCCESS -> DialogType.SUCCESS
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }
}
