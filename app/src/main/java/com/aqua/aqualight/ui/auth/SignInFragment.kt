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
import com.aqua.aqualight.databinding.FragmentSigninBinding
import com.aqua.aqualight.ui.main.MainActivity
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class SignInFragment : Fragment() {

    private var _binding: FragmentSigninBinding? = null
    private val binding get() = _binding!!

    private val auth = Firebase.auth
    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSigninBinding.inflate(inflater, container, false)
        setupUI()
        return binding.root
    }

    private fun setupUI() = with(binding) {
        btnLogin.setOnClickListener { handleSignIn() }

        tvForgotPassword.setOnClickListener {
            Toast.makeText(requireContext(), "Password reset coming soon", Toast.LENGTH_SHORT).show()
        }

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun handleSignIn() {
        val email = binding.emailEditText.text?.toString()?.trim() ?: ""
        val password = binding.passwordEditText.text?.toString()?.trim() ?: ""

        // 🧩 Giriş doğrulama
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailContainer.error = getString(R.string.invalid_email)
            return
        } else {
            binding.emailContainer.error = null
        }

        if (password.length < 6) {
            binding.passwordContainer.error = getString(R.string.invalid_password)
            return
        } else {
            binding.passwordContainer.error = null
        }

        // 🚀 Firebase Authentication işlemi
        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = getString(R.string.signin_loading)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = getString(R.string.signin_login_button)

                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        saveSession(user)
                        Toast.makeText(requireContext(), "Welcome back!", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Sign-in failed: ${task.exception?.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    // 🔐 Oturum bilgisini güvenli biçimde DataStore’a kaydet
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

    // 🔄 Başarılı girişten sonra ana ekrana geçiş
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