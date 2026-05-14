package com.aqua.aqualight.ui.tabs.settings.userinfo

import android.os.Bundle
import android.util.Log
import android.view.View
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
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentUserInfoBinding
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

                // USERNAME CARD RESET
                cardUsername.strokeColor =
                    resources.getColor(
                        R.color.card_stroke,
                        null
                    )
            }
        }

    // ---------------------------------------------------
    // CLICK LISTENERS
    // ---------------------------------------------------

    private fun setupClickListeners() =
        with(binding) {

            // BACK
            btnBack.setOnClickListener {

                findNavController()
                    .popBackStack()
            }

            // ADDRESS
            rowAddress.setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.userAddressFragment
                    )
            }

            // SAVE
            btnSave.setOnClickListener {

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

        // RESET CARD
        resetUsernameCardState()

        // ---------------------------------------------------
        // EMPTY
        // ---------------------------------------------------

        if (username.isEmpty()) {

            showUsernameErrorState()

            showSnackBar(
                getString(
                    R.string.user_info_username_empty_message
                ),
                BaseActivity.SnackType.WARNING
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

            showUsernameErrorState()

            showSnackBar(
                getString(
                    R.string.user_info_username_too_short
                ),
                BaseActivity.SnackType.WARNING
            )

            return
        }

        // ---------------------------------------------------
        // TOO LONG
        // ---------------------------------------------------

        if (
            username.length >
            USERNAME_MAX_LENGTH
        ) {

            showUsernameErrorState()

            showSnackBar(
                getString(
                    R.string.user_info_username_too_long
                ),
                BaseActivity.SnackType.WARNING
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

            showUsernameErrorState()

            showSnackBar(
                getString(
                    R.string.user_info_username_invalid
                ),
                BaseActivity.SnackType.WARNING
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
    // USERNAME ERROR STATE
    // ---------------------------------------------------

    private fun showUsernameErrorState() {

        binding.cardUsername.strokeColor =
            resources.getColor(
                R.color.snackbar_error,
                null
            )
    }

    // ---------------------------------------------------
    // RESET USERNAME CARD
    // ---------------------------------------------------

    private fun resetUsernameCardState() {

        binding.cardUsername.strokeColor =
            resources.getColor(
                R.color.card_stroke,
                null
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

        _binding =
            null
    }
}