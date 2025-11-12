package com.aqua.aqualight.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.delay
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
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ResetPasswordFragment())
                .addToBackStack(null)
                .commit()
        }

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun handleSignIn() {
        val email = binding.emailEditText.text?.toString()?.trim() ?: ""
        val password = binding.passwordEditText.text?.toString()?.trim() ?: ""

        // 🔄 Loading ekranını kısa süreli göster
        (requireActivity() as BaseActivity).showLoading(true)

        lifecycleScope.launch {
            delay(600) // kullanıcıya tepki hissi versin diye kısa delay

            // 🧩 Boş alan kontrolü
            if (email.isEmpty() || password.isEmpty()) {
                (requireActivity() as BaseActivity).showLoading(false)
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.WARNING,
                    title = getString(R.string.signin_empty_fields_title),
                    message = getString(R.string.signin_empty_fields_message)
                )
                return@launch
            }

            // 🧩 E-posta doğrulama
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                (requireActivity() as BaseActivity).showLoading(false)
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.WARNING,
                    title = getString(R.string.invalid_email_title),
                    message = getString(R.string.invalid_email)
                )
                return@launch
            }

            // 🧩 Şifre doğrulama
            if (password.length < 6) {
                (requireActivity() as BaseActivity).showLoading(false)
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.WARNING,
                    title = getString(R.string.invalid_password_title),
                    message = getString(R.string.invalid_password)
                )
                return@launch
            }

            // 🚀 Firebase Authentication işlemi
            binding.btnLogin.isEnabled = false
            binding.btnLogin.text = getString(R.string.signin_loading)

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity()) { task ->
                    (requireActivity() as BaseActivity).showLoading(false)
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = getString(R.string.signin_login_button)

                    if (task.isSuccessful) {
                        val user = task.result?.user
                        if (user != null) {
                            saveSession(user)
                            DialogManager.showInfoDialog(
                                requireContext(),
                                DialogType.SUCCESS,
                                title = getString(R.string.login_success_title),
                                message = getString(R.string.login_success_message),
                                onDismiss = { navigateToMain() }
                            )
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

    // 🔐 Oturum bilgisini güvenli biçimde kaydet
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

    // 🔄 Başarılı giriş sonrası
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