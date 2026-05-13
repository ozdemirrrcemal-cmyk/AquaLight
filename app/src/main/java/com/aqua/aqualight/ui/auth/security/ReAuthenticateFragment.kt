package com.aqua.aqualight.ui.auth.security

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentReAuthenticateBinding
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class ReAuthenticateFragment :
    Fragment(R.layout.fragment_re_authenticate) {

    private var _binding: FragmentReAuthenticateBinding? = null
    private val binding get() = _binding!!

    private val auth get() = FirebaseAuth.getInstance()

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    private val baseActivity get() = activity as? BaseActivity

    private lateinit var googleSignInClient: GoogleSignInClient

    // ---------------------------------------------------
    // GOOGLE LAUNCHER
    // ---------------------------------------------------

    private val googleLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode != Activity.RESULT_OK) {

                baseActivity?.showLoading(false)

                return@registerForActivityResult
            }

            try {

                val task =
                    GoogleSignIn.getSignedInAccountFromIntent(result.data)

                val account =
                    task.getResult(ApiException::class.java)

                val credential =
                    GoogleAuthProvider.getCredential(
                        account.idToken,
                        null
                    )

                auth.currentUser
                    ?.reauthenticate(credential)
                    ?.addOnSuccessListener {

                        deleteAccount()
                    }
                    ?.addOnFailureListener {

                        baseActivity?.showLoading(false)

                        DialogManager.showInfoDialog(
                            requireContext(),
                            DialogType.ERROR,
                            title = "Verification Failed",
                            message =
                                it.localizedMessage
                                    ?: "Google verification failed."
                        )
                    }

            } catch (e: Exception) {

                baseActivity?.showLoading(false)

                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = "Google Error",
                    message =
                        e.localizedMessage
                            ?: "Unknown error."
                )
            }
        }

    // ---------------------------------------------------
    // ON VIEW CREATED
    // ---------------------------------------------------

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(view, savedInstanceState)

        _binding =
            FragmentReAuthenticateBinding.bind(view)

        setupGoogle()

        setupUi()
    }

    // ---------------------------------------------------
    // GOOGLE SETUP
    // ---------------------------------------------------

    private fun setupGoogle() {

        val gso =
            GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN
            )
                .requestIdToken(
                    getString(R.string.default_web_client_id)
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
    // UI
    // ---------------------------------------------------

    private fun setupUi() {

        val user = auth.currentUser ?: return

        val isGoogleUser =
            user.providerData.any {
                it.providerId == "google.com"
            }

        if (isGoogleUser) {

            setupGoogleUi()

        } else {

            setupPasswordUi()
        }
    }

    // ---------------------------------------------------
    // GOOGLE UI
    // ---------------------------------------------------

    private fun setupGoogleUi() {

        binding.ivGoogle.visibility = View.VISIBLE

        binding.tvTitle.text =
            "Verify Google Account"

        binding.tvDescription.text =
            "For security reasons, please verify your Google account before deleting your account."

        binding.passwordLayout.visibility =
            View.GONE

        binding.btnContinue.text =
            "Continue with Google"

        binding.btnContinue.setOnClickListener {

            startGoogleReAuthentication()
        }
    }

    // ---------------------------------------------------
    // PASSWORD UI
    // ---------------------------------------------------

    private fun setupPasswordUi() {

        binding.ivGoogle.visibility = View.GONE

        binding.tvTitle.text =
            "Confirm Password"

        binding.tvDescription.text =
            "Please enter your password to continue."

        binding.passwordLayout.visibility =
            View.VISIBLE

        binding.btnContinue.text =
            "Continue"

        binding.btnContinue.setOnClickListener {

            validatePasswordReAuthentication()
        }
    }

    // ---------------------------------------------------
    // GOOGLE REAUTH
    // ---------------------------------------------------

    private fun startGoogleReAuthentication() {

        baseActivity?.showLoading(true)

        googleSignInClient
            .signOut()
            .addOnCompleteListener {

                val signIntent =
                    googleSignInClient.signInIntent

                googleLauncher.launch(signIntent)
            }
    }

    // ---------------------------------------------------
    // PASSWORD REAUTH
    // ---------------------------------------------------

    private fun validatePasswordReAuthentication() {

        val password =
            binding.etPassword.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (password.isBlank()) {

            binding.etPassword.error =
                "Password required"

            return
        }

        val user =
            auth.currentUser ?: return

        val email =
            user.email ?: return

        baseActivity?.showLoading(true)

        val credential =
            EmailAuthProvider.getCredential(
                email,
                password
            )

        user.reauthenticate(credential)
            .addOnSuccessListener {

                deleteAccount()
            }
            .addOnFailureListener {

                baseActivity?.showLoading(false)

                binding.etPassword.error =
                    "Incorrect password"
            }
    }

    // ---------------------------------------------------
    // DELETE ACCOUNT
    // ---------------------------------------------------

    private fun deleteAccount() {

        auth.currentUser
            ?.delete()
            ?.addOnSuccessListener {

                lifecycleScope.launch {

                    userPrefs.clearAllUserData()

                    baseActivity?.showLoading(false)

                    DialogManager.showInfoDialog(
                        requireContext(),
                        DialogType.SUCCESS,
                        title = "Account Deleted",
                        message = "Your account has been deleted successfully.",
                        onDismiss = {

                            navigateToLogin()
                        }
                    )
                }
            }
            ?.addOnFailureListener {

                baseActivity?.showLoading(false)

                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = "Delete Failed",
                    message =
                        it.localizedMessage
                            ?: "Unknown error."
                )
            }
    }

    // ---------------------------------------------------
    // NAVIGATE LOGIN
    // ---------------------------------------------------

    private fun navigateToLogin() {

        val rootNav =
            (
                requireActivity()
                    .supportFragmentManager
                    .findFragmentById(R.id.nav_host)
                        as NavHostFragment
                ).navController

        val opts =
            navOptions {

                popUpTo(R.id.nav_app) {
                    inclusive = true
                }

                launchSingleTop = true
            }

        rootNav.navigate(
            R.id.authContainerFragment,
            null,
            opts
        )
    }

    // ---------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------

    override fun onDestroyView() {

        _binding = null

        super.onDestroyView()
    }
}