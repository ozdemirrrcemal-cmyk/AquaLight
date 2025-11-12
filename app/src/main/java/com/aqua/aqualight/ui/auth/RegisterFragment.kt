package com.aqua.aqualight.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentRegisterBinding
import com.aqua.aqualight.ui.main.MainActivity
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val auth = Firebase.auth
    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }
    private val baseActivity get() = activity as? BaseActivity

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

        // 🔹 Geri butonu navigation ile yönetiliyor
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun handleRegister() {
        val email = binding.emailEditText.text?.toString()?.trim().orEmpty()
        val password = binding.passwordEditText.text?.toString()?.trim().orEmpty()
        val repeatPassword = binding.etPasswordRepeat.text?.toString()?.trim().orEmpty()

        baseActivity?.showLoading(true)

        lifecycleScope.launch {
            // 🔍 Giriş doğrulamaları
            val warning = when {
                email.isEmpty() || password.isEmpty() || repeatPassword.isEmpty() ->
                    R.string.register_empty_fields_message to R.string.register_empty_fields_title
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    R.string.invalid_email to R.string.invalid_email_title
                password.length < 6 ->
                    R.string.invalid_password to R.string.invalid_password_title
                password != repeatPassword ->
                    R.string.passwords_do_not_match to R.string.passwords_do_not_match_title
                else -> null
            }

            if (warning != null) {
                baseActivity?.showLoading(false)
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.WARNING,
                    title = getString(warning.second),
                    message = getString(warning.first)
                )
                return@launch
            }

            // 🚀 Firebase kayıt işlemi
            binding.btnRegister.isEnabled = false
            binding.btnRegister.text = getString(R.string.register_loading)

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity()) { task ->
                    baseActivity?.showLoading(false)
                    binding.btnRegister.isEnabled = true
                    binding.btnRegister.text = getString(R.string.register_button)

                    if (task.isSuccessful) {
                        val user = task.result?.user
                        if (user != null) {
                            lifecycleScope.launch {
                                try {
                                    saveSession(user)
                                    DialogManager.showInfoDialog(
                                        requireContext(),
                                        DialogType.SUCCESS,
                                        title = getString(R.string.register_success_title),
                                        message = getString(R.string.register_success_message),
                                        onDismiss = { navigateToMain() }
                                    )
                                } catch (e: Exception) {
                                    DialogManager.showInfoDialog(
                                        requireContext(),
                                        DialogType.ERROR,
                                        title = getString(R.string.session_save_error_title),
                                        message = e.localizedMessage
                                            ?: getString(R.string.register_failed_message)
                                    )
                                }
                            }
                        }
                    } else {
                        val errorMsg = task.exception?.localizedMessage
                            ?: getString(R.string.register_failed_message)
                        DialogManager.showInfoDialog(
                            requireContext(),
                            DialogType.ERROR,
                            title = getString(R.string.register_failed_title),
                            message = errorMsg
                        )
                    }
                }
        }
    }

    private suspend fun saveSession(user: FirebaseUser) {
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

    private fun navigateToMain() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        startActivity(intent)
        // ✅ Login flow tamamen kapatılır, geri tuşuyla dönülmez
        requireActivity().finishAffinity()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}