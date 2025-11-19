package com.aqua.aqualight.ui.tabs.settings.userinfo

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentUserAddressBinding
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UserAddressFragment : Fragment(R.layout.fragment_user_address) {

    private var _binding: FragmentUserAddressBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    // Son kullanılan ülke kodu, telefon alanından eski kodu sökebilmek için
    private var lastDialCode: String? = null

    // Yazarken recursive TextWatcher tetiklenmesini engellemek için flag
    private var isFormattingPhone = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUserAddressBinding.bind(view)

        loadAddressFromPrefs()
        setupCountryPhoneLink()       // mevcut telefon–ülke senkronu
        setupPhoneFormatting()        // yazarken format
        setupListeners()
        setupCountryPickerClick()     // kart'a tıklayınca bottom sheet aç
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

            // Kartın text'ini de güncelle (SADECE ülke adı)
            val name = binding.ccpCountry.selectedCountryName
            binding.tvCountryValue.text = name

            // 📞 Telefon
            isFormattingPhone = true
            if (prefs.phoneNumber.isNotBlank()) {
                binding.etPhoneNumber.setText(prefs.phoneNumber)
            } else {
                val dial = binding.ccpCountry.selectedCountryCodeWithPlus
                binding.etPhoneNumber.setText("$dial ")
            }
            // Seçimi HER ZAMAN mevcut text uzunluğuna göre yap
            val len = binding.etPhoneNumber.text?.length ?: 0
            binding.etPhoneNumber.setSelection(len)
            isFormattingPhone = false
        }
    }

    /** 🔹 Ülke değişince telefon alanına kodu yaz (CCP'nin change listener'ı) */
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

            isFormattingPhone = true
            etPhoneNumber.setText(newText)
            val len = etPhoneNumber.text?.length ?: newText.length
            etPhoneNumber.setSelection(len)
            isFormattingPhone = false

            // Son kodu güncelle
            lastDialCode = newDialCode

            // Kart text'ini de güncelle (tek ülke adı)
            val name = ccpCountry.selectedCountryName
            tvCountryValue.text = name
        }
    }

    /** 🔹 Telefon alanına yazarken ülkeye göre format + yaklaşık max uzunluk */
    private fun setupPhoneFormatting() {
        val phoneUtil = PhoneNumberUtil.getInstance()

        binding.etPhoneNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormattingPhone) return
                val text = s?.toString() ?: return
                if (text.isBlank()) return

                val countryIso = binding.ccpCountry.selectedCountryNameCode // "TR"
                val dialCode = lastDialCode ?: binding.ccpCountry.selectedCountryCodeWithPlus

                // Text'ten eski dialCode'u sök
                var raw = text
                if (raw.startsWith(dialCode)) {
                    raw = raw.removePrefix(dialCode).trimStart()
                }

                // Sadece rakamları al (kullanıcının yazdığı ulusal numara)
                val digits = raw.filter { it.isDigit() }
                if (digits.isEmpty()) {
                    // Sadece kodu göster
                    val onlyDial = "$dialCode "
                    isFormattingPhone = true
                    binding.etPhoneNumber.setText(onlyDial)
                    val len = binding.etPhoneNumber.text?.length ?: onlyDial.length
                    binding.etPhoneNumber.setSelection(len)
                    isFormattingPhone = false
                    return
                }

                // ✅ Public API: örnek numaradan yaklaşık uzunluk çıkar
                val example = try {
                    phoneUtil.getExampleNumber(countryIso)
                } catch (e: Exception) {
                    null
                }
                val exampleLen = example?.nationalNumber?.toString()?.length ?: 15
                val limitedDigits = digits.take(exampleLen)

                // AsYouTypeFormatter ile ulusal kısmı formatla
                val formatter = phoneUtil.getAsYouTypeFormatter(countryIso)
                var nationalFormatted = ""
                for (ch in limitedDigits) {
                    nationalFormatted = formatter.inputDigit(ch)
                }

                val finalText = if (nationalFormatted.isBlank()) {
                    "$dialCode "
                } else {
                    "$dialCode $nationalFormatted"
                }

                isFormattingPhone = true
                binding.etPhoneNumber.setText(finalText)
                val len = binding.etPhoneNumber.text?.length ?: finalText.length
                binding.etPhoneNumber.setSelection(len)
                isFormattingPhone = false
            }
        })
    }

    /** 🔹 Ülke kartına tıklayınca country bottomsheet’i aç */
    private fun setupCountryPickerClick() = with(binding) {
        cardCountry.setOnClickListener {
            showCountryBottomSheet()
        }
    }

    /** 🔹 Listener'lar */
    private fun setupListeners() = with(binding) {
        btnBack.setOnClickListener { findNavController().popBackStack() }
        btnCancel.setOnClickListener { findNavController().popBackStack() }
        btnSave.setOnClickListener { saveAddress() }
    }

    /** 🔹 Kaydet — TÜM alanlar için boşluk kontrolü + telefon validasyonu */
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

        // ✅ libphonenumber ile telefon validasyonu + format
        val phoneUtil = PhoneNumberUtil.getInstance()
        val numberProto = try {
            phoneUtil.parse(phoneRaw, countryIso)   // "+90 5xx ..." + "TR"
        } catch (e: NumberParseException) {
            binding.etPhoneNumber.error =
                getString(R.string.address_error_phone_invalid)
            return
        }

        if (!phoneUtil.isValidNumberForRegion(numberProto, countryIso)) {
            binding.etPhoneNumber.error =
                getString(R.string.address_error_phone_invalid)
            return
        }

        // International format: +90 5xx xxx xx xx
        val formattedPhone = phoneUtil.format(
            numberProto,
            PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
        )

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

    /** 🔹 Country bottomsheet’i göster */
    private fun showCountryBottomSheet() {
        val currentIso = binding.ccpCountry.selectedCountryNameCode // "TR" vs.

        CountryPickerBottomSheet.show(
            fragment = this,
            currentIso = currentIso
        ) { selected ->
            // Seçilen ülkeyi CCP'ye set et
            binding.ccpCountry.setCountryForNameCode(selected.iso)

            // Kart text'i güncelle (sadece ülke adı)
            val newName = binding.ccpCountry.selectedCountryName
            binding.tvCountryValue.text = newName
            // OnCountryChangeListener zaten telefon kodunu güncelliyor
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}