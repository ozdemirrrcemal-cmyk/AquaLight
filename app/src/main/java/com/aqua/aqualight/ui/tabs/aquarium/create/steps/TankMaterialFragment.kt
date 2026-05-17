package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankMaterialBinding
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.google.android.material.card.MaterialCardView

class TankMaterialFragment : Fragment(R.layout.fragment_tank_material), TankStepFragment {

    private var _binding: FragmentTankMaterialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private val bioItems = listOf(
        MaterialCategoryUi(
            title = "Fertilizer",
            key = "fertilizer",
            shortCode = "Fe"
        ),
        MaterialCategoryUi(
            title = "Decoration",
            key = "decoration",
            shortCode = "De"
        ),
        MaterialCategoryUi(
            title = "Gravel",
            key = "gravel",
            shortCode = "Gr"
        ),
        MaterialCategoryUi(
            title = "Substrate",
            key = "substrate",
            shortCode = "Su"
        )
    )

    private val hardwareItems = listOf(
        MaterialCategoryUi(
            title = "Aquarium",
            key = "aquarium",
            shortCode = "Aq"
        ),
        MaterialCategoryUi(
            title = "CO2",
            key = "co2",
            shortCode = "C"
        ),
        MaterialCategoryUi(
            title = "Light",
            key = "light",
            shortCode = "Li"
        ),
        MaterialCategoryUi(
            title = "Filter",
            key = "filter",
            shortCode = "Fi"
        ),
        MaterialCategoryUi(
            title = "Heater",
            key = "heater",
            shortCode = "He"
        ),
        MaterialCategoryUi(
            title = "Cooler",
            key = "cooler",
            shortCode = "Co"
        ),
        MaterialCategoryUi(
            title = "Dosing",
            key = "dosing",
            shortCode = "Do"
        ),
        MaterialCategoryUi(
            title = "LED Background",
            key = "led_background",
            shortCode = "LED"
        )
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentTankMaterialBinding.bind(view)

        renderMaterialCategories()
    }

    private fun renderMaterialCategories() {
        binding.bioContainer.removeAllViews()
        binding.hardwareContainer.removeAllViews()

        bioItems.forEach { item ->
            binding.bioContainer.addView(
                createMaterialRow(item)
            )
        }

        hardwareItems.forEach { item ->
            binding.hardwareContainer.addView(
                createMaterialRow(item)
            )
        }
    }

    private fun createMaterialRow(
        item: MaterialCategoryUi
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = 18.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#10233A"))
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true
            foreground = requireContext().obtainStyledAttributes(
                intArrayOf(android.R.attr.selectableItemBackground)
            ).let {
                val drawable = it.getDrawable(0)
                it.recycle()
                drawable
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                82.dp()
            )
            params.bottomMargin = 12.dp()
            layoutParams = params

            setOnClickListener {
                openMaterialPicker(item)
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                16.dp(),
                0,
                14.dp(),
                0
            )
        }

        val iconBox = TextView(requireContext()).apply {
            text = item.shortCode
            gravity = Gravity.CENTER
            textSize = if (item.shortCode.length > 2) 11f else 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_material_icon_box)

            layoutParams = LinearLayout.LayoutParams(
                50.dp(),
                50.dp()
            )
        }

        val textBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginStart = 18.dp()
            layoutParams = params
        }

        val title = TextView(requireContext()).apply {
            text = item.title
            setTextColor(Color.WHITE)
            textSize = 15f
            includeFontPadding = false
        }

        val selectedText = TextView(requireContext()).apply {
            text = getSelectedCountText(item.key)
            setTextColor(Color.parseColor("#8FA4BE"))
            textSize = 12f
            includeFontPadding = false

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 6.dp()
            layoutParams = params
        }

        val arrow = TextView(requireContext()).apply {
            text = "›"
            gravity = Gravity.CENTER
            textSize = 32f
            includeFontPadding = false
            setTextColor(Color.parseColor("#8FA4BE"))

            layoutParams = LinearLayout.LayoutParams(
                28.dp(),
                40.dp()
            )
        }

        textBox.addView(title)
        textBox.addView(selectedText)

        row.addView(iconBox)
        row.addView(textBox)
        row.addView(arrow)

        card.addView(row)

        return card
    }

    private fun getSelectedCountText(
    categoryKey: String
    ): String {
    val count = viewModel.getMaterialsByCategory(categoryKey).size

    return if (count == 0) {
        "Not selected"
    } else {
        "$count selected"
       }
    }

    private fun openMaterialPicker(
        item: MaterialCategoryUi
    ) {
        /*
          Bir sonraki adımda burası MaterialPickerFragment açacak.

          Mantık:
          MaterialPickerFragment.newInstance(
              categoryKey = item.key,
              title = item.title
          )

          Fertilizer tıklanınca fertilizer listesi,
          Aquarium tıklanınca aquarium listesi açılacak.
        */
    }

    override fun validateAndSave(): Boolean {
        /*
          Step 4 zorunlu olmayacaksa true kalabilir.
          Kullanıcı hiçbir malzeme seçmeden de Next yapabilir.
        */
        return true
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class MaterialCategoryUi(
        val title: String,
        val key: String,
        val shortCode: String
    )
}