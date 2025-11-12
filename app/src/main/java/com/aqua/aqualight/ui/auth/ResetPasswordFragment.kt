package com.aqua.aqualight.ui.auth

import android.util.Patterns
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentResetPasswordBinding
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    private val auth = Firebase.auth
    private val baseActivity get() = activity as? BaseActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        setupUI()
        return binding.root
    }

    private fun setupUI() = with(binding) {
        btnSend.setOnClickListener { handleResetPassword() }

        // 🔹 Navigation Component ile geri dön
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun handleResetPassword() {
        val email = binding.emailEditText.text?.toString()?.trim().orEmpty()

        baseActivity?.showLoading(true)

        lifecycleScope.launch {
            when {
                email.isEmpty() -> {
                    baseActivity?.showLoading(false)
                    DialogManager.showInfoDialog(
                        requireContext(),
                        DialogType.WARNING,
                        title = getString(R.string.reset_empty_email_title),
                        message = getString(R.string.reset_empty_email_message)
                    )
                    return@launch
                }

                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    baseActivity?.showLoading(false)
                    DialogManager.showInfoDialog(
                        requireContext(),
                        DialogType.WARNING,
                        title = getString(R.string.reset_invalid_email_title),
                        message = getString(R.string.reset_invalid_email)
                    )
                    return@launch
                }
            }

            // 🚀 Firebase işlem başlıyor
            binding.btnSend.isEnabled = false
            binding.btnSend.text = getString(R.string.signin_loading)

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(requireActivity()) { task ->
                    baseActivity?.showLoading(false)
                    binding.btnSend.isEnabled = true
                    binding.btnSend.text = getString(R.string.reset_password_button)

                    if (task.isSuccessful) {
                        DialogManager.showInfoDialog(
                            requireContext(),
                            DialogType.SUCCESS,
                            title = getString(R.string.reset_success_title),
                            message = getString(R.string.reset_success_message),
                            // 🔹 Navigation Component ile geri dön
                            onDismiss = { findNavController().popBackStack() }
                        )
                    } else {
                        val errorMsg = task.exception?.localizedMessage
                            ?: getString(R.string.reset_error_message)
                        DialogManager.showInfoDialog(
                            requireContext(),
                            DialogType.ERROR,
                            title = getString(R.string.reset_failed_title),
                            message = errorMsg
                        )
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}