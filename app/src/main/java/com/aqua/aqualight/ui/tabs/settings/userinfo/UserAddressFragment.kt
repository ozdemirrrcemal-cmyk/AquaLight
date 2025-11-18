package com.aqua.aqualight.ui.tabs.settings.userinfo

import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentUserAddressBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UserAddressFragment : Fragment(R.layout.fragment_user_address) {

    private var _binding: FragmentUserAddressBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    // Son kullanılan ülke kodu, telefon alanından eski kodu sökebilmek için
    private var lastDialCode: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUserAddressBinding.bind(view)

        loadAddressFromPrefs()
        setupCountryPhoneLink()
        setupListeners()
    }

    /** 🔹 DataStore'dan adres bilgilerini yükle */
    private fun loadAddressFromPrefs() {
        viewLifecycleOwner.lifecycleScope.launch {
            val prefs = userPrefs.userPrefsFlow.first()

            binding.etFirstName.setText(prefs.firstName)
            binding.etLastName.setText(prefs.lastName)
            binding.etCity.setText(prefs.city)
            binding.etAddress.setText(prefs.addressLine)
            binding.etPostCode.setText(prefs.postCode)

            // 📍 Ülke – sadece kayıtlıysa set et, yoksa CCP'nin default'u kalsın
            if (prefs.country.isNotBlank()) {
                binding.ccpCountry.setCountryForNameCode(prefs.country.uppercase())
            }
            // Şu an seçili ülkenin kodunu sakla
            lastDialCode = binding.ccpCountry.selectedCountryCodeWithPlus

            // 📞 Telefon – kayıtlıysa aynen göster, yoksa boş
            if (prefs.phoneNumber.isNotBlank()) {
                binding.etPhoneNumber.setText(prefs.phoneNumber)
                binding.etPhoneNumber.setSelection(prefs.phoneNumber.length)
            } else {
                binding.etPhoneNumber.setText("")
            }
        }
    }

    /** 🔹 Ülke değişince telefon alanına kodu yaz */
    private fun setupCountryPhoneLink() = with(binding) {
        // İlk değer
        lastDialCode = ccpCountry.selectedCountryCodeWithPlus

        ccpCountry.setOnCountryChangeListener {
            val newDialCode = ccpCountry.selectedCountryCodeWithPlus  // ör: +90
            val current = etPhoneNumber.text?.toString().orEmpty()

            // Eski ülke kodunu baştan sök
            val withoutOld = if (!lastDialCode.isNullOrBlank() && current.startsWith(lastDialCode!!)) {
                current.removePrefix(lastDialCode!!).trimStart()
            } else {
                current
            }

            // Yeni metni kur: "+90 " + kalan numara (varsa)
            val newText = if (withoutOld.isBlank()) {
                "$newDialCode "
            } else {
                "$newDialCode $withoutOld"
            }

            etPhoneNumber.setText(newText)
            etPhoneNumber.setSelection(newText.length)

            // Son kodu güncelle
            lastDialCode = newDialCode
        }
    }

    /** 🔹 Listener'lar */
    private fun setupListeners() = with(binding) {
        btnBack.setOnClickListener { findNavController().popBackStack() }
        btnCancel.setOnClickListener { findNavController().popBackStack() }
        btnSave.setOnClickListener { saveAddress() }
    }

    /** 🔹 Kaydet — TÜM alanlar için boşluk kontrolü, inline error */
    private fun saveAddress() {
        val firstName = binding.etFirstName.text?.toString()?.trim().orEmpty()
        val lastName  = binding.etLastName.text?.toString()?.trim().orEmpty()
        val city      = binding.etCity.text?.toString()?.trim().orEmpty()
        val address   = binding.etAddress.text?.toString()?.trim().orEmpty()
        val postCode  = binding.etPostCode.text?.toString()?.trim().orEmpty()
        val phoneRaw  = binding.etPhoneNumber.text?.toString()?.trim().orEmpty()

        val countryIso = binding.ccpCountry.selectedCountryNameCode   // "TR"

        // Eski error’ları temizle
        binding.etFirstName.error = null
        binding.etLastName.error = null
        binding.etCity.error = null
        binding.etAddress.error = null
        binding.etPostCode.error = null
        binding.etPhoneNumber.error = null

        var hasError = false

        if (firstName.isEmpty()) {
            binding.etFirstName.error =
                getString(R.string.address_error_first_name_required)
            hasError = true
        }
        if (lastName.isEmpty()) {
            binding.etLastName.error =
                getString(R.string.address_error_last_name_required)
            hasError = true
        }
        if (city.isEmpty()) {
            binding.etCity.error =
                getString(R.string.address_error_city_required)
            hasError = true
        }
        if (address.isEmpty()) {
            binding.etAddress.error =
                getString(R.string.address_error_address_required)
            hasError = true
        }
        if (postCode.isEmpty()) {
            binding.etPostCode.error =
                getString(R.string.address_error_postcode_required)
            hasError = true
        }
        if (phoneRaw.isEmpty()) {
            binding.etPhoneNumber.error =
                getString(R.string.address_error_phone_required)
            hasError = true
        }

        if (hasError) return

        // Telefonu ülke koduna göre formatla (mümkünse)
        val formattedPhone =
            PhoneNumberUtils.formatNumber(phoneRaw, countryIso) ?: phoneRaw

        val fullName = "$firstName $lastName"

        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.update { prefs ->
                prefs.toBuilder()
                    .setFirstName(firstName)
                    .setLastName(lastName)
                    .setFullName(fullName)
                    .setCity(city)
                    .setAddressLine(address)
                    .setPostCode(postCode)
                    .setCountry(countryIso)          // "TR"
                    .setPhoneNumber(formattedPhone)   // "+90 5xx ..." formatlı
                    .build()
            }
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}