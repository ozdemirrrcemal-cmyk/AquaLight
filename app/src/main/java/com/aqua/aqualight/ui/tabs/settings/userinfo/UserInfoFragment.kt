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
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentUserInfoBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UserInfoFragment :
    Fragment(R.layout.fragment_user_info) {

    companion object {

        private const val TAG =
            "UserInfoFragment"

        private const val USERNAME_MIN_LENGTH =
            3

        private const val USERNAME_MAX_LENGTH =
            20
    }

    private var _binding:
        FragmentUserInfoBinding? = null

    private val binding get() = _binding!!

    private val userPrefs by lazy {
        UserPreferencesManager.create(
            requireContext()
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentUserInfoBinding.bind(view)

        setupHeader()
        observeUserInfo()
        setupClickListeners()
        setupValidationWatcher()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun observeUserInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.userPrefsFlow.collectLatest { prefs ->

                if (
                    !isAdded ||
                    _binding == null
                ) return@collectLatest

                binding.tvName.text =
                    prefs.fullName.ifBlank {
                        getString(
                            R.string.user_info_name_default
                        )
                    }

                binding.tvUserEmail.text =
                    prefs.email.ifBlank {
                        getString(
                            R.string.user_info_email_default
                        )
                    }

                val username =
                    prefs.username

                if (username.isNotBlank()) {
                    if (
                        binding.etUsername.text
                            ?.toString() != username
                    ) {
                        binding.etUsername.setText(
                            username
                        )
                    }
                } else {
                    binding.etUsername.setText("")
                }

                if (prefs.profilePhotoUrl.isNotBlank()) {
                    binding.ivUserPhoto.load(
                        prefs.profilePhotoUrl
                    ) {
                        placeholder(
                            R.drawable.ic_profile_placeholder
                        )

                        error(
                            R.drawable.ic_profile_placeholder
                        )

                        crossfade(
                            true
                        )
                    }
                } else {
                    binding.ivUserPhoto.setImageResource(
                        R.drawable.ic_profile_placeholder
                    )
                }
            }
        }
    }

    private fun setupValidationWatcher() =
        with(binding) {

            etUsername.addTextChangedListener {
                resetUsernameError()
            }
        }

    private fun setupClickListeners() =
    with(binding) {

        rowAddress.setOnClickListener {
            findNavController().navigate(
                UserInfoFragmentDirections.actionUserInfoFragmentToUserAddressFragment()
            )
        }

        btnSave.setOnClickListener {
            hideKeyboard()

            etUsername.clearFocus()

            saveUserInfo()
        }
    }

    private fun saveUserInfo() {
        val username =
            binding.etUsername.text
                ?.toString()
                ?.trim()
                .orEmpty()

        resetUsernameError()

        if (username.isEmpty()) {
            showUsernameError(
                getString(
                    R.string.user_info_username_empty_message
                )
            )

            return
        }

        if (username.length < USERNAME_MIN_LENGTH) {
            showUsernameError(
                getString(
                    R.string.user_info_username_too_short
                )
            )

            return
        }

        val usernameRegex =
            Regex("^[a-zA-Z0-9_.]+$")

        if (!usernameRegex.matches(username)) {
            showUsernameError(
                getString(
                    R.string.user_info_username_invalid
                )
            )

            return
        }

        setLoadingState(
            true
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                userPrefs.updateUsername(
                    username
                )

                if (
                    !isAdded ||
                    _binding == null
                ) return@launch

                setLoadingState(
                    false
                )

                showSnackBar(
                    getString(
                        R.string.user_info_saved_success
                    ),
                    BaseActivity.SnackType.SUCCESS
                )

                findNavController().popBackStack()

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Save user info failed",
                    e
                )

                if (
                    !isAdded ||
                    _binding == null
                ) return@launch

                setLoadingState(
                    false
                )

                showSnackBar(
                    getString(
                        R.string.user_info_save_error_message
                    ),
                    BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    private fun showUsernameError(
        message: String
    ) {
        binding.tvUsernameError.text =
            message

        binding.tvUsernameError.visibility =
            View.VISIBLE

        binding.cardUsername.strokeColor =
            resources.getColor(
                R.color.snackbar_error,
                null
            )

        binding.cardUsername.strokeWidth =
            2
    }

    private fun resetUsernameError() {
        binding.tvUsernameError.visibility =
            View.GONE

        binding.cardUsername.strokeColor =
            resources.getColor(
                R.color.card_stroke,
                null
            )

        binding.cardUsername.strokeWidth =
            1
    }

    private fun hideKeyboard() {
        val imm =
            requireContext()
                .getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as InputMethodManager

        imm.hideSoftInputFromWindow(
            binding.etUsername.windowToken,
            0
        )
    }

    private fun setLoadingState(
        loading: Boolean
    ) {
        if (
            !isAdded ||
            _binding == null
        ) return

        binding.btnSave.isEnabled =
            !loading

        setFragmentGlobalLoading(
            loading
        )
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType
    ) {
        (requireActivity() as? BaseActivity)?.showSnackBar(
            message,
            type
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }
}
