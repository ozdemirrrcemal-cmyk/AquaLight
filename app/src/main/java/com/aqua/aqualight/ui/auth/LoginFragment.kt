package com.aqua.aqualight.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentLoginBinding
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
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
                baseActivity?.showLoading(true)
                firebaseAuthWithGoogle(account.idToken!!)
            } else {
                baseActivity?.showLoading(false)
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.WARNING,
                    title = getString(R.string.login_google_failed),
                    message = "Google hesabı seçilemedi. Lütfen tekrar deneyin."
                )
            }
        } catch (e: Exception) {
            Log.e("LoginFragment", "Google Sign-In failed ❌", e)
            baseActivity?.showLoading(false)
            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.ERROR,
                title = getString(R.string.login_google_failed),
                message = "Google girişi başarısız: ${e.localizedMessage}"
            )
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
            findNavController().navigate(R.id.action_loginFragment_to_signInFragment)
        }
        btnRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
        requireActivity().overridePendingTransition(0, 0)
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
                                onDismiss = { navigateToAppGraph() }
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

    private fun navigateToAppGraph() {
        // MainActivity'deki kök NavHost'u hedef al
        val rootNav = (requireActivity().supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment).navController

        val opts = navOptions {
            popUpTo(R.id.authContainerFragment) { inclusive = true }
            launchSingleTop = true
        }
        rootNav.navigate(R.id.nav_app, null, opts)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}