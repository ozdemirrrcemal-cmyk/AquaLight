package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.aquarium.catalog.material.MaterialCategoryCatalog
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.databinding.FragmentTankDetailTankBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDatePolicy
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDimensionFormatter
import com.aqua.aqualight.ui.tabs.aquarium.materials.MaterialSummaryFormatter
import com.aqua.aqualight.ui.tabs.aquarium.navigation.AquariumTabArgs
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

class TankDetailTankFragment : Fragment(R.layout.fragment_tank_detail_tank) {

    private var _binding: FragmentTankDetailTankBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var currentTank: AquariumTankSnapshot? = null
    private var isOpeningSettings: Boolean = false
    private var isUpdatingVolumeUnit: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankDetailTankBinding.bind(view)

        setupClickListeners()
        observeTank()
    }

    override fun onResume() {
        super.onResume()
        isOpeningSettings = false
    }

    private fun setupClickListeners() {
        binding.cardTankValue.setOnClickListener {
            toggleTankVolumeUnit()
        }

        binding.cardTankDays.setOnClickListener {
            openTankSettings()
        }

        binding.cardTankSize.setOnClickListener {
            openTankSettings()
        }

        binding.cardTankType.setOnClickListener {
            openTankSettings()
        }

        binding.cardTankSetup.setOnClickListener {
            openTankSettings()
        }

        binding.cardTankStyle.setOnClickListener {
            openTankSettings()
        }
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { tank ->
                tank.id == tankId
            } ?: return@observe

            currentTank = tank
            renderTankSection(tank)
        }
    }

    private fun openTankSettings() {
        openTankSettings(startTab = AquariumTabArgs.BASIC)
    }

    private fun openTankSettingsDetails() {
        openTankSettings(startTab = AquariumTabArgs.DETAILS)
    }

    private fun openTankSettings(startTab: String) {
        if (isOpeningSettings) {
            return
        }

        val didNavigate = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.tankDetailFragment,
            directions = TankDetailFragmentDirections.actionTankDetailFragmentToTankSettingsFragment(
                tankId = tankId,
                startTab = startTab
            )
        )

        isOpeningSettings = didNavigate
    }

    private fun toggleTankVolumeUnit() {
        if (isUpdatingVolumeUnit) {
            return
        }

        val tank = currentTank ?: return
        val currentUnit = tank.volumeUnit.ifBlank { DEFAULT_VOLUME_UNIT }
        val newUnit = if (currentUnit.equals(VOLUME_UNIT_GALLON, ignoreCase = true)) {
            DEFAULT_VOLUME_UNIT
        } else {
            VOLUME_UNIT_GALLON
        }

        isUpdatingVolumeUnit = true
        binding.tvTankVolumeValue.text = getTankVolumeText(tank, newUnit)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.updateTankVolumeUnit(
                    tankId = tankId,
                    volumeUnit = newUnit
                )
            } catch (exception: Exception) {
                exception.printStackTrace()
                binding.tvTankVolumeValue.text = getTankVolumeText(tank, currentUnit)
                showSnackBar(
                    message = getString(R.string.aquarium_error_volume_unit_save_failed),
                    type = BaseActivity.SnackType.ERROR
                )
            } finally {
                isUpdatingVolumeUnit = false
            }
        }
    }

    private fun renderTankSection(tank: AquariumTankSnapshot) {
        binding.tvTankDaysValue.text = getTankDaysText(tank.setupDateMillis)
        binding.tvTankVolumeValue.text = getTankVolumeText(tank, tank.volumeUnit)
        binding.tvTankSizeValue.text = getTankSizeText(tank)
        binding.tvTankTypeValue.text = tank.tankType.ifBlank { VALUE_EMPTY }
        binding.tvTankSetupDateValue.text = getTankSetupDateText(tank.setupDateMillis)
        binding.tvTankStyleValue.text = tank.tankStyle.ifBlank { VALUE_EMPTY }

        renderTankComponents(tank)
    }

    private fun renderTankComponents(tank: AquariumTankSnapshot) {
        binding.tankBioComponentsContainer.removeAllViews()
        binding.tankHardwareComponentsContainer.removeAllViews()

        MaterialCategoryCatalog.bioCategories.forEach { category ->
            val selectedMaterials = tank.materials.filter { material ->
                material.categoryKey == category.key
            }

            binding.tankBioComponentsContainer.addView(
                createTankComponentCard(
                    shortCode = category.shortCode,
                    title = getString(category.titleRes),
                    materials = selectedMaterials
                )
            )
        }

        MaterialCategoryCatalog.hardwareCategories.forEach { category ->
            val selectedMaterials = tank.materials.filter { material ->
                material.categoryKey == category.key
            }

            binding.tankHardwareComponentsContainer.addView(
                createTankComponentCard(
                    shortCode = category.shortCode,
                    title = getString(category.titleRes),
                    materials = selectedMaterials
                )
            )
        }
    }

    private fun createTankComponentCard(
        shortCode: String,
        title: String,
        materials: List<AquariumMaterialSelection>
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = 16.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = ContextCompat.getColor(
                requireContext(),
                R.color.aqua_card_outline
            )
            setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_card_surface
                )
            )
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dp()
            }
            setOnClickListener {
                openTankSettingsDetails()
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        }

        val iconBox = TextView(requireContext()).apply {
            text = shortCode.uppercase(Locale.getDefault())
            gravity = Gravity.CENTER
            textSize = if (shortCode.length > 2) 10f else 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_material_icon_box)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(42.dp(), 42.dp())
        }

        val textBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 14.dp()
            }
        }

        val titleText = TextView(requireContext()).apply {
            text = title
            textSize = 14f
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_card_text_primary
                )
            )
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val summaryText = TextView(requireContext()).apply {
            text = getComponentSummary(materials)
            textSize = 12f
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_card_text_secondary
                )
            )
            setLineSpacing(2.dp().toFloat(), 1.0f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6.dp()
            }
        }

        textBox.addView(titleText)
        textBox.addView(summaryText)
        row.addView(iconBox)
        row.addView(textBox)
        card.addView(row)

        return card
    }

    private fun getComponentSummary(materials: List<AquariumMaterialSelection>): String {
        return MaterialSummaryFormatter.summaryForSavedMaterials(
            context = requireContext(),
            materials = materials
        )
    }

    private fun getTankDaysText(setupDateMillis: Long?): String {
        if (setupDateMillis == null) {
            return VALUE_EMPTY
        }

        val day = TimeUnit.MILLISECONDS
            .toDays(System.currentTimeMillis() - setupDateMillis)
            .coerceAtLeast(0)

        return resources.getQuantityString(
            R.plurals.aquarium_tank_age_days,
            day.toInt(),
            day
        )
    }

    private fun getTankVolumeText(
        tank: AquariumTankSnapshot,
        volumeUnit: String
    ): String {
        return AquariumDimensionFormatter.volumeText(
            widthCm = tank.widthCm,
            lengthCm = tank.lengthCm,
            heightCm = tank.heightCm,
            volumeUnit = volumeUnit,
            rounded = true
        )
    }

    private fun getTankSizeText(tank: AquariumTankSnapshot): String {
        return AquariumDimensionFormatter.sizeText(
            widthCm = tank.widthCm,
            lengthCm = tank.lengthCm,
            heightCm = tank.heightCm,
            sizeUnit = tank.sizeUnit,
            separator = "×"
        )
    }

    private fun getTankSetupDateText(setupDateMillis: Long?): String {
        return AquariumDatePolicy.formatSetupDate(
            millis = setupDateMillis,
            emptyText = VALUE_EMPTY
        )
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType = BaseActivity.SnackType.NORMAL
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = type
        )
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
        private const val DEFAULT_VOLUME_UNIT = "L"
        private const val VOLUME_UNIT_GALLON = "gal"
        private const val VALUE_EMPTY = "-"

        fun newInstance(tankId: Long): TankDetailTankFragment {
            return TankDetailTankFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}
