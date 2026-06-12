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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.ui.tabs.aquarium.navigation.AquariumTabArgs
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailTankBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.data.aquarium.catalog.material.MaterialCategoryCatalog
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class TankDetailTankFragment : Fragment(R.layout.fragment_tank_detail_tank) {

    private var _binding: FragmentTankDetailTankBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var currentTank: SavedAquariumTank? = null
    private var isOpeningSettings: Boolean = false

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
        openTankSettings(
            startTab = AquariumTabArgs.BASIC
        )
    }

    private fun openTankSettingsDetails() {
        openTankSettings(
            startTab = AquariumTabArgs.DETAILS
        )
    }

    private fun openTankSettings(
        startTab: String
    ) {
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
        val tank = currentTank ?: return

        val currentUnit = tank.volumeUnit.ifBlank {
            "L"
        }

        val newUnit = if (currentUnit.equals("gal", ignoreCase = true)) {
            "L"
        } else {
            "gal"
        }

        binding.tvTankVolumeValue.text = getTankVolumeText(
            tank = tank,
            volumeUnit = newUnit
        )

        viewLifecycleOwner.lifecycleScope.launch {
            aquariumTankViewModel.updateTankVolumeUnit(
                tankId = tankId,
                volumeUnit = newUnit
            )
        }
    }

    private fun renderTankSection(
        tank: SavedAquariumTank
    ) {
        binding.tvTankDaysValue.text = getTankDaysText(tank.setupDateMillis)

        binding.tvTankVolumeValue.text = getTankVolumeText(
            tank = tank,
            volumeUnit = tank.volumeUnit
        )

        binding.tvTankSizeValue.text = getTankSizeText(tank)

        binding.tvTankTypeValue.text = tank.tankType.ifBlank {
            "-"
        }

        binding.tvTankSetupDateValue.text = getTankSetupDateText(
            tank.setupDateMillis
        )

        binding.tvTankStyleValue.text = tank.tankStyle.ifBlank {
            "-"
        }

        renderTankComponents(tank)
    }

    private fun renderTankComponents(
        tank: SavedAquariumTank
    ) {
        binding.tankBioComponentsContainer.removeAllViews()
        binding.tankHardwareComponentsContainer.removeAllViews()

        MaterialCategoryCatalog.bioCategories.forEach { category ->
            val selectedMaterials = tank.materials.filter { material ->
                material.categoryKey == category.key
            }

            binding.tankBioComponentsContainer.addView(
                createTankComponentCard(
                    shortCode = category.shortCode,
                    title = category.title,
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
                    title = category.title,
                    materials = selectedMaterials
                )
            )
        }
    }

    private fun createTankComponentCard(
        shortCode: String,
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

            setOnClickListener {
                openTankSettingsDetails()
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                14.dp(),
                12.dp(),
                14.dp(),
                12.dp()
            )
        }

        val iconBox = TextView(requireContext()).apply {
            text = shortCode.uppercase(Locale.getDefault())
            gravity = Gravity.CENTER
            textSize = if (shortCode.length > 2) 10f else 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_material_icon_box)
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
            text = getComponentSummary(materials)
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

        textBox.addView(titleText)
        textBox.addView(summaryText)

        row.addView(iconBox)
        row.addView(textBox)

        card.addView(row)

        return card
    }

    private fun getComponentSummary(
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

    private fun getTankDaysText(
        setupDateMillis: Long?
    ): String {
        if (setupDateMillis == null) {
            return "-"
        }

        val day = TimeUnit.MILLISECONDS
            .toDays(System.currentTimeMillis() - setupDateMillis)
            .coerceAtLeast(0)

        return "$day days"
    }

    private fun getTankVolumeText(
        tank: SavedAquariumTank,
        volumeUnit: String
    ): String {
        val liter = (tank.widthCm * tank.lengthCm * tank.heightCm) / 1000.0

        return if (volumeUnit.equals("gal", ignoreCase = true)) {
            val gallon = liter * 0.264172
            "${gallon.roundToInt()} gal"
        } else {
            "${liter.roundToInt()} L"
        }
    }

    private fun getTankSizeText(
        tank: SavedAquariumTank
    ): String {
        return "${tank.widthCm}×${tank.lengthCm}×${tank.heightCm}"
    }

    private fun getTankSetupDateText(
        setupDateMillis: Long?
    ): String {
        if (setupDateMillis == null) {
            return "-"
        }

        val formatter = SimpleDateFormat(
            "dd MMM yy",
            Locale.getDefault()
        )

        return formatter.format(Date(setupDateMillis))
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
        ): TankDetailTankFragment {
            return TankDetailTankFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}