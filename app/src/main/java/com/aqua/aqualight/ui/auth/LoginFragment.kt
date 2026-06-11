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
import com.aqua.aqualight.data.auth.AuthSessionManager
import com.aqua.aqualight.databinding.FragmentLoginBinding
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var googleSignInClient: GoogleSignInClient

    private val auth = Firebase.auth

    private val baseActivity
        get() = activity as? BaseActivity

    private val authSessionManager by lazy {
        AuthSessionManager.create(requireContext())
    }

    // ---------------------------------------------------
    // GOOGLE SIGN IN RESULT
    // ---------------------------------------------------

    private val googleSignInLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) googleResult@{ result ->

            val task =
                GoogleSignIn.getSignedInAccountFromIntent(
                    result.data
                )

            try {

                val account =
                    task.getResult(
                        ApiException::class.java
                    )

                if (account != null) {

                    Log.d(
                        "LoginFragment",
                        "✅ Google Sign-In account: ${account.email}"
                    )

                    val token = account.idToken

                    if (token.isNullOrBlank()) {
                        baseActivity?.showLoading(false)

                        DialogManager.showInfoDialog(
                            requireContext(),
                            DialogType.ERROR,
                            title = getString(
                                R.string.login_google_failed
                            ),
                            message = getString(
                                R.string.login_google_account_not_selected
                            )
                        )

                        return@googleResult
                    }

                    baseActivity?.showLoading(true)

                    firebaseAuthWithGoogle(
                        token
                    )

                } else {

                    baseActivity?.showLoading(false)

                    DialogManager.showInfoDialog(
                        requireContext(),
                        DialogType.WARNING,
                        title = getString(
                            R.string.login_google_failed
                        ),
                        message = getString(
                            R.string.login_google_account_not_selected
                        )
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    "LoginFragment",
                    "Google Sign-In failed ❌",
                    e
                )

                baseActivity?.showLoading(false)

                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = getString(
                        R.string.login_google_failed
                    ),
                    message = getString(
                        R.string.login_google_failed_with_reason,
                        e.localizedMessage ?: ""
                    )
                )
            }
        }

    // ---------------------------------------------------
    // ON CREATE VIEW
    // ---------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding =
            FragmentLoginBinding.inflate(
                inflater,
                container,
                false
            )

        setupGoogleSignIn()

        setupButtonActions()

        return binding.root
    }

    // ---------------------------------------------------
    // GOOGLE CONFIG
    // ---------------------------------------------------

    private fun setupGoogleSignIn() {

        val gso =
            GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN
            )
                .requestIdToken(
                    getString(
                        R.string.default_web_client_id
                    )
                )
                .requestEmail()
                .build()

        googleSignInClient =
            GoogleSignIn.getClient(
                requireContext(),
                gso
            )
    }

    // ---------------------------------------------------
    // BUTTON ACTIONS
    // ---------------------------------------------------

    private fun setupButtonActions() =
        with(binding) {

            btnGoogleLogin.setOnClickListener {

                signInWithGoogle()
            }

            btnSignIn.setOnClickListener {

                findNavController().navigate(
                    LoginFragmentDirections.actionLoginFragmentToSignInFragment()
                )
            }

            btnRegister.setOnClickListener {

                findNavController().navigate(
                    LoginFragmentDirections.actionLoginFragmentToRegisterFragment()
                )
            }
        }

    // ---------------------------------------------------
    // START GOOGLE LOGIN
    // ---------------------------------------------------

    private fun signInWithGoogle() {

        val signInIntent =
            googleSignInClient.signInIntent

        googleSignInLauncher.launch(
            signInIntent
        )

        requireActivity()
            .overridePendingTransition(
                0,
                0
            )
    }

    // ---------------------------------------------------
    // FIREBASE AUTH
    // ---------------------------------------------------

    private fun firebaseAuthWithGoogle(
        idToken: String
    ) {

        val credential =
            GoogleAuthProvider.getCredential(
                idToken,
                null
            )

        auth.signInWithCredential(
            credential
        )
            .addOnCompleteListener(
                requireActivity()
            ) { task ->

                baseActivity?.showLoading(false)

                if (task.isSuccessful) {

                    val user =
                        auth.currentUser

                    if (user != null) {

                        viewLifecycleOwner
                            .lifecycleScope
                            .launch {

                                authSessionManager.completeLogin(
                                    user = user
                                )

                                // ✅ Direkt uygulamaya geç
                                navigateToAppGraph()
                            }

                    } else {

                        DialogManager.showInfoDialog(
                            requireContext(),
                            DialogType.WARNING,
                            title = getString(
                                R.string.login_firebase_failed
                            ),
                            message = getString(
                                R.string.login_user_info_unavailable
                            )
                        )
                    }

                } else {

                    Log.e(
                        "LoginFragment",
                        "❌ Firebase auth failed",
                        task.exception
                    )

                    DialogManager.showInfoDialog(
                        requireContext(),
                        DialogType.ERROR,
                        title = getString(
                            R.string.login_firebase_failed
                        ),
                        message = getString(
                            R.string.login_auth_failed_with_reason,
                            task.exception?.localizedMessage ?: ""
                        )
                    )
                }
            }
    }

    // ---------------------------------------------------
    // NAVIGATE APP
    // ---------------------------------------------------

    private fun navigateToAppGraph() {

        val rootNav =
            (
                requireActivity()
                    .supportFragmentManager
                    .findFragmentById(R.id.nav_host)
                        as NavHostFragment
                ).navController

        val opts =
            navOptions {

                popUpTo(
                    R.id.authContainerFragment
                ) {
                    inclusive = true
                }

                launchSingleTop = true
            }

        rootNav.navigate(
            R.id.nav_app,
            null,
            opts
        )
    }

    // ---------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------

    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }
}