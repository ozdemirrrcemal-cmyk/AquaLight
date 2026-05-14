package com.aqua.aqualight.ui.auth.security

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
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

    companion object {

        const val ARG_ACTION =
            "arg_action"

        const val ACTION_DELETE_ACCOUNT =
            "delete_account"

        const val ACTION_CHANGE_PASSWORD =
            "change_password"

        const val ACTION_CHANGE_EMAIL =
            "change_email"
    }

    private var _binding: FragmentReAuthenticateBinding? = null
    private val binding get() = _binding!!

    private val auth get() = FirebaseAuth.getInstance()

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    private val baseActivity get() = activity as? BaseActivity

    private lateinit var googleSignInClient: GoogleSignInClient

    private var isLoading = false

    private var currentAction =
        ACTION_DELETE_ACCOUNT

    // ---------------------------------------------------
    // GOOGLE LAUNCHER
    // ---------------------------------------------------

    private val googleLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode != Activity.RESULT_OK) {

                baseActivity?.showLoading(false)

                setLoadingState(false)

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

                val user =
                    auth.currentUser
                        ?: return@registerForActivityResult

                user.reauthenticate(credential)
                    .addOnSuccessListener {

                        handleAuthenticatedAction()
                    }
                    .addOnFailureListener {

                        baseActivity?.showLoading(false)

                        setLoadingState(false)

                        DialogManager.showInfoDialog(
                            requireContext(),
                            DialogType.ERROR,
                            title = "Verification Failed",
                            message =
                                it.localizedMessage
                                    ?: getString(
                                        R.string.re_auth_google_failed
                                    )
                        )
                    }

            } catch (e: Exception) {

                baseActivity?.showLoading(false)

                setLoadingState(false)

                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = "Google Error",
                    message =
                        e.localizedMessage
                            ?: getString(
                                R.string.re_auth_unknown_error
                            )
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

        currentAction =
            arguments?.getString(ARG_ACTION)
                ?: ACTION_DELETE_ACCOUNT

        setupGoogle()

        setupBackButton()

        setupUi()
    }

    // ---------------------------------------------------
    // BACK BUTTON
    // ---------------------------------------------------

    private fun setupBackButton() {

        binding.btnBack.setOnClickListener {

            findNavController().popBackStack()
        }
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

        binding.ivGoogle.visibility =
            View.VISIBLE

        binding.tvTitle.text =
            getString(R.string.re_auth_google_title)

        binding.tvDescription.text =
            getString(R.string.re_auth_google_description)

        binding.passwordLayout.visibility =
            View.GONE

        binding.btnContinue.text =
            getString(R.string.re_auth_continue_google)

        binding.btnContinue.setOnClickListener {

            startGoogleReAuthentication()
        }
    }

    // ---------------------------------------------------
    // PASSWORD UI
    // ---------------------------------------------------

    private fun setupPasswordUi() {

        binding.ivGoogle.visibility =
            View.GONE

        binding.tvTitle.text =
            getString(R.string.re_auth_confirm_password)

        binding.tvDescription.text =
            getString(R.string.re_auth_password_description)

        binding.passwordLayout.visibility =
            View.VISIBLE

        binding.btnContinue.text =
            getString(R.string.re_auth_continue)

        binding.etPassword.requestFocus()

        binding.etPassword.doOnTextChanged { _, _, _, _ ->

            binding.etPassword.error = null
        }

        binding.etPassword.setOnEditorActionListener { _, _, _ ->

            validatePasswordReAuthentication()

            true
        }

        binding.btnContinue.setOnClickListener {

            validatePasswordReAuthentication()
        }
    }

    // ---------------------------------------------------
    // GOOGLE REAUTH
    // ---------------------------------------------------

    private fun startGoogleReAuthentication() {

        if (isLoading) return

        baseActivity?.showLoading(true)

        setLoadingState(true)

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

        if (isLoading) return

        val password =
            binding.etPassword.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (password.isBlank()) {

            binding.etPassword.error =
                getString(
                    R.string.re_auth_password_required
                )

            shakeView(binding.passwordLayout)

            return
        }

        val user =
            auth.currentUser ?: return

        val email =
            user.email ?: return

        baseActivity?.showLoading(true)

        setLoadingState(true)

        val credential =
            EmailAuthProvider.getCredential(
                email,
                password
            )

        user.reauthenticate(credential)
            .addOnSuccessListener {

                handleAuthenticatedAction()
            }
            .addOnFailureListener {

                baseActivity?.showLoading(false)

                setLoadingState(false)

                binding.etPassword.error =
                    getString(
                        R.string.re_auth_wrong_password
                    )

                shakeView(binding.passwordLayout)
            }
    }

    // ---------------------------------------------------
    // AUTHENTICATED ACTION
    // ---------------------------------------------------

    private fun handleAuthenticatedAction() {

        when (currentAction) {

            ACTION_DELETE_ACCOUNT -> {

                deleteAccount()
            }

            ACTION_CHANGE_PASSWORD,
            ACTION_CHANGE_EMAIL -> {

                findNavController()
                    .previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(
                        "reauth_success",
                        true
                    )

                findNavController().popBackStack()
            }
        }
    }

    // ---------------------------------------------------
    // DELETE ACCOUNT
    // ---------------------------------------------------

    private fun deleteAccount() {

        val user =
            auth.currentUser ?: return

        user.delete()
            .addOnSuccessListener {

                lifecycleScope.launch {

                    userPrefs.clearAllUserData()

                    baseActivity?.showLoading(false)

                    setLoadingState(false)

                    baseActivity?.showSnackBar(
                        getString(
                            R.string.re_auth_delete_success_message
                        )
                    )

                    binding.root.postDelayed({

                        navigateToLogin()

                    }, 500)
                }
            }
            .addOnFailureListener {

                baseActivity?.showLoading(false)

                setLoadingState(false)

                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = getString(
                        R.string.re_auth_delete_failed_title
                    ),
                    message =
                        it.localizedMessage
                            ?: getString(
                                R.string.re_auth_unknown_error
                            )
                )
            }
    }

    // ---------------------------------------------------
    // LOADING STATE
    // ---------------------------------------------------

    private fun setLoadingState(
        loading: Boolean
    ) {

        isLoading = loading

        binding.btnContinue.isEnabled =
            !loading

        binding.btnContinue.alpha =
            if (loading) 0.6f else 1f

        binding.btnContinue.text =
            if (loading) {

                getString(R.string.loading)

            } else {

                if (binding.ivGoogle.visibility == View.VISIBLE) {

                    getString(
                        R.string.re_auth_continue_google
                    )

                } else {

                    getString(
                        R.string.re_auth_continue
                    )
                }
            }
    }

    // ---------------------------------------------------
    // SHAKE ANIMATION
    // ---------------------------------------------------

    private fun shakeView(
        view: View
    ) {

        view.animate()
            .translationX(20f)
            .setDuration(50)
            .withEndAction {

                view.animate()
                    .translationX(-20f)
                    .setDuration(50)
                    .withEndAction {

                        view.animate()
                            .translationX(10f)
                            .setDuration(50)
                            .withEndAction {

                                view.animate()
                                    .translationX(0f)
                                    .duration = 50
                            }
                    }
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