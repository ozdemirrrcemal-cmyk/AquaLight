package com.aqua.aqualight.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentRegisterBinding
import com.aqua.aqualight.ui.auth.state.AuthActionState
import com.aqua.aqualight.ui.auth.viewmodel.AuthViewModelFactory
import com.aqua.aqualight.ui.auth.viewmodel.RegisterViewModel
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels {
        AuthViewModelFactory(requireContext())
    }

    private val baseActivity
        get() = activity as? BaseActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding =
            FragmentRegisterBinding.inflate(
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

        setupUI()
        observeState()
    }

    private fun setupUI() =
        with(binding) {

            btnRegister.setOnClickListener {
                viewModel.register(
                    email = emailEditText.text
                        ?.toString()
                        .orEmpty(),
                    password = passwordEditText.text
                        ?.toString()
                        .orEmpty(),
                    repeatPassword = etPasswordRepeat.text
                        ?.toString()
                        .orEmpty()
                )
            }

            btnReturnToSignIn.setOnClickListener {
                findNavController().popBackStack()
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

    private fun renderState(
        state: AuthActionState
    ) {
        val isLoading = state is AuthActionState.Loading

        baseActivity?.showLoading(isLoading)
        binding.btnRegister.isEnabled = !isLoading
        binding.btnRegister.text = getString(
            if (isLoading) {
                R.string.register_loading
            } else {
                R.string.register_button
            }
        )

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

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }
}
