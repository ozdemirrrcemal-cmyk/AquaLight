package com.aqua.aqualight.ui.auth.security

import android.os.Bundle
import android.view.View
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
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
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

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentReAuthenticateBinding.bind(view)

        setupContinueButton()
    }

    // ---------------------------------------------------------
    // CONTINUE BUTTON
    // ---------------------------------------------------------

    private fun setupContinueButton() {

        binding.btnContinue.setOnClickListener {

            val password =
                binding.etPassword.text?.toString()?.trim().orEmpty()

            if (password.isBlank()) {

                binding.etPassword.error = "Password required"
                return@setOnClickListener
            }

            reAuthenticateAndDelete(password)
        }
    }

    // ---------------------------------------------------------
    // RE-AUTH + DELETE
    // ---------------------------------------------------------

    private fun reAuthenticateAndDelete(password: String) {

        val user = auth.currentUser

        if (user == null) {

            showError("User not found")
            return
        }

        val email = user.email

        if (email.isNullOrBlank()) {

            showError("Email not found")
            return
        }

        baseActivity?.showLoading(true)

        binding.btnContinue.isEnabled = false

        val credential =
            EmailAuthProvider.getCredential(email, password)

        user.reauthenticate(credential)
            .addOnCompleteListener { reAuthTask ->

                if (!isAdded || _binding == null)
                    return@addOnCompleteListener

                if (reAuthTask.isSuccessful) {

                    deleteAccount(user)

                } else {

                    baseActivity?.showLoading(false)

                    binding.btnContinue.isEnabled = true

                    binding.etPassword.error = "Wrong password"
                }
            }
    }

    // ---------------------------------------------------------
    // DELETE ACCOUNT
    // ---------------------------------------------------------

    private fun deleteAccount(user: com.google.firebase.auth.FirebaseUser) {

        user.delete()
            .addOnCompleteListener { deleteTask ->

                if (!isAdded || _binding == null)
                    return@addOnCompleteListener

                baseActivity?.showLoading(false)

                binding.btnContinue.isEnabled = true

                if (deleteTask.isSuccessful) {

                    viewLifecycleOwner.lifecycleScope.launch {

                        userPrefs.clearAllUserData()

                        navigateToLogin()
                    }

                } else {

                    showError(
                        deleteTask.exception?.localizedMessage
                            ?: "Account deletion failed"
                    )
                }
            }
    }

    // ---------------------------------------------------------
    // NAVIGATE LOGIN
    // ---------------------------------------------------------

    private fun navigateToLogin() {

        val rootNav =
            (requireActivity()
                .supportFragmentManager
                .findFragmentById(R.id.nav_host) as NavHostFragment)
                .navController

        val opts = navOptions {

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

    // ---------------------------------------------------------
    // ERROR
    // ---------------------------------------------------------

    private fun showError(message: String) {

        DialogManager.showInfoDialog(
            requireContext(),
            DialogType.ERROR,
            title = "Error",
            message = message
        )
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    override fun onDestroyView() {

        _binding = null

        super.onDestroyView()
    }
}