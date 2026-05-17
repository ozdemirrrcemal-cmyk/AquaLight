package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankSettingsBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class TankSettingsFragment : Fragment(R.layout.fragment_tank_settings) {

    private var _binding: FragmentTankSettingsBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var selectedTab: SettingsTab = SettingsTab.BASIC
    private var currentTank: SavedAquariumTank? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentTankSettingsBinding.bind(view)

        setupClickListeners()
        observeTank()
        selectTab(SettingsTab.BASIC)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.tabBasic.setOnClickListener {
            selectTab(SettingsTab.BASIC)
        }

        binding.tabDetails.setOnClickListener {
            selectTab(SettingsTab.DETAILS)
        }

        binding.tabOthers.setOnClickListener {
            selectTab(SettingsTab.OTHERS)
        }

        binding.btnChangePhoto.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Photo picker will be connected next.",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.rowTankName.setOnClickListener {
            showComingSoon("Edit tank name")
        }

        binding.rowTankType.setOnClickListener {
            showComingSoon("Edit tank type")
        }

        binding.rowSize.setOnClickListener {
            showComingSoon("Edit size")
        }

        binding.rowVolume.setOnClickListener {
            showComingSoon("Edit volume")
        }

        binding.rowSetupDate.setOnClickListener {
            showComingSoon("Edit setup date")
        }

        binding.rowStyle.setOnClickListener {
            showComingSoon("Edit style")
        }

        binding.rowIdea.setOnClickListener {
            showComingSoon("Edit idea")
        }
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { it.id == tankId }

            if (tank == null) {
                Toast.makeText(
                    requireContext(),
                    "Tank not found.",
                    Toast.LENGTH_SHORT
                ).show()

                findNavController().navigateUp()
                return@observe
            }

            bindTank(tank)
        }
    }

    private fun bindTank(
        tank: SavedAquariumTank
    ) {
        currentTank = tank

        if (!tank.photoUri.isNullOrBlank()) {
            binding.imgTankPhoto.load(Uri.parse(tank.photoUri)) {
                placeholder(R.drawable.nature_aquarium)
                error(R.drawable.nature_aquarium)
                crossfade(true)
            }
        } else {
            binding.imgTankPhoto.setImageResource(R.drawable.nature_aquarium)
        }

        binding.tvSettingTankName.text = tank.name
        binding.tvSettingTankType.text = tank.tankType.ifBlank { "-" }
        binding.tvSettingSize.text = getSizeText(tank)
        binding.tvSettingVolume.text = getVolumeText(tank)
        binding.tvSettingSetupDate.text = getSetupDateText(tank.setupDateMillis)

        binding.tvSettingStyle.text = tank.tankStyle.ifBlank { "-" }
        binding.tvSettingIdea.text = tank.description.ifBlank { "No idea added" }

        renderMaterials(tank)
    }

    private fun selectTab(
        tab: SettingsTab
    ) {
        selectedTab = tab

        resetTabs()

        when (tab) {
            SettingsTab.BASIC -> {
                activateTab(binding.tabBasic)
                moveTabUnderline(binding.tabBasic)
                binding.basicSection.isVisible = true
            }

            SettingsTab.DETAILS -> {
                activateTab(binding.tabDetails)
                moveTabUnderline(binding.tabDetails)
                binding.detailsSection.isVisible = true
            }

            SettingsTab.OTHERS -> {
                activateTab(binding.tabOthers)
                moveTabUnderline(binding.tabOthers)
                binding.othersSection.isVisible = true
            }
        }

        binding.contentScrollView.post {
            binding.contentScrollView.scrollTo(0, 0)
        }
    }

    private fun resetTabs() {
        val inactiveColor = Color.parseColor("#8FA4BE")

        listOf(
            binding.tabBasic,
            binding.tabDetails,
            binding.tabOthers
        ).forEach { tab ->
            tab.setTextColor(inactiveColor)
            tab.setTypeface(null, Typeface.NORMAL)
        }

        binding.basicSection.isVisible = false
        binding.detailsSection.isVisible = false
        binding.othersSection.isVisible = false
    }

    private fun activateTab(
        tabView: TextView
    ) {
        tabView.setTextColor(Color.WHITE)
        tabView.setTypeface(null, Typeface.BOLD)
    }

    private fun moveTabUnderline(
        tabView: TextView
    ) {
        binding.settingsTabsContainer.post {
            val underlineWidth = (tabView.width * 0.70f)
                .toInt()
                .coerceIn(
                    42.dp(),
                    76.dp()
                )

            val params = binding.tabUnderline.layoutParams
            params.width = underlineWidth
            binding.tabUnderline.layoutParams = params

            val targetX = tabView.x + ((tabView.width - underlineWidth) / 2f)

            binding.tabUnderline.animate()
                .translationX(targetX)
                .setDuration(180)
                .start()
        }
    }

    private fun renderMaterials(
        tank: SavedAquariumTank
    ) {
        binding.bioMaterialsContainer.removeAllViews()
        binding.hardwareMaterialsContainer.removeAllViews()

        val bioMaterials = tank.materials.filter { material ->
            material.categoryKey in BIO_CATEGORY_KEYS
        }

        val hardwareMaterials = tank.materials.filter { material ->
            material.categoryKey in HARDWARE_CATEGORY_KEYS
        }

        if (bioMaterials.isEmpty()) {
            binding.bioMaterialsContainer.addView(
                createEmptyText("No bio materials selected.")
            )
        } else {
            bioMaterials
                .groupBy { it.categoryTitle }
                .forEach { entry ->
                    binding.bioMaterialsContainer.addView(
                        createMaterialCard(
                            title = entry.key,
                            materials = entry.value
                        )
                    )
                }
        }

        if (hardwareMaterials.isEmpty()) {
            binding.hardwareMaterialsContainer.addView(
                createEmptyText("No hardware materials selected.")
            )
        } else {
            hardwareMaterials
                .groupBy { it.categoryTitle }
                .forEach { entry ->
                    binding.hardwareMaterialsContainer.addView(
                        createMaterialCard(
                            title = entry.key,
                            materials = entry.value
                        )
                    )
                }
        }
    }

    private fun createMaterialCard(
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
            setLineSpacing(2.dp().toFloat(), 1.0f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 6.dp()
            layoutParams = params
        }

        val arrow = TextView(requireContext()).apply {
            text = "›"
            textSize = 23f
            setTextColor(Color.parseColor("#8FA4BE"))
            gravity = Gravity.CENTER

            layoutParams = LinearLayout.LayoutParams(
                22.dp(),
                34.dp()
            )
        }

        textBox.addView(titleText)
        textBox.addView(summaryText)

        row.addView(iconBox)
        row.addView(textBox)
        row.addView(arrow)

        card.addView(row)

        card.setOnClickListener {
            showComingSoon("Edit $title")
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

    private fun createEmptyText(
        text: String
    ): View {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor("#8FA4BE"))

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 10.dp()
            layoutParams = params
        }
    }

    private fun getSizeText(
        tank: SavedAquariumTank
    ): String {
        return "${tank.widthCm} W x ${tank.lengthCm} L x ${tank.heightCm} H"
    }

    private fun getVolumeText(
        tank: SavedAquariumTank
    ): String {
        val liter = (tank.widthCm * tank.lengthCm * tank.heightCm) / 1000.0

        return if (tank.volumeUnit.equals("gal", ignoreCase = true)) {
            val gallon = liter * 0.264172
            "${gallon.roundToInt()} gal"
        } else {
            "${liter.roundToInt()} L"
        }
    }

    private fun getSetupDateText(
        setupDateMillis: Long?
    ): String {
        if (setupDateMillis == null) {
            return "-"
        }

        val formatter = SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        )

        return formatter.format(Date(setupDateMillis))
    }

    private fun showComingSoon(
        title: String
    ) {
        Toast.makeText(
            requireContext(),
            "$title will be added next.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class SettingsTab {
        BASIC,
        DETAILS,
        OTHERS
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"

        private val BIO_CATEGORY_KEYS = setOf(
            "fertilizer",
            "decoration",
            "gravel",
            "substrate"
        )

        private val HARDWARE_CATEGORY_KEYS = setOf(
            "aquarium",
            "co2",
            "light",
            "filter",
            "heater",
            "cooler",
            "dosing",
            "led_background"
        )
    }
}