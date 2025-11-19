package com.aqua.aqualight.ui.tabs.settings.userinfo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomsheetCountryPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.hbb20.CountryCodePicker
import java.util.Locale

/**
 * Ülke seçim bottomsheet’i.
 * - Sadece CCP'nin gerçekten desteklediği (telefon kodu olan) ülkeleri listeler.
 * - Arama + radio + emoji bayrak içerir.
 */
object CountryPickerBottomSheet {

    data class Country(val iso: String, val name: String)

    fun show(
        fragment: Fragment,
        currentIso: String?,
        onCountrySelected: (Country) -> Unit
    ) {
        val context = fragment.requireContext()
        val dialog = BottomSheetDialog(context, R.style.AppBottomSheetDialogTheme)

        val sheetBinding = BottomsheetCountryPickerBinding.inflate(fragment.layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Başlık
        sheetBinding.tvBottomTitle.text =
            fragment.getString(R.string.address_info_country_label)

        // CCP'nin desteklediği ülkelere göre listeyi oluştur
        val countries = buildCountryList(context)

        // Recycler
        sheetBinding.recyclerCountries.layoutManager =
            LinearLayoutManager(context)

        val adapter = CountryAdapter(
            countries = countries,
            selectedIso = currentIso,
            isoToFlag = ::isoToFlag
        ) { selected ->
            onCountrySelected(selected)
            dialog.dismiss()
        }

        sheetBinding.recyclerCountries.adapter = adapter

        // Yüksekliği ekranın yarısıyla sınırla
        val maxHeight = (context.resources.displayMetrics.heightPixels * 0.5f).toInt()
        sheetBinding.recyclerCountries.layoutParams =
            sheetBinding.recyclerCountries.layoutParams.apply {
                height = maxHeight
            }

        // Arama filtresi
        sheetBinding.etSearchCountry.addTextChangedListener { text ->
            adapter.filter(text?.toString().orEmpty())
        }

        dialog.show()
    }

    /** 🔹 CCP'nin gerçekten desteklediği ülkelerden liste üret (ISO + display name) */
    private fun buildCountryList(context: Context): List<Country> {
        val tempCcp = CountryCodePicker(context)
        val result = mutableListOf<Country>()

        for (iso in Locale.getISOCountries()) {
            val locale = Locale("", iso)

            try {
                // ISO'yu CCP'ye set etmeyi dene
                tempCcp.setCountryForNameCode(iso)

                // Eğer CCP aynı ISO ile kaldıysa, gerçekten destekliyor demektir
                val effectiveIso = tempCcp.selectedCountryNameCode
                if (effectiveIso.equals(iso, ignoreCase = true)) {
                    result.add(Country(iso = iso, name = locale.displayCountry))
                }
                // UM -> IN gibi map'lenenler otomatik elenmiş oluyor
            } catch (_: Exception) {
                // Desteklemediği ISO varsa sessizce geç
            }
        }

        return result.sortedBy { it.name }
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
    private class CountryAdapter(
        private val countries: List<Country>,
        private var selectedIso: String?,
        private val isoToFlag: (String) -> String,
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
}