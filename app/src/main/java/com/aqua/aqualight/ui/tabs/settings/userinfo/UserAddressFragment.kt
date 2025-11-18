package com.aqua.aqualight.ui.tabs.settings.userinfo

import android.os.Bundle
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUserAddressBinding.bind(view)

        // 📞 CCP → phone editText bağla (format + full number)
        binding.ccpCountry.registerCarrierNumberEditText(binding.etPhoneNumber)

        loadAddressFromPrefs()
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

            // 📍 Ülke: sadece kayıtlıysa set et, yoksa CCP'nin default'u kalsın
            if (prefs.country.isNotBlank()) {
                binding.ccpCountry.setCountryForNameCode(prefs.country.uppercase())
            }

            // 📞 Telefon: kayıtlıysa full numara olarak yükle
            if (prefs.phoneNumber.isNotBlank()) {
                binding.ccpCountry.setFullNumber(prefs.phoneNumber) // "+90555..."
            } else {
                binding.etPhoneNumber.setText("") // ilk girişte boş
            }
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
        val lastName = binding.etLastName.text?.toString()?.trim().orEmpty()
        val city = binding.etCity.text?.toString()?.trim().orEmpty()
        val address = binding.etAddress.text?.toString()?.trim().orEmpty()
        val postCode = binding.etPostCode.text?.toString()?.trim().orEmpty()
        val phoneRaw = binding.etPhoneNumber.text?.toString()?.trim().orEmpty()

        // ISO ülke kodu (TR, US vs.) ve full phone
        val countryIso = binding.ccpCountry.selectedCountryNameCode   // "TR"
        val fullPhone  = binding.ccpCountry.fullNumberWithPlus       // "+90555..."

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
                    .setCountry(countryIso)   // "TR"
                    .setPhoneNumber(fullPhone) // "+90555..."
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