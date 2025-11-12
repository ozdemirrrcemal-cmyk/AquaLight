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
import com.aqua.aqualight.databinding.FragmentSigninBinding
import com.aqua.aqualight.ui.main.MainActivity
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class SignInFragment : Fragment() {

    private var _binding: FragmentSigninBinding? = null
    private val binding get() = _binding!!

    private val auth = Firebase.auth
    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }
    private val baseActivity get() = activity as? BaseActivity

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

        // 🔹 Şifremi unuttum → Navigation Component ile geçiş
        tvForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_signInFragment_to_resetPasswordFragment)
        }

        // 🔹 Geri dön → Navigation Component stack kontrolü
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun handleSignIn() {
        val email = binding.emailEditText.text?.toString()?.trim().orEmpty()
        val password = binding.passwordEditText.text?.toString()?.trim().orEmpty()

        baseActivity?.showLoading(true)

        lifecycleScope.launch {
            // 🧩 Boş alan kontrolü
            if (email.isEmpty() || password.isEmpty()) {
                baseActivity?.showLoading(false)
                showWarning(R.string.signin_empty_fields_title, R.string.signin_empty_fields_message)
                return@launch
            }

            // 📧 E-posta doğrulama
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                baseActivity?.showLoading(false)
                showWarning(R.string.invalid_email_title, R.string.invalid_email)
                return@launch
            }

            // 🔒 Şifre doğrulama
            if (password.length < 6) {
                baseActivity?.showLoading(false)
                showWarning(R.string.invalid_password_title, R.string.invalid_password)
                return@launch
            }

            // 🚀 Firebase Authentication
            binding.btnLogin.isEnabled = false
            binding.btnLogin.text = getString(R.string.signin_loading)

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity()) { task ->
                    baseActivity?.showLoading(false)
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = getString(R.string.signin_login_button)

                    if (task.isSuccessful) {
                        val user = task.result?.user
                        if (user != null) {
                            lifecycleScope.launch {
                                try {
                                    saveSession(user)
                                    DialogManager.showInfoDialog(
                                        requireContext(),
                                        DialogType.SUCCESS,
                                        title = getString(R.string.login_success_title),
                                        message = getString(R.string.login_success_message),
                                        onDismiss = { navigateToMain() }
                                    )
                                } catch (e: Exception) {
                                    DialogManager.showInfoDialog(
                                        requireContext(),
                                        DialogType.ERROR,
                                        title = getString(R.string.session_save_error_title),
                                        message = e.localizedMessage ?: "Session kaydedilemedi"
                                    )
                                }
                            }
                        }
                    } else {
                        val errorMsg = task.exception?.localizedMessage
                            ?: getString(R.string.signin_failed_default)
                        DialogManager.showInfoDialog(
                            requireContext(),
                            DialogType.ERROR,
                            title = getString(R.string.signin_failed_title),
                            message = errorMsg
                        )
                    }
                }
        }
    }

    private fun showWarning(titleRes: Int, msgRes: Int) {
        DialogManager.showInfoDialog(
            requireContext(),
            DialogType.WARNING,
            title = getString(titleRes),
            message = getString(msgRes)
        )
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