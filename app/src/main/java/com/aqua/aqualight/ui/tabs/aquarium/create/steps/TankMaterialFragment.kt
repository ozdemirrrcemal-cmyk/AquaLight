package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankMaterialBinding
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategory
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryCatalog
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialPickerFragment
import com.google.android.material.card.MaterialCardView

class TankMaterialFragment :
    Fragment(R.layout.fragment_tank_material),
    TankStepFragment {

    private var _binding: FragmentTankMaterialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by viewModels(
        ownerProducer = {
            requireParentFragment()
        }
    )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentTankMaterialBinding.bind(view)

        setupMaterialPickerResultListener()
        renderMaterialCategories()
    }

    private fun setupMaterialPickerResultListener() {
        parentFragmentManager.setFragmentResultListener(
            MaterialPickerFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            renderMaterialCategories()
        }
    }

    private fun renderMaterialCategories() {
        binding.bioContainer.removeAllViews()
        binding.hardwareContainer.removeAllViews()

        MaterialCategoryCatalog.bioCategories.forEach { item ->
            binding.bioContainer.addView(
                createMaterialRow(item)
            )
        }

        MaterialCategoryCatalog.hardwareCategories.forEach { item ->
            binding.hardwareContainer.addView(
                createMaterialRow(item)
            )
        }
    }

    private fun createMaterialRow(
        item: MaterialCategory
    ): View {
        val card =
            MaterialCardView(requireContext()).apply {
                radius =
                    18.dp().toFloat()

                strokeWidth =
                    1.dp()

                strokeColor =
                    Color.parseColor("#223A57")

                setCardBackgroundColor(
                    Color.parseColor("#10233A")
                )

                cardElevation =
                    0f

                useCompatPadding =
                    false

                isClickable =
                    true

                isFocusable =
                    true

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        82.dp()
                    ).apply {
                        bottomMargin =
                            12.dp()
                    }

                setOnClickListener {
                    openMaterialPicker(item)
                }
            }

        val row =
            LinearLayout(requireContext()).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    16.dp(),
                    0,
                    14.dp(),
                    0
                )
            }

        val iconBox =
            TextView(requireContext()).apply {
                text =
                    item.shortCode

                gravity =
                    Gravity.CENTER

                textSize =
                    if (item.shortCode.length > 2) {
                        11f
                    } else {
                        13f
                    }

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setBackgroundResource(
                    R.drawable.bg_material_icon_box
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        50.dp(),
                        50.dp()
                    )
            }

        val textBox =
            LinearLayout(requireContext()).apply {
                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_VERTICAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        marginStart =
                            18.dp()
                    }
            }

        val title =
            TextView(requireContext()).apply {
                text =
                    item.title

                setTextColor(
                    Color.WHITE
                )

                textSize =
                    15f

                includeFontPadding =
                    false
            }

        val selectedText =
            TextView(requireContext()).apply {
                val selectedMaterials =
                    viewModel.getMaterialsByCategory(
                        item.key
                    )

                text =
                    getSelectedMaterialsText(
                        item.key
                    )

                setTextColor(
                    if (selectedMaterials.isEmpty()) {
                        Color.parseColor("#8FA4BE")
                    } else {
                        Color.parseColor("#B8C7D9")
                    }
                )

                textSize =
                    12f

                includeFontPadding =
                    false

                maxLines =
                    1

                ellipsize =
                    TextUtils.TruncateAt.END

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin =
                            6.dp()
                    }
            }

        val arrow =
            TextView(requireContext()).apply {
                text =
                    "›"

                gravity =
                    Gravity.CENTER

                textSize =
                    32f

                includeFontPadding =
                    false

                setTextColor(
                    Color.parseColor("#8FA4BE")
                )

                layoutParams =
                    LinearLayout.LayoutParams(
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

    private fun getSelectedMaterialsText(
        categoryKey: String
    ): String {
        val materials =
            viewModel.getMaterialsByCategory(
                categoryKey
            )

        if (materials.isEmpty()) {
            return "Not selected"
        }

        if (materials.size == 1) {
            return materials.first().name
        }

        return "${materials.first().name} +${materials.size - 1} more"
    }

    private fun openMaterialPicker(
        item: MaterialCategory
    ) {
        (requireParentFragment() as? CreateTankFragment)
            ?.openMaterialPickerFlow(
                categoryKey = item.key,
                categoryTitle = item.title
            )
    }

    override fun validateAndSave(): Boolean {
        return true
    }

    private fun Int.dp(): Int {
        return (
            this * resources.displayMetrics.density
            ).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }
}