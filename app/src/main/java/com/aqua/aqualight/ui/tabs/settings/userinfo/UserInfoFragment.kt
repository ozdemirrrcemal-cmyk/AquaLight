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
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentUserInfoBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

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

    // ---------------------------------------------------
    // VIEW BINDING
    // ---------------------------------------------------

    private var _binding:
        FragmentUserInfoBinding? = null

    private val binding get() = _binding!!

    // ---------------------------------------------------
    // DATASTORE
    // ---------------------------------------------------

    private val userPrefs by lazy {

        UserPreferencesManager.create(
            requireContext()
        )
    }

    // ---------------------------------------------------
    // ON VIEW CREATED
    // ---------------------------------------------------

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

        observeUserInfo()

        setupClickListeners()

        setupValidationWatcher()
    }

    // ---------------------------------------------------
    // OBSERVE USER INFO
    // ---------------------------------------------------

    private fun observeUserInfo() {

        viewLifecycleOwner.lifecycleScope.launch {

            userPrefs.userPrefsFlow
                .collectLatest { prefs ->

                    if (
                        !isAdded ||
                        _binding == null
                    ) return@collectLatest

                    // NAME

                    binding.tvName.text =
                        prefs.fullName.ifBlank {

                            getString(
                                R.string.user_info_name_default
                            )
                        }

                    // EMAIL

                    binding.tvUserEmail.text =
                        prefs.email.ifBlank {

                            getString(
                                R.string.user_info_email_default
                            )
                        }

                    // USERNAME

                    val username =
                        prefs.username

                    if (
                        username.isNotBlank()
                    ) {

                        if (
                            binding.etUsername.text
                                ?.toString() != username
                        ) {

                            binding.etUsername
                                .setText(username)
                        }

                    } else {

                        binding.etUsername
                            .setText("")
                    }

                    // PROFILE PHOTO

                    if (
                        prefs.profilePhotoUrl
                            .isNotBlank()
                    ) {

                        binding.ivUserPhoto.load(
                            prefs.profilePhotoUrl
                        ) {

                            placeholder(
                                R.drawable.ic_profile_placeholder
                            )

                            error(
                                R.drawable.ic_profile_placeholder
                            )

                            crossfade(true)
                        }

                    } else {

                        binding.ivUserPhoto
                            .setImageResource(
                                R.drawable.ic_profile_placeholder
                            )
                    }
                }
        }
    }

    // ---------------------------------------------------
    // VALIDATION WATCHER
    // ---------------------------------------------------

    private fun setupValidationWatcher() =
        with(binding) {

            etUsername.addTextChangedListener {

                resetUsernameError()
            }
        }

    // ---------------------------------------------------
    // CLICK LISTENERS
    // ---------------------------------------------------

    private fun setupClickListeners() =
        with(binding) {

            // BACK

            binding.appHeader.setupAquaHeader(
    AquaHeaderConfig(
        title = getString(R.string.settings_about_title),
        showBackButton = true,
        onBackClick = {
            findNavController().popBackStack()
        }
    )
)

            // ADDRESS

            rowAddress.setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.action_userInfoFragment_to_userAddressFragment
                    )
            }

            // SAVE

            btnSave.setOnClickListener {

                hideKeyboard()

                etUsername.clearFocus()

                saveUserInfo()
            }
        }

    // ---------------------------------------------------
    // SAVE USER INFO
    // ---------------------------------------------------

    private fun saveUserInfo() {

        val username =
            binding.etUsername.text
                ?.toString()
                ?.trim()
                .orEmpty()

        resetUsernameError()

        // ---------------------------------------------------
        // EMPTY
        // ---------------------------------------------------

        if (
            username.isEmpty()
        ) {

            showUsernameError(
                getString(
                    R.string.user_info_username_empty_message
                )
            )

            return
        }

        // ---------------------------------------------------
        // TOO SHORT
        // ---------------------------------------------------

        if (
            username.length <
            USERNAME_MIN_LENGTH
        ) {

            showUsernameError(
                getString(
                    R.string.user_info_username_too_short
                )
            )

            return
        }
		
        // ---------------------------------------------------
        // INVALID CHARACTERS
        // ---------------------------------------------------

        val usernameRegex =
            Regex("^[a-zA-Z0-9_.]+$")

        if (
            !usernameRegex.matches(
                username
            )
        ) {

            showUsernameError(
                getString(
                    R.string.user_info_username_invalid
                )
            )

            return
        }

        // ---------------------------------------------------
        // SAVE
        // ---------------------------------------------------

        setLoadingState(true)

        viewLifecycleOwner.lifecycleScope.launch {

            try {

                userPrefs.update { prefs ->

                    prefs.toBuilder()
                        .setUsername(username)
                        .build()
                }

                if (
                    !isAdded ||
                    _binding == null
                ) return@launch

                setLoadingState(false)

                showSnackBar(
                    getString(
                        R.string.user_info_saved_success
                    ),
                    BaseActivity.SnackType.SUCCESS
                )

                findNavController()
                    .popBackStack()

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

                setLoadingState(false)

                showSnackBar(
                    getString(
                        R.string.user_info_save_error_message
                    ),
                    BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    // ---------------------------------------------------
    // SHOW USERNAME ERROR
    // ---------------------------------------------------

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

    // ---------------------------------------------------
    // RESET USERNAME ERROR
    // ---------------------------------------------------

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

    // ---------------------------------------------------
    // HIDE KEYBOARD
    // ---------------------------------------------------

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

    // ---------------------------------------------------
    // LOADING STATE
    // ---------------------------------------------------

    private fun setLoadingState(
        loading: Boolean
    ) {

        if (
            !isAdded ||
            _binding == null
        ) return

        binding.btnSave.isEnabled =
            !loading

        (
            requireActivity()
                as? BaseActivity
            )?.showLoading(
            loading
        )
    }

    // ---------------------------------------------------
    // GLOBAL SNACKBAR
    // ---------------------------------------------------

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType
    ) {

        (
            requireActivity()
                as? BaseActivity
            )?.showSnackBar(
            message,
            type
        )
    }

    // ---------------------------------------------------
    // DESTROY
    // ---------------------------------------------------

    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }
}