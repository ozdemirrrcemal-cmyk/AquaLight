package com.aqua.aqualight.ui.tabs.settings.userinfo

import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentUserAddressBinding
import com.aqua.aqualight.databinding.BottomsheetCountryPickerBinding
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class UserAddressFragment : Fragment(R.layout.fragment_user_address) {

    private var _binding: FragmentUserAddressBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    // Son kullanılan ülke kodu, telefon alanından eski kodu sökebilmek için
    private var lastDialCode: String? = null

    // BottomSheet için basit model
    data class Country(val iso: String, val name: String)

    // Tüm ülkeleri Locale'den çekiyoruz (tek tek yazmak yok)
    private val countries: List<Country> by lazy { buildCountryList() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUserAddressBinding.bind(view)

        loadAddressFromPrefs()
        setupCountryPhoneLink()       // mevcut telefon–ülke senkronu
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

            // Kartın text'ini de güncelle
            val dial = binding.ccpCountry.selectedCountryCodeWithPlus
            val name = binding.ccpCountry.selectedCountryName
            binding.tvCountryValue.text = "$name ($dial)"

            // 📞 Telefon – kayıtlıysa aynen göster, yoksa boş
            if (prefs.phoneNumber.isNotBlank()) {
                binding.etPhoneNumber.setText(prefs.phoneNumber)
                binding.etPhoneNumber.setSelection(prefs.phoneNumber.length)
            } else {
                binding.etPhoneNumber.setText("")
            }
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

            etPhoneNumber.setText(newText)
            etPhoneNumber.setSelection(newText.length)

            // Son kodu güncelle
            lastDialCode = newDialCode

            // Kart text'ini de güncelle
            val name = ccpCountry.selectedCountryName
            tvCountryValue.text = "$name ($newDialCode)"
        }
    }

    /** 🔹 Ülke kartına tıklayınca kendi bottom sheet’imizi aç */
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

    /** 🔹 BottomSheet’i aç — tüm ülkeleri custom listede göster */
private fun showCountryBottomSheet() {
    val dialog = BottomSheetDialog(requireContext(), R.style.AppBottomSheetDialogTheme)

    // Bottom sheet için binding
    val sheetBinding = BottomsheetCountryPickerBinding.inflate(layoutInflater)
    dialog.setContentView(sheetBinding.root)

    val currentIso = binding.ccpCountry.selectedCountryNameCode  // "TR"

    // Başlık zaten XML'de Country, istersen override edebilirsin:
    sheetBinding.tvBottomTitle.text =
        getString(R.string.address_info_country_label)

    sheetBinding.recyclerCountries.layoutManager =
        LinearLayoutManager(requireContext())

    val adapter = CountryAdapter(
        countries = countries,
        selectedIso = currentIso
    ) { selected ->
        // Seçilen ülkeyi CCP'ye set et
        binding.ccpCountry.setCountryForNameCode(selected.iso)

        // Kart text'i güncelle
        val newDial = binding.ccpCountry.selectedCountryCodeWithPlus
        val newName = binding.ccpCountry.selectedCountryName
        binding.tvCountryValue.text = "$newName ($newDial)"

        // OnCountryChangeListener zaten telefon kodunu güncelliyor
        dialog.dismiss()
    }

    sheetBinding.recyclerCountries.adapter = adapter

    // 🔹 RecyclerView yüksekliğini ekranın yarısı ile sınırla
    val maxHeight = (resources.displayMetrics.heightPixels * 0.5f).toInt()
    sheetBinding.recyclerCountries.layoutParams =
        sheetBinding.recyclerCountries.layoutParams.apply {
            height = maxHeight
        }

    // 🔍 Arama filtresi
    sheetBinding.etSearchCountry.addTextChangedListener { text ->
        adapter.filter(text?.toString().orEmpty())
    }

    dialog.show()
}
    /** 🔹 Locale'den ülke listesi üret (ISO + display name) */
    private fun buildCountryList(): List<Country> {
        return Locale.getISOCountries()
            .map { iso ->
                val locale = Locale("", iso)
                Country(iso = iso, name = locale.displayCountry)
            }
            .sortedBy { it.name }
    }

    /** 🔹 ISO -> Emoji bayrak */
    private fun isoToFlag(isoCode: String): String {
        if (isoCode.length != 2) return ""
        val upper = isoCode.uppercase(Locale.ROOT)
        val firstLetter =
            Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
        val secondLetter =
            Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) +
                String(Character.toChars(secondLetter))
    }

    /** 🔹 BottomSheet içindeki ülke listesi adapter’i */
    private inner class CountryAdapter(
    private val countries: List<Country>,
    private var selectedIso: String?,
    private val onItemClick: (Country) -> Unit
) : RecyclerView.Adapter<CountryAdapter.CountryVH>() {

    private var filtered: List<Country> = countries

    inner class CountryVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvFlag: TextView = view.findViewById(R.id.tvFlag)
        val tvCountryName: TextView = view.findViewById(R.id.tvCountryName)
        val tvCountryIso: TextView = view.findViewById(R.id.tvCountryIso)
        val radio: RadioButton = view.findViewById(R.id.radioCountry)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountryVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_country_radio, parent, false)
        return CountryVH(view)
    }

    override fun onBindViewHolder(holder: CountryVH, position: Int) {
        val item = filtered[position]

        holder.tvFlag.text = isoToFlag(item.iso)
        holder.tvCountryName.text = item.name
        holder.tvCountryIso.text = item.iso
        holder.radio.isChecked = item.iso.equals(selectedIso, ignoreCase = true)

        val clickListener = View.OnClickListener {
            selectedIso = item.iso
            notifyDataSetChanged()
            onItemClick(item)
        }

        holder.itemView.setOnClickListener(clickListener)
        holder.radio.setOnClickListener(clickListener)
    }

    override fun getItemCount(): Int = filtered.size

    fun filter(query: String) {
        filtered = if (query.isBlank()) {
            countries
        } else {
            countries.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.iso.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }
}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}