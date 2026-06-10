package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankSettingsDetailsBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.data.aquarium.catalog.material.MaterialCategoryCatalog
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class TankSettingsDetailsFragment : Fragment(R.layout.fragment_tank_settings_details) {

    private var _binding: FragmentTankSettingsDetailsBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankSettingsDetailsBinding.bind(view)

        observeTank()
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { savedTank ->
                savedTank.id == tankId
            } ?: return@observe

            renderMaterials(tank)
        }
    }

    private fun renderMaterials(
        tank: SavedAquariumTank
    ) {
        binding.bioMaterialsContainer.removeAllViews()
        binding.hardwareMaterialsContainer.removeAllViews()

        MaterialCategoryCatalog.bioCategories.forEach { category ->
            val selectedMaterials = tank.materials.filter { material ->
                material.categoryKey == category.key
            }

            binding.bioMaterialsContainer.addView(
                createMaterialCard(
                    categoryKey = category.key,
                    title = category.title,
                    materials = selectedMaterials
                )
            )
        }

        MaterialCategoryCatalog.hardwareCategories.forEach { category ->
            val selectedMaterials = tank.materials.filter { material ->
                material.categoryKey == category.key
            }

            binding.hardwareMaterialsContainer.addView(
                createMaterialCard(
                    categoryKey = category.key,
                    title = category.title,
                    materials = selectedMaterials
                )
            )
        }
    }

    private fun createMaterialCard(
        categoryKey: String,
        title: String,
        materials: List<SavedAquariumMaterial>
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = 16.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#10233A"))
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 10.dp()
            layoutParams = params
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                14.dp(),
                12.dp(),
                12.dp(),
                12.dp()
            )
        }

        val iconBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#263B5A"))
            cornerRadius = 12.dp().toFloat()
        }

        val iconBox = TextView(requireContext()).apply {
            text = title.take(2).uppercase(Locale.getDefault())
            gravity = Gravity.CENTER
            textSize = 10f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = iconBackground
            includeFontPadding = false

            layoutParams = LinearLayout.LayoutParams(
                42.dp(),
                42.dp()
            )
        }

        val textBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginStart = 14.dp()
            layoutParams = params
        }

        val titleText = TextView(requireContext()).apply {
            text = title
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val summaryText = TextView(requireContext()).apply {
            text = getMaterialSummary(materials)
            textSize = 12f
            setTextColor(Color.parseColor("#8FA4BE"))

            setLineSpacing(
                2.dp().toFloat(),
                1.0f
            )

            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 6.dp()
            layoutParams = params
        }

        val arrow = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_arrow_right)
            setColorFilter(Color.parseColor("#8FA4BE"))
            scaleType = ImageView.ScaleType.CENTER

            layoutParams = LinearLayout.LayoutParams(
                22.dp(),
                22.dp()
            )
        }

        textBox.addView(titleText)
        textBox.addView(summaryText)

        row.addView(iconBox)
        row.addView(textBox)
        row.addView(arrow)

        card.addView(row)

        card.setOnClickListener {
            (parentFragment as? TankSettingsFragment)?.openMaterialPickerFlow(
                categoryKey = categoryKey,
                categoryTitle = title
            )
        }

        return card
    }

    private fun getMaterialSummary(
        materials: List<SavedAquariumMaterial>
    ): String {
        if (materials.isEmpty()) {
            return "Not selected"
        }

        if (materials.size == 1) {
            return materials.first().name
        }

        return "${materials.first().name} +${materials.size - 1} more"
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"

        fun newInstance(
            tankId: Long
        ): TankSettingsDetailsFragment {
            return TankSettingsDetailsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}