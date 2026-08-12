package com.aqua.aqualight.ui.common.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomsheetCountryPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hbb20.CountryCodePicker
import java.util.Locale

class CountryPickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetCountryPickerBinding? = null
    private val binding get() = _binding!!

    companion object {

        const val TAG = "CountryPickerBottomSheet"

        const val REQUEST_KEY = "country_picker_request"

        const val RESULT_COUNTRY_ISO = "result_country_iso"

        const val RESULT_COUNTRY_NAME = "result_country_name"

        private const val ARG_SELECTED_ISO = "selected_iso"

        fun newInstance(currentIso: String?): CountryPickerBottomSheet {

            return CountryPickerBottomSheet().apply {

                arguments = bundleOf(
                    ARG_SELECTED_ISO to currentIso
                )
            }
        }
    }

    private var selectedIso: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedIso =
            arguments?.getString(ARG_SELECTED_ISO)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding =
            BottomsheetCountryPickerBinding.inflate(
                inflater,
                container,
                false
            )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvBottomTitle.text =
            getString(R.string.address_info_country_label)

        val countries = buildCountryList()

        binding.recyclerCountries.layoutManager =
            LinearLayoutManager(requireContext())

        val adapter = CountryAdapter(
            countries = countries,
            selectedIso = selectedIso,
            isoToFlag = ::isoToFlag
        ) { selected ->

            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_COUNTRY_ISO to selected.iso,
                    RESULT_COUNTRY_NAME to selected.name
                )
            )

            dismiss()
        }

        binding.recyclerCountries.adapter = adapter

        val maxHeight =
            (resources.displayMetrics.heightPixels * 0.5f).toInt()

        binding.recyclerCountries.layoutParams =
            binding.recyclerCountries.layoutParams.apply {
                height = maxHeight
            }

        binding.etSearchCountry.addTextChangedListener { text ->

            adapter.filter(text?.toString().orEmpty())
        }
    }

    private data class Country(
        val iso: String,
        val name: String
    )

    private fun buildCountryList(): List<Country> {

        val tempCcp =
            CountryCodePicker(requireContext())

        val result = mutableListOf<Country>()

        for (iso in Locale.getISOCountries()) {

            val locale = Locale("", iso)

            try {

                tempCcp.setCountryForNameCode(iso)

                val effectiveIso =
                    tempCcp.selectedCountryNameCode

                if (
                    effectiveIso.equals(
                        iso,
                        ignoreCase = true
                    )
                ) {

                    result.add(
                        Country(
                            iso = iso,
                            name = locale.displayCountry
                        )
                    )
                }

            } catch (_: Exception) {
            }
        }

        return result.sortedBy { it.name }
    }

    private fun isoToFlag(
        isoCode: String
    ): String {

        if (isoCode.length != 2) return ""

        val upper =
            isoCode.uppercase(Locale.ROOT)

        val firstLetter =
            Character.codePointAt(upper, 0) -
                    0x41 + 0x1F1E6

        val secondLetter =
            Character.codePointAt(upper, 1) -
                    0x41 + 0x1F1E6

        return String(Character.toChars(firstLetter)) +
                String(Character.toChars(secondLetter))
    }

    private class CountryAdapter(
        private val countries: List<Country>,
        private var selectedIso: String?,
        private val isoToFlag: (String) -> String,
        private val onItemClick: (Country) -> Unit
    ) : RecyclerView.Adapter<CountryAdapter.CountryVH>() {

        private var filtered: List<Country> =
            countries

        inner class CountryVH(view: View) :
            RecyclerView.ViewHolder(view) {

            val tvFlag: TextView =
                view.findViewById(R.id.tvFlag)

            val tvCountryName: TextView =
                view.findViewById(R.id.tvCountryName)

            val tvCountryIso: TextView =
                view.findViewById(R.id.tvCountryIso)

            val radio: RadioButton =
                view.findViewById(R.id.radioCountry)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): CountryVH {

            val view =
                LayoutInflater.from(parent.context)
                    .inflate(
                        R.layout.item_country_radio,
                        parent,
                        false
                    )

            return CountryVH(view)
        }

        override fun onBindViewHolder(
            holder: CountryVH,
            position: Int
        ) {

            val item = filtered[position]

            holder.tvFlag.text =
                isoToFlag(item.iso)

            holder.tvCountryName.text =
                item.name

            holder.tvCountryIso.text =
                item.iso

            val selected =
                item.iso.equals(
                    selectedIso,
                    ignoreCase = true
                )

            holder.itemView.isSelected = selected

            holder.radio.isChecked = selected

            val clickListener =
                View.OnClickListener {

                    selectedIso = item.iso

                    notifyDataSetChanged()

                    onItemClick(item)
                }

            holder.itemView.setOnClickListener(
                clickListener
            )

            holder.radio.setOnClickListener(
                clickListener
            )
        }

        override fun getItemCount(): Int {

            return filtered.size
        }

        fun filter(query: String) {

            filtered =
                if (query.isBlank()) {

                    countries

                } else {

                    countries.filter {

                        it.name.contains(
                            query,
                            ignoreCase = true
                        ) ||

                                it.iso.contains(
                                    query,
                                    ignoreCase = true
                                )
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
