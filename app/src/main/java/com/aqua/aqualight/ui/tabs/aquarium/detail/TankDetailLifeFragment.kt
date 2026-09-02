package com.aqua.aqualight.ui.tabs.aquarium.detail

import com.aqua.aqualight.ui.common.text.setTextSizeResource
import androidx.core.content.ContextCompat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailLifeBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.i18n.LocaleFormatter
import com.google.android.material.card.MaterialCardView
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.aqua.aqualight.ui.tabs.aquarium.navigation.TankDetailTabArgs

class TankDetailLifeFragment : Fragment(R.layout.fragment_tank_detail_life) {

    private var _binding: FragmentTankDetailLifeBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var isOpeningLivestockForm: Boolean = false

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

    override fun onResume() {
        super.onResume()
        isOpeningLivestockForm = false
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
        if (isOpeningLivestockForm) {
            return
        }

        val navController = findNavController()

        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.set(
                TankDetailFragment.KEY_SELECTED_TAB,
                TankDetailTabArgs.TANK_LIFE
            )

        val didNavigate = navController.navigateSafelyFrom(
            sourceDestinationId = R.id.tankDetailFragment,
            directions = TankDetailFragmentDirections.actionTankDetailFragmentToTankDetailLivestockFormFragment(
                tankId = tankId,
                livestockId = livestockId
            )
        )

        isOpeningLivestockForm = didNavigate
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
        livestock: List<AquariumLivestock>
    ) {
        binding.tankLifeListContainer.removeAllViews()

        val totalQuantity = livestock.sumOf {
            item ->
            item.quantity.coerceAtLeast(1)
        }

        binding.tvTankLifeSummary.text = if (livestock.isEmpty()) {
            getString(R.string.aquarium_no_livestock_yet)
        } else {
            getString(
                R.string.aquarium_livestock_summary_format,
                livestock.size,
                totalQuantity
            )
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
        livestock: AquariumLivestock
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = resources.getDimensionPixelOffset(R.dimen.aqua_size_18).toFloat()
            strokeWidth = resources.getDimensionPixelOffset(R.dimen.aqua_size_1)
            strokeColor = ContextCompat.getColor(requireContext(), R.color.aqua_tank_detail_life_fragment_outline)
            setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.aqua_surface_deep))
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_12)
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
                resources.getDimensionPixelOffset(R.dimen.aqua_size_14),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_12),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_12),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_12)
            )
        }

        val iconBox = ImageView(requireContext()).apply {
            setImageResource(
                getLivestockCategoryIcon(livestock.category)
            )

            setColorFilter(ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark))

            background = createLifeIconBackground(
                color = getLivestockCategoryColor(livestock.category)
            )

            scaleType = ImageView.ScaleType.CENTER_INSIDE

            contentDescription = livestock.category.ifBlank {
                getString(R.string.aquarium_livestock_default_title)
            }

            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_46),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_46)
            )

            setPadding(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_10),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_10),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_10),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_10)
            )
        }

        val textBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginStart = resources.getDimensionPixelOffset(R.dimen.aqua_size_14)
            params.marginEnd = resources.getDimensionPixelOffset(R.dimen.aqua_size_10)
            layoutParams = params
        }

        val nameText = TextView(requireContext()).apply {
            text = livestock.name.ifBlank {
                getString(R.string.aquarium_unnamed_livestock)
            }

            setTextSizeResource(R.dimen.aqua_text_size_body_plus)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark))
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val metaText = TextView(requireContext()).apply {
            text = getString(
                R.string.aquarium_livestock_meta_format,
                livestock.category.ifBlank { getString(R.string.aquarium_tank_type_other) },
                getLivestockQuantityText(livestock.quantity)
            )
            setTextSizeResource(R.dimen.aqua_text_size_caption_plus)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_content_secondary))
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_7)
            layoutParams = params
        }

        val dateText = TextView(requireContext()).apply {
            text = getLivestockAddedDateText(livestock.addedDateEpochDay)
            setTextSizeResource(R.dimen.aqua_text_size_caption)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_accent_positive))
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_7)
            layoutParams = params
        }

        textBox.addView(nameText)
        textBox.addView(metaText)
        textBox.addView(dateText)

        if (livestock.note.isNotBlank()) {
            val noteText = TextView(requireContext()).apply {
                text = livestock.note
                setTextSizeResource(R.dimen.aqua_text_size_caption)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_content_secondary))
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_7)
                layoutParams = params
            }

            textBox.addView(noteText)
        }

        val arrow = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_arrow_right)
            setColorFilter(ContextCompat.getColor(requireContext(), R.color.aqua_content_secondary))
            scaleType = ImageView.ScaleType.CENTER

            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_22),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_22)
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

        return resources.getQuantityString(
            R.plurals.aquarium_livestock_quantity_piece,
            safeQuantity,
            safeQuantity
        )
    }

    private fun getLivestockAddedDateText(
        addedDateEpochDay: Long?
    ): String {
        if (addedDateEpochDay == null || addedDateEpochDay <= 0L) {
            return getString(R.string.aquarium_livestock_added_date_not_set)
        }

        return getString(
            R.string.aquarium_livestock_added_date_format,
            LocaleFormatter.formatDateEpochDay(requireContext(), addedDateEpochDay)
        )
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
    ): Int {
        return when (category) {
            LivestockCategories.FISH -> R.color.aqua_tank_detail_life_fragment_color
            LivestockCategories.SHRIMP -> R.color.aqua_tank_detail_life_fragment_color_variant_2
            LivestockCategories.SNAIL -> R.color.aqua_tank_detail_life_fragment_color_variant_3
            LivestockCategories.CRAB_CRAYFISH -> R.color.aqua_tank_detail_life_fragment_color_variant_4
            LivestockCategories.CORAL -> R.color.aqua_tank_detail_life_fragment_color_variant_5
            else -> R.color.aqua_tank_detail_life_fragment_color_variant_6
        }
    }

    private fun createLifeIconBackground(
        @ColorRes color: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(ContextCompat.getColor(requireContext(), color))
            cornerRadius = resources.getDimensionPixelOffset(R.dimen.aqua_size_16).toFloat()
        }
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
