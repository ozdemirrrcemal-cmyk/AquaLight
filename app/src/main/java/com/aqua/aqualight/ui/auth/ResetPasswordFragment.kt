package com.aqua.aqualight.ui.auth

import android.util.Patterns
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentResetPasswordBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    private val auth = Firebase.auth

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
        // 🔹 Şifre sıfırlama butonu
        btnSend.setOnClickListener { handleResetPassword() }

        // 🔹 Geri butonu
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    // 🔒 Şifre sıfırlama işlemi
    private fun handleResetPassword() {
        val email = binding.emailEditText.text?.toString()?.trim() ?: ""

        // 🧩 E-posta kontrolü
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailContainer.error = getString(R.string.reset_invalid_email)
            return
        } else {
            binding.emailContainer.error = null
        }

        binding.btnSend.isEnabled = false
        binding.btnSend.text = getString(R.string.signin_loading)

        // 📩 Firebase üzerinden sıfırlama e-postası gönder
        Firebase.auth.sendPasswordResetEmail(email)
            .addOnCompleteListener(requireActivity()) { task ->
                binding.btnSend.isEnabled = true
                binding.btnSend.text = getString(R.string.reset_password_button)

                if (task.isSuccessful) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.reset_success_message),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.reset_error_message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}