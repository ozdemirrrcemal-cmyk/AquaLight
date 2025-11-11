package com.aqua.aqualight.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentRegisterBinding
import com.aqua.aqualight.ui.main.MainActivity
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val auth = Firebase.auth
    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) } // ✅ Şifreli DataStore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        setupUI()
        return binding.root
    }

    private fun setupUI() = with(binding) {
        btnRegister.setOnClickListener { handleRegister() }
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun handleRegister() {
        val email = binding.emailEditText.text?.toString()?.trim() ?: ""
        val password = binding.passwordEditText.text?.toString()?.trim() ?: ""
        val repeatPassword = binding.etPasswordRepeat.text?.toString()?.trim() ?: ""

        // 🧩 Form doğrulama
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailContainer.error = getString(R.string.invalid_email)
            return
        } else binding.emailContainer.error = null

        if (password.length < 6) {
            binding.passwordContainer.error = getString(R.string.invalid_password)
            return
        } else binding.passwordContainer.error = null

        if (password != repeatPassword) {
            binding.passwordRepeatContainer.error = getString(R.string.passwords_do_not_match)
            return
        } else binding.passwordRepeatContainer.error = null

        // 🚀 Firebase kayıt işlemi
        binding.btnRegister.isEnabled = false
        binding.btnRegister.text = getString(R.string.register_loading)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                binding.btnRegister.isEnabled = true
                binding.btnRegister.text = getString(R.string.register_button)

                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        saveSession(user)
                        Toast.makeText(requireContext(), "Account created successfully!", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Registration failed: ${task.exception?.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    // 🔐 Oturum ve profil bilgisini güvenli DataStore’a kaydet
    private fun saveSession(user: FirebaseUser) {
        lifecycleScope.launch {
            userPrefs.saveUserSession(
                idToken = user.uid,
                isLoggedIn = true
            )
            userPrefs.saveProfile(
                email = user.email ?: "",
                username = user.displayName ?: "",
                photoUrl = user.photoUrl?.toString() ?: ""
            )
        }
    }

    // 🔄 Başarılı kayıt sonrası ana ekrana geçiş
    private fun navigateToMain() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}