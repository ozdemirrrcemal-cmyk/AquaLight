package com.aqua.aqualight.ui.tabs.aquarium.detail

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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailLifeBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.data.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController

class TankDetailLifeFragment : Fragment(R.layout.fragment_tank_detail_life) {

    private var _binding: FragmentTankDetailLifeBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankDetailLifeBinding.bind(view)

        setupClickListeners()
        observeTank()
    }

    private fun setupClickListeners() {
        binding.btnAddLife.setOnClickListener {
            openLivestockForm()
        }

        binding.btnEmptyAddLife.setOnClickListener {
            openLivestockForm()
        }
    }

    private fun openLivestockForm(
        livestockId: Long = 0L
    ) {
        findNavController().navigate(
            R.id.action_tankDetailFragment_to_tankDetailLivestockFormFragment,
            bundleOf(
                "tankId" to tankId,
                "livestockId" to livestockId
            )
        )
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) {
            tanks ->
            val tank = tanks.firstOrNull {
                tank ->
                tank.id == tankId
            } ?: return@observe

            renderLivestock(
                livestock = tank.livestock
            )
        }
    }

    private fun renderLivestock(
        livestock: List<SavedAquariumLivestock>
    ) {
        binding.tankLifeListContainer.removeAllViews()

        val totalQuantity = livestock.sumOf {
            item ->
            item.quantity.coerceAtLeast(1)
        }

        binding.tvTankLifeSummary.text = if (livestock.isEmpty()) {
            "No livestock yet"
        } else {
            "${livestock.size} species • $totalQuantity total livestock"
        }

        binding.cardTankLifeEmpty.isVisible = livestock.isEmpty()
        binding.tankLifeListContainer.isVisible = livestock.isNotEmpty()

        livestock.forEach {
            item ->
            binding.tankLifeListContainer.addView(
                createLivestockCard(item)
            )
        }
    }

    private fun createLivestockCard(
        livestock: SavedAquariumLivestock
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

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 12.dp()
            layoutParams = params

            setOnClickListener {
                openLivestockForm(
                    livestockId = livestock.id
                )
            }
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

        val iconBox = ImageView(requireContext()).apply {
            setImageResource(
                getLivestockCategoryIcon(livestock.category)
            )

            setColorFilter(Color.WHITE)

            background = createLifeIconBackground(
                color = getLivestockCategoryColor(livestock.category)
            )

            scaleType = ImageView.ScaleType.CENTER_INSIDE

            contentDescription = livestock.category.ifBlank {
                "Livestock"
            }

            layoutParams = LinearLayout.LayoutParams(
                46.dp(),
                46.dp()
            )

            setPadding(
                10.dp(),
                10.dp(),
                10.dp(),
                10.dp()
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
            params.marginEnd = 10.dp()
            layoutParams = params
        }

        val nameText = TextView(requireContext()).apply {
            text = livestock.name.ifBlank {
                "Unnamed livestock"
            }

            textSize = 14.5f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val metaText = TextView(requireContext()).apply {
            text = "${livestock.category.ifBlank { "Other" }} • ${getLivestockQuantityText(livestock.quantity)}"
            textSize = 12.5f
            setTextColor(Color.parseColor("#8FA4BE"))
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 7.dp()
            layoutParams = params
        }

        val dateText = TextView(requireContext()).apply {
            text = getLivestockAddedDateText(livestock.addedDateMillis)
            textSize = 12f
            setTextColor(Color.parseColor("#5FD6B4"))
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 7.dp()
            layoutParams = params
        }

        textBox.addView(nameText)
        textBox.addView(metaText)
        textBox.addView(dateText)

        if (livestock.note.isNotBlank()) {
            val noteText = TextView(requireContext()).apply {
                text = livestock.note
                textSize = 12f
                setTextColor(Color.parseColor("#8FA4BE"))
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = 7.dp()
                layoutParams = params
            }

            textBox.addView(noteText)
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

        row.addView(iconBox)
        row.addView(textBox)
        row.addView(arrow)

        card.addView(row)

        return card
    }

    private fun getLivestockQuantityText(
        quantity: Int
    ): String {
        val safeQuantity = quantity.coerceAtLeast(1)

        return if (safeQuantity == 1) {
            "1 pc"
        } else {
            "$safeQuantity pcs"
        }
    }

    private fun getLivestockAddedDateText(
        addedDateMillis: Long?
    ): String {
        if (addedDateMillis == null || addedDateMillis <= 0L) {
            return "Added date not set"
        }

        val formatter = SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        )

        return "Added ${formatter.format(Date(addedDateMillis))}"
    }

    private fun getLivestockCategoryIcon(
        category: String
    ): Int {
        return when (category) {
            LivestockCategories.FISH -> R.drawable.ic_life_fish_24
            LivestockCategories.SHRIMP -> R.drawable.ic_life_shrimp_24
            LivestockCategories.SNAIL -> R.drawable.ic_life_snail_24
            LivestockCategories.CRAB_CRAYFISH -> R.drawable.ic_life_crab_24
            LivestockCategories.CORAL -> R.drawable.ic_life_coral_24
            else -> R.drawable.ic_life_other_24
        }
    }

    private fun getLivestockCategoryColor(
        category: String
    ): String {
        return when (category) {
            LivestockCategories.FISH -> "#1C5D8F"
            LivestockCategories.SHRIMP -> "#8F4A3A"
            LivestockCategories.SNAIL -> "#3E6B4A"
            LivestockCategories.CRAB_CRAYFISH -> "#7A4D2D"
            LivestockCategories.CORAL -> "#7A4E8F"
            else -> "#3E536B"
        }
    }

    private fun createLifeIconBackground(
        color: String
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(color))
            cornerRadius = 16.dp().toFloat()
        }
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
        ): TankDetailLifeFragment {
            return TankDetailLifeFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}