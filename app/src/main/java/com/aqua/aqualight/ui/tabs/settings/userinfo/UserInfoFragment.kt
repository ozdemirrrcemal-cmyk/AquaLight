package com.aqua.aqualight.ui.tabs.settings.userinfo

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentUserInfoBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UserInfoFragment : Fragment(R.layout.fragment_user_info) {

    companion object {
        private const val TAG = "UserInfoFragment"
        private const val USERNAME_MIN_LENGTH = 3
        private const val USERNAME_MAX_LENGTH = 20
    }

    private var _binding: FragmentUserInfoBinding? = null
    private val binding get() = _binding!!

    private val userProfileOperations by lazy {
        requireContext().requireAppContainer().userProfileOperations
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUserInfoBinding.bind(view)

        setupHeader()
        observeUserInfo()
        setupClickListeners()
        setupValidationWatcher()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_user_info)
            )
        )
    }

    private fun observeUserInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            userProfileOperations.profile.collectLatest { profile ->
                if (!isAdded || _binding == null) {
                    return@collectLatest
                }

                binding.tvName.text = profile.fullName.ifBlank {
                    getString(R.string.user_info_name_default)
                }
                binding.tvUserEmail.text = profile.email.ifBlank {
                    getString(R.string.user_info_email_default)
                }

                if (binding.etUsername.text?.toString() != profile.username) {
                    binding.etUsername.setText(profile.username)
                }

                if (profile.profilePhotoUrl.isNotBlank()) {
                    binding.ivUserPhoto.load(profile.profilePhotoUrl) {
                        placeholder(R.drawable.ic_profile_placeholder)
                        error(R.drawable.ic_profile_placeholder)
                        crossfade(true)
                    }
                } else {
                    binding.ivUserPhoto.setImageResource(
                        R.drawable.ic_profile_placeholder
                    )
                }
            }
        }
    }

    private fun setupValidationWatcher() {
        binding.etUsername.addTextChangedListener {
            resetUsernameError()
        }
    }

    private fun setupClickListeners() = with(binding) {
        rowAddress.setOnClickListener {
            findNavController().navigate(
                UserInfoFragmentDirections
                    .actionUserInfoFragmentToUserAddressFragment()
            )
        }

        btnSave.setOnClickListener {
            hideKeyboard()
            etUsername.clearFocus()
            saveUserInfo()
        }
    }

    private fun saveUserInfo() {
        val username = binding.etUsername.text
            ?.toString()
            ?.trim()
            .orEmpty()

        resetUsernameError()

        if (username.isEmpty()) {
            showUsernameError(
                getString(R.string.user_info_username_empty_message)
            )
            return
        }

        if (username.length < USERNAME_MIN_LENGTH) {
            showUsernameError(
                getString(R.string.user_info_username_too_short)
            )
            return
        }

        if (!Regex("^[a-zA-Z0-9_.]+$").matches(username)) {
            showUsernameError(
                getString(R.string.user_info_username_invalid)
            )
            return
        }

        setLoadingState(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                userProfileOperations.updateUsername(username)

                if (!isAdded || _binding == null) {
                    return@launch
                }

                setLoadingState(false)
                showSnackBar(
                    getString(R.string.user_info_saved_success),
                    BaseActivity.SnackType.SUCCESS
                )
                findNavController().popBackStack()
            } catch (error: Exception) {
                Log.e(TAG, "Save user info failed", error)

                if (!isAdded || _binding == null) {
                    return@launch
                }

                setLoadingState(false)
                showSnackBar(
                    getString(R.string.user_info_save_error_message),
                    BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    private fun showUsernameError(message: String) {
        binding.tvUsernameError.text = message
        binding.tvUsernameError.visibility = View.VISIBLE
        binding.cardUsername.strokeColor = resources.getColor(
            R.color.snackbar_error,
            null
        )
        binding.cardUsername.strokeWidth = 2
    }

    private fun resetUsernameError() {
        binding.tvUsernameError.visibility = View.GONE
        binding.cardUsername.strokeColor = resources.getColor(
            R.color.aqua_card_outline_subtle,
            null
        )
        binding.cardUsername.strokeWidth = 1
    }

    private fun hideKeyboard() {
        val inputMethodManager = requireContext().getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(
            binding.etUsername.windowToken,
            0
        )
    }

    private fun setLoadingState(loading: Boolean) {
        if (!isAdded || _binding == null) {
            return
        }
        binding.btnSave.isEnabled = !loading
        setFragmentGlobalLoading(loading)
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType
    ) {
        (requireActivity() as? BaseActivity)?.showSnackBar(message, type)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
