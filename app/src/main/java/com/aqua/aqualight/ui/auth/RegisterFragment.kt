package com.aqua.aqualight.ui.auth

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentRegisterBinding
import com.aqua.aqualight.ui.auth.state.AuthActionState
import com.aqua.aqualight.ui.auth.viewmodel.RegisterViewModel
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.navigation.RootNavigator
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels {
        requireContext().requireAppContainer().authViewModelFactory
    }

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

            configureTermsAgreementLink()

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
                        .orEmpty(),
                    termsAccepted = checkTermsAccepted.isChecked
                )
            }

            btnReturnToSignIn.setOnClickListener {
                findNavController().popBackStack()
            }
        }

    private fun configureTermsAgreementLink() {
        val agreementText = getString(R.string.legal_accept_terms)
        val linkText = getString(R.string.legal_accept_terms_link)
        val linkStart = agreementText.indexOf(linkText)
        check(linkStart >= 0) {
            "The terms agreement link text must be present in the agreement text."
        }

        val linkedAgreement = SpannableString(agreementText).apply {
            setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        findNavController().navigate(
                            RegisterFragmentDirections
                                .actionRegisterFragmentToLegalDocumentFragment()
                        )
                    }

                    override fun updateDrawState(drawState: android.text.TextPaint) {
                        drawState.color = ContextCompat.getColor(
                            requireContext(),
                            R.color.aqua_accent_aqua
                        )
                        drawState.isUnderlineText = true
                    }
                },
                linkStart,
                linkStart + linkText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        binding.checkTermsAccepted.apply {
            text = linkedAgreement
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = ContextCompat.getColor(
                requireContext(),
                android.R.color.transparent
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

    private fun renderState(
        state: AuthActionState
    ) {
        val isLoading = state is AuthActionState.Loading

        setFragmentGlobalLoading(isLoading)
        binding.btnRegister.isEnabled = !isLoading
        binding.checkTermsAccepted.isEnabled = !isLoading
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
        RootNavigator.openAppGraph(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }
}
