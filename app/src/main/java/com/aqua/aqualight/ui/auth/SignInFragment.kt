package com.aqua.aqualight.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentSigninBinding
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class SignInFragment : Fragment() {

    private var _binding: FragmentSigninBinding? = null
    private val binding get() = _binding!!

    private val auth = Firebase.auth

    private val userPrefs by lazy {
        UserPreferencesManager.create(
            requireContext()
        )
    }

    private val baseActivity
        get() = activity as? BaseActivity

    // ---------------------------------------------------
    // ON CREATE VIEW
    // ---------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding =
            FragmentSigninBinding.inflate(
                inflater,
                container,
                false
            )

        setupUI()

        return binding.root
    }

    // ---------------------------------------------------
    // UI
    // ---------------------------------------------------

    private fun setupUI() =
        with(binding) {

            btnLogin.setOnClickListener {

                handleSignIn()
            }

            tvForgotPassword.setOnClickListener {

                findNavController().navigate(
                    R.id.action_signInFragment_to_resetPasswordFragment
                )
            }

            btnBack.setOnClickListener {

                findNavController().popBackStack()
            }
        }

    // ---------------------------------------------------
    // SIGN IN
    // ---------------------------------------------------

    private fun handleSignIn() {

        val email =
            binding.emailEditText.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val password =
            binding.passwordEditText.text
                ?.toString()
                ?.trim()
                .orEmpty()

        baseActivity?.showLoading(true)

        lifecycleScope.launch {

            // -------------------------------
            // EMPTY CHECK
            // -------------------------------

            if (
                email.isEmpty() ||
                password.isEmpty()
            ) {

                baseActivity?.showLoading(false)

                showWarning(
                    R.string.signin_empty_fields_title,
                    R.string.signin_empty_fields_message
                )

                return@launch
            }

            // -------------------------------
            // EMAIL FORMAT
            // -------------------------------

            if (
                !Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches()
            ) {

                baseActivity?.showLoading(false)

                showWarning(
                    R.string.invalid_email_title,
                    R.string.invalid_email
                )

                return@launch
            }

            // -------------------------------
            // LOADING UI
            // -------------------------------

            binding.btnLogin.isEnabled =
                false

            binding.btnLogin.text =
                getString(
                    R.string.signin_loading
                )

            // -------------------------------
            // FIREBASE LOGIN
            // -------------------------------

            auth.signInWithEmailAndPassword(
                email,
                password
            )
                .addOnCompleteListener { task ->

                    baseActivity?.showLoading(false)

                    binding.btnLogin.isEnabled =
                        true

                    binding.btnLogin.text =
                        getString(
                            R.string.signin_login_button
                        )

                    // -----------------------
                    // SUCCESS
                    // -----------------------

                    if (task.isSuccessful) {

                        val user =
                            task.result?.user

                        if (user != null) {

                            viewLifecycleOwner
                                .lifecycleScope
                                .launch {

                                    try {

                                        saveSession(user)

                                        // ✅ Direkt uygulamaya geç
                                        navigateToAppGraph()

                                    } catch (e: Exception) {

                                        DialogManager.showInfoDialog(
                                            requireContext(),
                                            DialogType.ERROR,
                                            title = getString(
                                                R.string.session_save_error_title
                                            ),
                                            message =
                                                e.localizedMessage
                                                    ?: getString(
                                                        R.string.session_save_error_fallback
                                                    )
                                        )
                                    }
                                }

                        } else {

                            DialogManager.showInfoDialog(
                                requireContext(),
                                DialogType.ERROR,
                                title = getString(
                                    R.string.signin_failed_title
                                ),
                                message = getString(
                                    R.string.login_user_info_unavailable
                                )
                            )
                        }

                    } else {

                        // -----------------------
                        // ERROR
                        // -----------------------

                        val errorMsg =
                            task.exception
                                ?.localizedMessage
                                ?: getString(
                                    R.string.signin_failed_default
                                )

                        DialogManager.showInfoDialog(
                            requireContext(),
                            DialogType.ERROR,
                            title = getString(
                                R.string.signin_failed_title
                            ),
                            message = errorMsg
                        )
                    }
                }
        }
    }

    // ---------------------------------------------------
    // WARNING DIALOG
    // ---------------------------------------------------

    private fun showWarning(
        titleRes: Int,
        msgRes: Int
    ) {

        DialogManager.showInfoDialog(
            requireContext(),
            DialogType.WARNING,
            title = getString(titleRes),
            message = getString(msgRes)
        )
    }

    // ---------------------------------------------------
    // SAVE SESSION
    // ---------------------------------------------------

    private suspend fun saveSession(
        user: FirebaseUser
    ) {

        // 🔐 Session
        userPrefs.saveUserSession(
            idToken = user.uid,
            isLoggedIn = true
        )

        // 👤 Profile
        userPrefs.saveProfile(
            email = user.email ?: "",
            username = null,
            fullName = null,
            photoUrl = null
        )
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