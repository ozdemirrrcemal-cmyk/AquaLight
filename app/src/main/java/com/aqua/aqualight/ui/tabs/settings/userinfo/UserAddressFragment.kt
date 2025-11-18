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

        loadAddressFromPrefs()
        setupCountryPhoneLink()
        setupListeners()
    }

    /** 🔹 DataStore'dan adres bilgilerini yükle */
    private fun loadAddressFromPrefs() {
        viewLifecycleOwner.lifecycleScope.launch {
            val prefs = userPrefs.userPrefsFlow.first()

            // Text alanları
            binding.etFirstName.setText(prefs.firstName)
            binding.etLastName.setText(prefs.lastName)
            binding.etCity.setText(prefs.city)
            binding.etAddress.setText(prefs.addressLine)
            binding.etPostCode.setText(prefs.postCode)
            binding.etPhoneNumber.setText(prefs.phoneNumber)

            // Country ISO code (ör: "TR") kaydettiğimizi varsayıyoruz
            if (prefs.country.isNotBlank()) {
                // CountryCodePicker case'e çok takılmıyor ama garanti olsun diye upper yapıyoruz
                binding.ccpCountry.setCountryForNameCode(prefs.country.uppercase())
            }
        }
    }

    /** 🔹 Ülke değişince telefon kodunu otomatik güncelle */
    private fun setupCountryPhoneLink() = with(binding) {
        ccpCountry.setOnCountryChangeListener {
            val dialCode = ccpCountry.selectedCountryCodeWithPlus  // ör: +90
            val currentPhone = etPhoneNumber.text?.toString().orEmpty()

            val newValue = if (currentPhone.startsWith("+")) {
                // Eski kodu yenisiyle değiştir: "+90 555..." gibi format
                val rest = currentPhone.substringAfter(' ', "")
                if (rest.isNotEmpty()) "$dialCode $rest" else "$dialCode "
            } else {
                // Daha önce kod yoksa başa ekle
                if (currentPhone.isNotEmpty()) {
                    "$dialCode $currentPhone"
                } else {
                    "$dialCode "
                }
            }

            etPhoneNumber.setText(newValue)
            etPhoneNumber.setSelection(newValue.length)
        }
    }

    /** 🔹 Listener'lar */
    private fun setupListeners() = with(binding) {
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }

        btnSave.setOnClickListener {
            saveAddress()
        }
    }

    /** 🔹 Kaydet — TÜM alanlar için boşluk kontrolü, inline error */
    private fun saveAddress() {
        val firstName = binding.etFirstName.text?.toString()?.trim().orEmpty()
        val lastName = binding.etLastName.text?.toString()?.trim().orEmpty()
        val city = binding.etCity.text?.toString()?.trim().orEmpty()
        val address = binding.etAddress.text?.toString()?.trim().orEmpty()
        val postCode = binding.etPostCode.text?.toString()?.trim().orEmpty()
        val phone = binding.etPhoneNumber.text?.toString()?.trim().orEmpty()

        // CountryCodePicker her zaman bir ülke seçili tutar,
        // yine de ISO code'u alıyoruz:
        val countryIso = binding.ccpCountry.selectedCountryNameCode  // ör: "TR"

        // Eski error’ları temizle
        binding.etFirstName.error = null
        binding.etLastName.error = null
        binding.etCity.error = null
        binding.etAddress.error = null
        binding.etPostCode.error = null
        binding.etPhoneNumber.error = null
        // ccpCountry için ayrı bir error UI yok, o yüzden dokunmuyoruz

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

        // Country picker default bir ülke seçiyor, o yüzden ekstra boş kontrol
        // gerekmez; ama istersen ISO boşsa hata verebilirsin.
        // (Genelde burası hiçbir zaman boş olmayacak.)

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

        if (phone.isEmpty()) {
            binding.etPhoneNumber.error =
                getString(R.string.address_error_phone_required)
            hasError = true
        }

        if (hasError) return

        // fullName’i de güncelle
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
                    .setCountry(countryIso)     // ISO: "TR" gibi
                    .setPhoneNumber(phone)      // "+90 5xx ..." gibi
                    .build()
            }

            // ✅ Hata yoksa sessizce geri dön
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}