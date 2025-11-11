package com.aqua.aqualight.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentLoginBinding
import com.aqua.aqualight.ui.main.MainActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var googleSignInClient: GoogleSignInClient
    private val auth = Firebase.auth

    // ✅ Şifreli DataStore yöneticisi
    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    companion object {
        private const val RC_SIGN_IN = 9001
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        setupGoogleSignIn()
        setupButtonActions()
        return binding.root
    }

    // 🔹 Google Sign-In yapılandırması
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
    }

    // 🔹 Buton işlevleri
    private fun setupButtonActions() = with(binding) {
        btnGoogleLogin.setOnClickListener { signInWithGoogle() }

        btnSignIn.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, SignInFragment())
                addToBackStack(null)
            }
        }

        btnRegister.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, RegisterFragment())
                addToBackStack(null)
            }
        }
    }

    // 🔹 Google ile giriş işlemi
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w("LoginFragment", "Google sign in failed ❌", e)
            }
        }
    }

    // 🔹 Firebase kimlik doğrulama
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        Log.d("LoginFragment", "✅ Google Sign-In Success: ${user.email}")

                        // 🔐 Oturumu güvenli şekilde kaydet
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

                            // 🔄 Ana ekrana yönlendir
                            val intent = Intent(requireContext(), MainActivity::class.java)
                            startActivity(intent)
                            requireActivity().finish()
                        }
                    }
                } else {
                    Log.w("LoginFragment", "❌ Firebase auth failed", task.exception)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}