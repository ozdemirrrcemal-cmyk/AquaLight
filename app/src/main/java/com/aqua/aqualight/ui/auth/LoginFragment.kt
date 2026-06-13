package com.aqua.aqualight.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.navigation.RootNavigator
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.data.auth.GoogleSignInClientFactory
import com.aqua.aqualight.databinding.FragmentLoginBinding
import com.aqua.aqualight.ui.auth.state.AuthActionState
import com.aqua.aqualight.ui.auth.viewmodel.AuthViewModelFactory
import com.aqua.aqualight.ui.auth.viewmodel.LoginViewModel
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var googleSignInClient: GoogleSignInClient

    private val viewModel: LoginViewModel by viewModels {
        AuthViewModelFactory(requireContext())
    }


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

                val token = account.idToken

                if (token.isNullOrBlank()) {
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
				
                viewModel.signInWithGoogleToken(
                    idToken = token
                )
            } catch (e: Exception) {
                Log.e(
                    "LoginFragment",
                    "Google Sign-In failed",
                    e
                )

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

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupGoogleSignIn()
        setupButtonActions()
        observeState()
    }

    private fun setupGoogleSignIn() {
        googleSignInClient =
            GoogleSignInClientFactory.create(
                requireContext()
            )
    }

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

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.state.collect { state ->
                    renderState(state)
                }
            }
        }
    }

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

    private fun renderState(
        state: AuthActionState
    ) {
        val isLoading = state is AuthActionState.Loading

        setFragmentGlobalLoading(isLoading)
        binding.btnGoogleLogin.isEnabled = !isLoading
        binding.btnSignIn.isEnabled = !isLoading
        binding.btnRegister.isEnabled = !isLoading

        when (state) {
            AuthActionState.Authenticated -> {
                navigateToAppGraph()
            }

            is AuthActionState.Message -> {
                DialogManager.showInfoDialog(
                    requireContext(),
                    state.kind.toDialogType(),
                    title = state.title.resolve(requireContext()),
                    message = state.message.resolve(requireContext())
                )
                viewModel.resetState()
            }

            else -> Unit
        }
    }

    private fun AuthActionState.Kind.toDialogType(): DialogType {
        return when (this) {
            AuthActionState.Kind.WARNING -> DialogType.WARNING
            AuthActionState.Kind.ERROR -> DialogType.ERROR
            AuthActionState.Kind.SUCCESS -> DialogType.SUCCESS
        }
    }

    private fun navigateToAppGraph() {
        RootNavigator.openAppGraph(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }
}
