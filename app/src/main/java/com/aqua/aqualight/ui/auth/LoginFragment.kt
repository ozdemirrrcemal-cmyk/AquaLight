package com.aqua.aqualight.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentLoginBinding
import com.aqua.aqualight.ui.main.MainActivity
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
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
    private val baseActivity get() = activity as? BaseActivity

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                Log.d("LoginFragment", "✅ Google Sign-In account: ${account.email}")
                // 🔄 Loading göstergesi burada başlatıldı (daha doğru nokta)
                baseActivity?.showLoading(true)
                firebaseAuthWithGoogle(account.idToken!!)
            } else {
                Log.w("LoginFragment", "⚠️ Google account null döndü!")
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.WARNING,
                    title = getString(R.string.login_google_failed),
                    message = "Google hesabı seçilemedi. Lütfen tekrar deneyin."
                )
            }
        } catch (e: Exception) {
            Log.e("LoginFragment", "Google Sign-In failed ❌", e)
            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.ERROR,
                title = getString(R.string.login_google_failed),
                message = "Google girişi başarısız: ${e.localizedMessage}"
            )
        } finally {
            baseActivity?.showLoading(false)
        }
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

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
    }

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

    private fun signInWithGoogle() {
        // ❌ Artık loading burada gösterilmiyor — sadece Firebase sürecinde gösteriliyor.
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                baseActivity?.showLoading(false)

                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            userPrefs.saveUserSession(user.uid, true)
                            userPrefs.saveProfile(
                                email = user.email ?: "",
                                username = user.displayName ?: "",
                                photoUrl = user.photoUrl?.toString() ?: ""
                            )

                            DialogManager.showInfoDialog(
                                requireContext(),
                                DialogType.SUCCESS,
                                title = getString(R.string.login_google_success),
                                message = "Google hesabınızla başarıyla giriş yaptınız.",
                                onDismiss = {
                                    val intent = Intent(requireContext(), MainActivity::class.java)
                                    startActivity(intent)
                                    requireActivity().finishAffinity() // 🔁 öneri 2: geri dönüşü engelle
                                }
                            )
                        }
                    } else {
                        DialogManager.showInfoDialog(
                            requireContext(),
                            DialogType.WARNING,
                            title = getString(R.string.login_firebase_failed),
                            message = "Kullanıcı bilgisi alınamadı, lütfen tekrar deneyin."
                        )
                    }
                } else {
                    Log.e("LoginFragment", "❌ Firebase auth failed", task.exception)
                    DialogManager.showInfoDialog(
                        requireContext(),
                        DialogType.ERROR,
                        title = getString(R.string.login_firebase_failed),
                        message = "Kimlik doğrulama başarısız. ${task.exception?.localizedMessage ?: ""}"
                    )
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}