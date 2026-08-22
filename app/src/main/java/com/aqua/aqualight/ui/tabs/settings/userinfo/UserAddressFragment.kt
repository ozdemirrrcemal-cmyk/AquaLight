package com.aqua.aqualight.ui.tabs.settings.userinfo

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.application.user.UserAddressInput
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentUserAddressBinding
import com.aqua.aqualight.ui.common.bottomsheet.CountryPickerBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UserAddressFragment : Fragment(R.layout.fragment_user_address) {

    companion object {
        private const val FIRST_NAME_MIN = 2
        private const val LAST_NAME_MIN = 2
    }

    private var _binding: FragmentUserAddressBinding? = null
    private val binding get() = _binding!!

    private val userProfileOperations by lazy {
        requireContext().requireAppContainer().userProfileOperations
    }

    private var lastDialCode: String? = null
    private var isFormattingPhone = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUserAddressBinding.bind(view)

        setupHeader()
        loadAddress()
        setupCountryPhoneLink()
        setupPhoneFormatting()
        setupValidationWatchers()
        setupListeners()
        setupCountryPickerClick()
        setupCountryPickerResult()
        setupKeyboardAutoScroll()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_user_address)
            )
        )
    }

    private fun setupKeyboardAutoScroll() {
        val fields = listOf(
            binding.etFirstName,
            binding.etLastName,
            binding.etCity,
            binding.etAddress,
            binding.etPostCode,
            binding.etPhoneNumber
        )

        fields.forEach { editText ->
            editText.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    binding.scrollContent.postDelayed(
                        {
                            binding.scrollContent.smoothScrollTo(
                                0,
                                view.bottom + 400
                            )
                        },
                        250
                    )
                }
            }
            editText.setOnClickListener {
                binding.scrollContent.postDelayed(
                    {
                        binding.scrollContent.smoothScrollTo(
                            0,
                            editText.bottom + 400
                        )
                    },
                    250
                )
            }
        }
    }

    private fun loadAddress() {
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = userProfileOperations.profile.first()
            if (!isAdded || _binding == null) {
                return@launch
            }

            binding.etFirstName.setText(profile.firstName)
            binding.etLastName.setText(profile.lastName)
            binding.etCity.setText(profile.city)
            binding.etAddress.setText(profile.addressLine)
            binding.etPostCode.setText(profile.postCode)

            if (profile.country.isNotBlank()) {
                binding.ccpCountry.setCountryForNameCode(
                    profile.country.uppercase()
                )
            }

            lastDialCode = binding.ccpCountry.selectedCountryCodeWithPlus
            binding.tvCountryValue.text = binding.ccpCountry.selectedCountryName

            isFormattingPhone = true
            val phoneText = profile.phoneNumber.ifBlank {
                "${binding.ccpCountry.selectedCountryCodeWithPlus} "
            }
            binding.etPhoneNumber.setText(phoneText)
            binding.etPhoneNumber.setSelection(phoneText.length)
            isFormattingPhone = false
        }
    }

    private fun setupValidationWatchers() = with(binding) {
        etFirstName.addTextChangedListener {
            clearFieldError(cardFirstName, tvFirstNameError)
        }
        etLastName.addTextChangedListener {
            clearFieldError(cardLastName, tvLastNameError)
        }
        etCity.addTextChangedListener {
            clearFieldError(cardCity, tvCityError)
        }
        etAddress.addTextChangedListener {
            clearFieldError(cardAddressInput, tvAddressError)
        }
        etPostCode.addTextChangedListener {
            clearFieldError(cardPostCode, tvPostCodeError)
        }
        etPhoneNumber.addTextChangedListener {
            clearFieldError(cardPhone, tvPhoneError)
        }
    }

    private fun setupCountryPhoneLink() = with(binding) {
        lastDialCode = ccpCountry.selectedCountryCodeWithPlus
        ccpCountry.setOnCountryChangeListener {
            val newDialCode = ccpCountry.selectedCountryCodeWithPlus
            val current = etPhoneNumber.text?.toString().orEmpty()
            val oldDialCode = lastDialCode
            val numberWithoutOldDialCode = if (
                !oldDialCode.isNullOrBlank() && current.startsWith(oldDialCode)
            ) {
                current.removePrefix(oldDialCode).trimStart()
            } else {
                current
            }
            val newText = if (numberWithoutOldDialCode.isBlank()) {
                "$newDialCode "
            } else {
                "$newDialCode $numberWithoutOldDialCode"
            }

            isFormattingPhone = true
            etPhoneNumber.setText(newText)
            etPhoneNumber.setSelection(newText.length)
            isFormattingPhone = false
            lastDialCode = newDialCode
            tvCountryValue.text = ccpCountry.selectedCountryName
        }
    }

    private fun setupPhoneFormatting() {
        val phoneUtil = PhoneNumberUtil.getInstance()
        binding.etPhoneNumber.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    text: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    text: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) = Unit

                override fun afterTextChanged(text: Editable?) {
                    if (isFormattingPhone) {
                        return
                    }

                    val currentText = text?.toString() ?: return
                    if (currentText.isBlank()) {
                        return
                    }

                    val countryIso = binding.ccpCountry.selectedCountryNameCode
                    val dialCode = lastDialCode
                        ?: binding.ccpCountry.selectedCountryCodeWithPlus
                    val rawNumber = currentText
                        .removePrefix(dialCode)
                        .trimStart()
                    val digits = rawNumber.filter(Char::isDigit)

                    if (digits.isEmpty()) {
                        replacePhoneText("$dialCode ")
                        return
                    }

                    val maximumLength = runCatching {
                        phoneUtil.getExampleNumber(countryIso)
                            ?.nationalNumber
                            ?.toString()
                            ?.length
                    }.getOrNull() ?: 15
                    val formatter = phoneUtil.getAsYouTypeFormatter(countryIso)
                    var nationalFormatted = ""
                    digits.take(maximumLength).forEach { digit ->
                        nationalFormatted = formatter.inputDigit(digit)
                    }
                    replacePhoneText("$dialCode $nationalFormatted")
                }
            }
        )
    }

    private fun replacePhoneText(value: String) {
        isFormattingPhone = true
        binding.etPhoneNumber.setText(value)
        binding.etPhoneNumber.setSelection(value.length)
        isFormattingPhone = false
    }

    private fun setupCountryPickerClick() {
        binding.cardCountry.setOnClickListener {
            showCountryBottomSheet()
        }
    }

    private fun setupCountryPickerResult() {
        parentFragmentManager.setFragmentResultListener(
            CountryPickerBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val iso = bundle.getString(
                CountryPickerBottomSheet.RESULT_COUNTRY_ISO
            ) ?: return@setFragmentResultListener
            binding.ccpCountry.setCountryForNameCode(iso)
            binding.tvCountryValue.text = binding.ccpCountry.selectedCountryName
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            hideKeyboard()
            saveAddress()
        }
    }

    private fun saveAddress() {
        val firstName = binding.etFirstName.text?.toString()?.trim().orEmpty()
        val lastName = binding.etLastName.text?.toString()?.trim().orEmpty()
        val city = binding.etCity.text?.toString()?.trim().orEmpty()
        val addressLine = binding.etAddress.text?.toString()?.trim().orEmpty()
        val postCode = binding.etPostCode.text?.toString()?.trim().orEmpty()
        val phoneRaw = binding.etPhoneNumber.text?.toString()?.trim().orEmpty()
        val countryIso = binding.ccpCountry.selectedCountryNameCode

        clearAllErrors()
        var hasError = false

        if (firstName.length < FIRST_NAME_MIN) {
            showFieldError(
                binding.cardFirstName,
                binding.tvFirstNameError,
                getString(R.string.address_error_first_name_required)
            )
            hasError = true
        }
        if (lastName.length < LAST_NAME_MIN) {
            showFieldError(
                binding.cardLastName,
                binding.tvLastNameError,
                getString(R.string.address_error_last_name_required)
            )
            hasError = true
        }
        if (city.isBlank()) {
            showFieldError(
                binding.cardCity,
                binding.tvCityError,
                getString(R.string.address_error_city_required)
            )
            hasError = true
        }
        if (addressLine.isBlank()) {
            showFieldError(
                binding.cardAddressInput,
                binding.tvAddressError,
                getString(R.string.address_error_address_required)
            )
            hasError = true
        }
        if (postCode.isBlank()) {
            showFieldError(
                binding.cardPostCode,
                binding.tvPostCodeError,
                getString(R.string.address_error_postcode_required)
            )
            hasError = true
        }
        if (phoneRaw.isBlank()) {
            showFieldError(
                binding.cardPhone,
                binding.tvPhoneError,
                getString(R.string.address_error_phone_required)
            )
            hasError = true
        }
        if (hasError) {
            return
        }

        val phoneUtil = PhoneNumberUtil.getInstance()
        val parsedNumber = try {
            phoneUtil.parse(phoneRaw, countryIso)
        } catch (_: NumberParseException) {
            showInvalidPhoneError()
            return
        }
        if (!phoneUtil.isValidNumberForRegion(parsedNumber, countryIso)) {
            showInvalidPhoneError()
            return
        }

        val formattedPhone = phoneUtil.format(
            parsedNumber,
            PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
        )

        setLoadingState(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                userProfileOperations.saveAddress(
                    UserAddressInput(
                        firstName = firstName,
                        lastName = lastName,
                        city = city,
                        addressLine = addressLine,
                        postCode = postCode,
                        phoneNumber = formattedPhone,
                        country = countryIso
                    )
                )

                if (!isAdded || _binding == null) {
                    return@launch
                }

                setLoadingState(false)
                showSnackBar(
                    getString(R.string.user_info_saved_success),
                    BaseActivity.SnackType.SUCCESS
                )
                findNavController().popBackStack()
            } catch (_: Exception) {
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

    private fun showInvalidPhoneError() {
        showFieldError(
            binding.cardPhone,
            binding.tvPhoneError,
            getString(R.string.address_error_phone_invalid)
        )
    }

    private fun showFieldError(
        card: MaterialCardView,
        errorText: MaterialTextView,
        message: String
    ) {
        card.strokeColor = resources.getColor(R.color.snackbar_error, null)
        card.strokeWidth = 2
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun clearFieldError(
        card: MaterialCardView,
        errorText: MaterialTextView
    ) {
        card.strokeColor = resources.getColor(
            R.color.aqua_card_outline_subtle,
            null
        )
        card.strokeWidth = 1
        errorText.visibility = View.GONE
    }

    private fun clearAllErrors() = with(binding) {
        clearFieldError(cardFirstName, tvFirstNameError)
        clearFieldError(cardLastName, tvLastNameError)
        clearFieldError(cardCity, tvCityError)
        clearFieldError(cardAddressInput, tvAddressError)
        clearFieldError(cardPostCode, tvPostCodeError)
        clearFieldError(cardPhone, tvPhoneError)
    }

    private fun setLoadingState(loading: Boolean) {
        if (!isAdded || _binding == null) {
            return
        }
        binding.btnSave.isEnabled = !loading
        setFragmentGlobalLoading(loading)
    }

    private fun hideKeyboard() {
        val inputMethodManager = requireContext().getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.root.clearFocus()
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType
    ) {
        (requireActivity() as? BaseActivity)?.showSnackBar(message, type)
    }

    private fun showCountryBottomSheet() {
        CountryPickerBottomSheet.newInstance(
            binding.ccpCountry.selectedCountryNameCode
        ).show(
            parentFragmentManager,
            CountryPickerBottomSheet.TAG
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
