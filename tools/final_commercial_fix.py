from pathlib import Path


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, path: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, found {count}: {old[:120]!r}")
    return text.replace(old, new, 1)


# Language-neutral numeric values: display always uses '.', input accepts either '.' or ','.
path = "app/src/main/java/com/aqua/aqualight/i18n/LocaleFormatter.kt"
text = read(path)
text = replace_once(text, "import java.text.DecimalFormatSymbols\n", "", path)
text = replace_once(
    text,
    """    internal fun formatInteger(value: Number, locale: Locale): String {
        return NumberFormat.getIntegerInstance(locale).format(value)
    }
""",
    """    /** Product numeric values are language-neutral and never use grouping separators. */
    internal fun formatInteger(value: Number, locale: Locale): String {
        return NumberFormat.getIntegerInstance(Locale.US).apply {
            isGroupingUsed = false
        }.format(value)
    }
""",
    path,
)
text = replace_once(
    text,
    """    internal fun formatDecimal(
        value: Number,
        locale: Locale,
        maximumFractionDigits: Int = 2
    ): String {
        return NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            this.maximumFractionDigits = maximumFractionDigits.coerceAtLeast(0)
        }.format(value)
    }
""",
    """    /** Product numeric values use a stable dot decimal separator in every app language. */
    internal fun formatDecimal(
        value: Number,
        locale: Locale,
        maximumFractionDigits: Int = 2
    ): String {
        return NumberFormat.getNumberInstance(Locale.US).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            this.maximumFractionDigits = maximumFractionDigits.coerceAtLeast(0)
        }.format(value)
    }
""",
    path,
)
text = replace_once(
    text,
    """    internal fun parseDecimal(value: String, locale: Locale): Double? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null

        val localeSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        val alternateSeparator = if (localeSeparator == ',') '.' else ','
        val localeSeparatorCount = trimmed.count { it == localeSeparator }
        val alternateSeparatorCount = trimmed.count { it == alternateSeparator }

        if (localeSeparatorCount > 1 || alternateSeparatorCount > 1) return null
        if (localeSeparatorCount > 0 && alternateSeparatorCount > 0) return null

        val normalized = trimmed
            .replace(localeSeparator, '.')
            .replace(alternateSeparator, '.')
        if (!decimalInputPattern.matches(normalized)) return null

        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
""",
    """    internal fun parseDecimal(value: String, locale: Locale): Double? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.count { it == '.' } > 1 || trimmed.count { it == ',' } > 1) return null
        if ('.' in trimmed && ',' in trimmed) return null

        val normalized = trimmed.replace(',', '.')
        if (!decimalInputPattern.matches(normalized)) return null
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
""",
    path,
)
write(path, text)

# Canonical centimeter rounding prevents 1 cm -> 0.39 in -> rejected round trips.
path = "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/common/AquariumDimensionInputPolicy.kt"
text = read(path)
text = replace_once(
    text,
    """    internal fun parseCentimeters(value: String, unit: String, locale: Locale): Int? {
        val displayedValue = LocaleFormatter.parseDecimal(value, locale) ?: return null
        val centimeters = toCentimeters(displayedValue, unit)
        if (!AquariumMeasurementPolicy.isValidDimensionCm(centimeters)) return null

        return centimeters.roundToInt()
            .takeIf(AquariumMeasurementPolicy::isValidDimensionCm)
    }
""",
    """    internal fun parseCentimeters(value: String, unit: String, locale: Locale): Int? {
        val displayedValue = LocaleFormatter.parseDecimal(value, locale) ?: return null
        return canonicalCentimeters(displayedValue, unit)
    }
""",
    path,
)
text = replace_once(
    text,
    """        val displayedValue = LocaleFormatter.parseDecimal(value, locale) ?: return null
        val centimeters = toCentimeters(displayedValue, fromUnit)
        if (!AquariumMeasurementPolicy.isValidDimensionCm(centimeters)) return null

        val convertedValue = if (isInches(toUnit)) {
            fromCentimeters(centimeters, toUnit)
        } else {
            centimeters.roundToInt().toDouble()
        }
""",
    """        val displayedValue = LocaleFormatter.parseDecimal(value, locale) ?: return null
        val centimeters = canonicalCentimeters(displayedValue, fromUnit) ?: return null
        val convertedValue = fromCentimeters(centimeters.toDouble(), toUnit)
""",
    path,
)
text = replace_once(
    text,
    """    private fun toCentimeters(value: Double, unit: String): Double {
        return if (isInches(unit)) value * CM_PER_INCH else value
    }
""",
    """    private fun canonicalCentimeters(value: Double, unit: String): Int? {
        if (!value.isFinite()) return null
        return toCentimeters(value, unit)
            .roundToInt()
            .takeIf(AquariumMeasurementPolicy::isValidDimensionCm)
    }

    private fun toCentimeters(value: Double, unit: String): Double {
        return if (isInches(unit)) value * CM_PER_INCH else value
    }
""",
    path,
)
write(path, text)

# Stable domain taxonomy, independent from translated labels.
write(
    "app/src/main/java/com/aqua/aqualight/application/aquarium/AquariumTankTaxonomy.kt",
    '''package com.aqua.aqualight.application.aquarium

object AquariumTankTaxonomy {
    const val TYPE_FISH = "Fish"
    const val TYPE_SHRIMP = "Shrimp"
    const val TYPE_PLANTED = "Planted"
    const val TYPE_MARINE = "Marine"
    const val TYPE_SOFTIES = "Softies"
    const val TYPE_MIXED_REEF = "Mixed Reef"
    const val TYPE_SPS = "SPS"
    const val TYPE_CORAL = "Coral"
    const val TYPE_OTHER = "Other"

    val tankTypeCodes: Set<String> = linkedSetOf(
        TYPE_FISH,
        TYPE_SHRIMP,
        TYPE_PLANTED,
        TYPE_MARINE,
        TYPE_SOFTIES,
        TYPE_MIXED_REEF,
        TYPE_SPS,
        TYPE_CORAL,
        TYPE_OTHER
    )

    const val STYLE_NATURE_AQUARIUM = "Nature Aquarium"
    const val STYLE_IWAGUMI = "Iwagumi"
    const val STYLE_DUTCH = "Dutch"
    const val STYLE_JUNGLE = "Jungle"
    const val STYLE_BIOTOPE = "Biotope"
    const val STYLE_BLACKWATER = "Blackwater"
    const val STYLE_FOREST = "Forest"
    const val STYLE_MOUNTAIN = "Mountain"
    const val STYLE_ISLAND = "Island"

    val presetStyleCodes: Set<String> = linkedSetOf(
        STYLE_NATURE_AQUARIUM,
        STYLE_IWAGUMI,
        STYLE_DUTCH,
        STYLE_JUNGLE,
        STYLE_BIOTOPE,
        STYLE_BLACKWATER,
        STYLE_FOREST,
        STYLE_MOUNTAIN,
        STYLE_ISLAND
    )

    fun isSupportedTankType(value: String): Boolean = value in tankTypeCodes
}
'''
)

write(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/common/AquariumTankTaxonomyText.kt",
    '''package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy

object AquariumTankTaxonomyText {
    private data class Choice(val code: String, @StringRes val labelRes: Int)

    private val tankTypes = listOf(
        Choice(AquariumTankTaxonomy.TYPE_FISH, R.string.aquarium_tank_type_fish),
        Choice(AquariumTankTaxonomy.TYPE_SHRIMP, R.string.aquarium_tank_type_shrimp),
        Choice(AquariumTankTaxonomy.TYPE_PLANTED, R.string.aquarium_tank_type_planted),
        Choice(AquariumTankTaxonomy.TYPE_MARINE, R.string.aquarium_tank_type_marine),
        Choice(AquariumTankTaxonomy.TYPE_SOFTIES, R.string.aquarium_tank_type_softies),
        Choice(AquariumTankTaxonomy.TYPE_MIXED_REEF, R.string.aquarium_tank_type_mixed_reef),
        Choice(AquariumTankTaxonomy.TYPE_SPS, R.string.aquarium_tank_type_sps),
        Choice(AquariumTankTaxonomy.TYPE_CORAL, R.string.aquarium_tank_type_coral),
        Choice(AquariumTankTaxonomy.TYPE_OTHER, R.string.aquarium_tank_type_other)
    )

    private val presetStyles = listOf(
        Choice(AquariumTankTaxonomy.STYLE_NATURE_AQUARIUM, R.string.aquarium_text_nature_aquarium),
        Choice(AquariumTankTaxonomy.STYLE_IWAGUMI, R.string.aquarium_style_iwagumi),
        Choice(AquariumTankTaxonomy.STYLE_DUTCH, R.string.aquarium_style_dutch),
        Choice(AquariumTankTaxonomy.STYLE_JUNGLE, R.string.aquarium_style_jungle),
        Choice(AquariumTankTaxonomy.STYLE_BIOTOPE, R.string.aquarium_style_biotope),
        Choice(AquariumTankTaxonomy.STYLE_BLACKWATER, R.string.aquarium_style_blackwater),
        Choice(AquariumTankTaxonomy.STYLE_FOREST, R.string.aquarium_style_forest),
        Choice(AquariumTankTaxonomy.STYLE_MOUNTAIN, R.string.aquarium_style_mountain),
        Choice(AquariumTankTaxonomy.STYLE_ISLAND, R.string.aquarium_style_island)
    )

    fun canonicalTankType(context: Context, value: String): String? =
        canonical(value, tankTypes) { context.getString(it) }

    fun tankTypeLabel(context: Context, value: String): String =
        label(value, tankTypes) { context.getString(it) }

    fun canonicalTankStyle(context: Context, value: String): String {
        val trimmed = value.trim()
        return canonical(trimmed, presetStyles) { context.getString(it) } ?: trimmed
    }

    fun tankStyleLabel(context: Context, value: String): String =
        label(value, presetStyles) { context.getString(it) }

    internal fun canonicalTankType(value: String, labelFor: (Int) -> String): String? =
        canonical(value, tankTypes, labelFor)

    internal fun canonicalTankStyle(value: String, labelFor: (Int) -> String): String {
        val trimmed = value.trim()
        return canonical(trimmed, presetStyles, labelFor) ?: trimmed
    }

    internal fun tankTypeLabel(value: String, labelFor: (Int) -> String): String =
        label(value, tankTypes, labelFor)

    internal fun tankStyleLabel(value: String, labelFor: (Int) -> String): String =
        label(value, presetStyles, labelFor)

    private fun canonical(
        value: String,
        choices: List<Choice>,
        labelFor: (Int) -> String
    ): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return choices.firstOrNull { choice ->
            choice.code.equals(trimmed, ignoreCase = true) ||
                labelFor(choice.labelRes).trim().equals(trimmed, ignoreCase = true)
        }?.code
    }

    private fun label(
        value: String,
        choices: List<Choice>,
        labelFor: (Int) -> String
    ): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        val choice = choices.firstOrNull { choice ->
            choice.code.equals(trimmed, ignoreCase = true) ||
                labelFor(choice.labelRes).trim().equals(trimmed, ignoreCase = true)
        }
        return choice?.let { labelFor(it.labelRes) } ?: trimmed
    }
}
'''
)

# Store validation consumes stable codes, never translated labels.
path = "app/src/main/java/com/aqua/aqualight/data/aquarium/store/TankStoreRules.kt"
text = read(path)
text = replace_once(
    text,
    "import com.aqua.aqualight.data.store.CommercialStoreSchema\n",
    "import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy\nimport com.aqua.aqualight.data.store.CommercialStoreSchema\n",
    path,
)
text = replace_once(
    text,
    '''    private val allowedTankTypes = setOf(
        "Fish",
        "Shrimp",
        "Planted",
        "Marine",
        "Softies",
        "Mixed Reef",
        "SPS",
        "Coral",
        "Other"
    )
''',
    "    private val allowedTankTypes = AquariumTankTaxonomy.tankTypeCodes\n",
    path,
)
write(path, text)

# Creation flow canonicalizes translated labels and displays localized labels from stable codes.
path = "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/create/steps/TankInfoFragment.kt"
text = read(path)
text = replace_once(
    text,
    "import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumMeasurementPolicy\n",
    "import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumMeasurementPolicy\nimport com.aqua.aqualight.ui.tabs.aquarium.common.AquariumTankTaxonomyText\n",
    path,
)
text = replace_once(
    text,
    '''                TankSettingsEditorBottomSheet.Mode.TYPE -> {
                    result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.takeIf(String::isNotBlank)
                        ?.let(viewModel::updateTankType)
                }
''',
    '''                TankSettingsEditorBottomSheet.Mode.TYPE -> {
                    result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.let { AquariumTankTaxonomyText.canonicalTankType(requireContext(), it) }
                        ?.let(viewModel::updateTankType)
                }
''',
    path,
)
text = replace_once(
    text,
    '''                TankSettingsEditorBottomSheet.Mode.STYLE -> {
                    result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.takeIf(String::isNotBlank)
                        ?.let(viewModel::updateTankStyle)
                }
''',
    '''                TankSettingsEditorBottomSheet.Mode.STYLE -> {
                    result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.let { AquariumTankTaxonomyText.canonicalTankStyle(requireContext(), it) }
                        ?.takeIf(String::isNotBlank)
                        ?.let(viewModel::updateTankStyle)
                }
''',
    path,
)
text = replace_once(
    text,
    '''        binding.tvTankTypeValue.text = draft.tankType.ifBlank {
            getString(R.string.aquarium_common_not_selected)
        }
''',
    '''        binding.tvTankTypeValue.text = draft.tankType
            .takeIf(String::isNotBlank)
            ?.let { AquariumTankTaxonomyText.tankTypeLabel(requireContext(), it) }
            ?: getString(R.string.aquarium_common_not_selected)
''',
    path,
)
text = replace_once(
    text,
    "            binding.tvStyleValue.text = draft.tankStyle\n",
    "            binding.tvStyleValue.text = AquariumTankTaxonomyText.tankStyleLabel(\n                requireContext(),\n                draft.tankStyle\n            )\n",
    path,
)
text = replace_once(
    text,
    "            currentText = viewModel.tankDraft.tankType\n",
    "            currentText = AquariumTankTaxonomyText.tankTypeLabel(\n                requireContext(),\n                viewModel.tankDraft.tankType\n            )\n",
    path,
)
text = replace_once(
    text,
    "            currentText = viewModel.tankDraft.tankStyle,\n",
    "            currentText = AquariumTankTaxonomyText.tankStyleLabel(\n                requireContext(),\n                viewModel.tankDraft.tankStyle\n            ),\n",
    path,
)
write(path, text)

# Settings flow uses the same stable code boundary.
path = "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/settings/TankSettingsBasicFragment.kt"
text = read(path)
text = replace_once(
    text,
    "import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDimensionFormatter\n",
    "import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDimensionFormatter\nimport com.aqua.aqualight.ui.tabs.aquarium.common.AquariumTankTaxonomyText\n",
    path,
)
text = replace_once(
    text,
    '''                    val value = result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.takeIf(String::isNotBlank)
                        ?: return@setFragmentResultListener
                    runTankUpdate(getString(R.string.aquarium_error_tank_type_save_failed)) {
                        aquariumTankViewModel.updateTankType(tankId, value)
                    }
''',
    '''                    val value = result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.let { AquariumTankTaxonomyText.canonicalTankType(requireContext(), it) }
                        ?: return@setFragmentResultListener
                    runTankUpdate(getString(R.string.aquarium_error_tank_type_save_failed)) {
                        aquariumTankViewModel.updateTankType(tankId, value)
                    }
''',
    path,
)
text = replace_once(
    text,
    '''                    val value = result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.takeIf(String::isNotBlank)
                        ?: return@setFragmentResultListener
                    runTankUpdate(getString(R.string.aquarium_error_tank_style_save_failed)) {
                        aquariumTankViewModel.updateTankStyle(tankId, value)
                    }
''',
    '''                    val value = result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.let { AquariumTankTaxonomyText.canonicalTankStyle(requireContext(), it) }
                        ?.takeIf(String::isNotBlank)
                        ?: return@setFragmentResultListener
                    runTankUpdate(getString(R.string.aquarium_error_tank_style_save_failed)) {
                        aquariumTankViewModel.updateTankStyle(tankId, value)
                    }
''',
    path,
)
text = replace_once(
    text,
    '''        binding.tvSettingTankType.text = tank.tankType.ifBlank {
            getString(R.string.aquarium_no_value_placeholder)
        }
''',
    '''        binding.tvSettingTankType.text = tank.tankType
            .takeIf(String::isNotBlank)
            ?.let { AquariumTankTaxonomyText.tankTypeLabel(requireContext(), it) }
            ?: getString(R.string.aquarium_no_value_placeholder)
''',
    path,
)
text = replace_once(
    text,
    '''        binding.tvSettingStyle.text = tank.tankStyle.ifBlank {
            getString(R.string.aquarium_no_value_placeholder)
        }
''',
    '''        binding.tvSettingStyle.text = tank.tankStyle
            .takeIf(String::isNotBlank)
            ?.let { AquariumTankTaxonomyText.tankStyleLabel(requireContext(), it) }
            ?: getString(R.string.aquarium_no_value_placeholder)
''',
    path,
)
text = replace_once(
    text,
    "            currentText = tank.tankType\n",
    "            currentText = AquariumTankTaxonomyText.tankTypeLabel(requireContext(), tank.tankType)\n",
    path,
)
text = replace_once(
    text,
    "            currentText = tank.tankStyle,\n",
    "            currentText = AquariumTankTaxonomyText.tankStyleLabel(requireContext(), tank.tankStyle),\n",
    path,
)
write(path, text)

# Detail and PDF surfaces translate stable codes only at render time.
path = "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/TankDetailTankFragment.kt"
text = read(path)
text = replace_once(
    text,
    "import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDimensionFormatter\n",
    "import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDimensionFormatter\nimport com.aqua.aqualight.ui.tabs.aquarium.common.AquariumTankTaxonomyText\n",
    path,
)
text = replace_once(
    text,
    "        binding.tvTankTypeValue.text = tank.tankType.ifBlank { VALUE_EMPTY }\n",
    "        binding.tvTankTypeValue.text = tank.tankType.takeIf(String::isNotBlank)\n            ?.let { AquariumTankTaxonomyText.tankTypeLabel(requireContext(), it) }\n            ?: VALUE_EMPTY\n",
    path,
)
text = replace_once(
    text,
    "        binding.tvTankStyleValue.text = tank.tankStyle.ifBlank { VALUE_EMPTY }\n",
    "        binding.tvTankStyleValue.text = tank.tankStyle.takeIf(String::isNotBlank)\n            ?.let { AquariumTankTaxonomyText.tankStyleLabel(requireContext(), it) }\n            ?: VALUE_EMPTY\n",
    path,
)
write(path, text)

path = "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/export/TankPdfExporter.kt"
text = read(path)
text = replace_once(
    text,
    "import com.aqua.aqualight.i18n.LocaleFormatter\n",
    "import com.aqua.aqualight.i18n.LocaleFormatter\nimport com.aqua.aqualight.ui.tabs.aquarium.common.AquariumTankTaxonomyText\n",
    path,
)
text = replace_once(
    text,
    '''    writer.drawLabelValue(texts.labelTankType, tank.tankType.ifBlank {
      texts.noValue
    })
''',
    '''    writer.drawLabelValue(
      texts.labelTankType,
      tank.tankType.takeIf(String::isNotBlank)
        ?.let { AquariumTankTaxonomyText.tankTypeLabel(context, it) }
        ?: texts.noValue
    )
''',
    path,
)
text = replace_once(
    text,
    '''    writer.drawLabelValue(texts.labelTankStyle, tank.tankStyle.ifBlank {
      texts.noValue
    })
''',
    '''    writer.drawLabelValue(
      texts.labelTankStyle,
      tank.tankStyle.takeIf(String::isNotBlank)
        ?.let { AquariumTankTaxonomyText.tankStyleLabel(context, it) }
        ?: texts.noValue
    )
''',
    path,
)
write(path, text)

# Deterministic overlap coordinate: clearly nearer to the second visual target.
path = "app/src/androidTest/java/com/aqua/aqualight/base/accessibility/MinimumTouchTargetInstrumentedTest.kt"
text = read(path)
text = replace_once(
    text,
    "                    // 45dp is outside both 20dp visual controls but inside both 48dp targets.\n                    x = dp(45, density).toFloat(),\n",
    "                    // 49dp is outside both visuals, inside both expanded targets, and clearly\n                    // nearer to the second visual control.\n                    x = dp(49, density).toFloat(),\n",
    path,
)
text = replace_once(
    text,
    "                    x = dp(45, density).toFloat(),\n",
    "                    x = dp(49, density).toFloat(),\n",
    path,
)
write(path, text)

# Unit contracts.
path = "app/src/test/java/com/aqua/aqualight/i18n/LocaleFormatterTest.kt"
text = read(path)
text = replace_once(
    text,
    '''    fun integersUseLocaleSpecificGrouping() {
        assertEquals(
            "1,234",
            LocaleFormatter.formatInteger(1_234, Locale.US)
        )
        assertEquals(
            "1.234",
            LocaleFormatter.formatInteger(1_234, Locale.GERMANY)
        )
    }
''',
    '''    fun integersAreLanguageNeutralWithoutGrouping() {
        assertEquals("1234", LocaleFormatter.formatInteger(1_234, Locale.US))
        assertEquals("1234", LocaleFormatter.formatInteger(1_234, Locale.GERMANY))
    }
''',
    path,
)
text = replace_once(
    text,
    '''    fun decimalsUseLocaleSpecificSeparators() {
        assertEquals(
            "12.5",
            LocaleFormatter.formatDecimal(12.5, Locale.US)
        )
        assertEquals(
            "12,5",
            LocaleFormatter.formatDecimal(12.5, Locale.GERMANY)
        )
    }
''',
    '''    fun decimalsAreLanguageNeutralAndUseDot() {
        assertEquals("12.5", LocaleFormatter.formatDecimal(12.5, Locale.US))
        assertEquals("12.5", LocaleFormatter.formatDecimal(12.5, Locale.GERMANY))
    }
''',
    path,
)
text = replace_once(
    text,
    "        assertNull(LocaleFormatter.parseDecimal(\"1,234.5\", Locale.US))\n",
    "        assertEquals(1.234, requireNotNull(LocaleFormatter.parseDecimal(\"1,234\", Locale.US)), 0.0)\n        assertEquals(1.234, requireNotNull(LocaleFormatter.parseDecimal(\"1.234\", turkish)), 0.0)\n        assertNull(LocaleFormatter.parseDecimal(\"1,234.5\", Locale.US))\n",
    path,
)
write(path, text)

path = "app/src/test/java/com/aqua/aqualight/ui/tabs/aquarium/common/AquariumDimensionInputPolicyTest.kt"
text = read(path)
text = text.replace('assertEquals("23,62", AquariumDimensionInputPolicy.format(60.0, "in", turkish))', 'assertEquals("23.62", AquariumDimensionInputPolicy.format(60.0, "in", turkish))')
text = text.replace('AquariumDimensionInputPolicy.parseCentimeters("23,62", "in", turkish)', 'AquariumDimensionInputPolicy.parseCentimeters("23,62", "in", turkish)')
text = replace_once(
    text,
    '''    @Test
    fun invalidOrOutOfRangeDimensionsAreRejectedBeforePersistence() {
''',
    '''    @Test
    fun minimumCentimeterRoundTripsThroughDisplayedInches() {
        val turkish = Locale("tr", "TR")
        val inches = AquariumDimensionInputPolicy.format(1.0, "in", turkish)

        assertEquals("0.39", inches)
        assertEquals(1, AquariumDimensionInputPolicy.parseCentimeters(inches, "in", turkish))
    }

    @Test
    fun invalidOrOutOfRangeDimensionsAreRejectedBeforePersistence() {
''',
    path,
)
write(path, text)

path = "app/src/test/java/com/aqua/aqualight/i18n/PopulatedTankLocalizationContractTest.kt"
text = read(path)
text = replace_once(text, "import org.junit.Assert.assertNotEquals\n", "", path)
text = replace_once(text, '            "23,62",\n', '            "23.62",\n', path)
text = replace_once(text, '        assertEquals("25,36", LocaleFormatter.formatDecimal(gallons, turkish))\n', '        assertEquals("25.36", LocaleFormatter.formatDecimal(gallons, turkish))\n', path)
text = replace_once(
    text,
    '''        assertNotEquals(
            LocaleFormatter.formatDecimal(gallons, turkish),
            LocaleFormatter.formatDecimal(gallons, english)
        )
''',
    '''        assertEquals(
            LocaleFormatter.formatDecimal(gallons, turkish),
            LocaleFormatter.formatDecimal(gallons, english)
        )
''',
    path,
)
write(path, text)

write(
    "app/src/test/java/com/aqua/aqualight/ui/tabs/aquarium/common/AquariumTankTaxonomyTextTest.kt",
    '''package com.aqua.aqualight.ui.tabs.aquarium.common

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AquariumTankTaxonomyTextTest {
    private val turkishLabels = mapOf(
        R.string.aquarium_tank_type_fish to "Balık",
        R.string.aquarium_tank_type_shrimp to "Karides",
        R.string.aquarium_tank_type_planted to "Bitkili",
        R.string.aquarium_tank_type_marine to "Deniz",
        R.string.aquarium_tank_type_softies to "Yumuşak Mercan",
        R.string.aquarium_tank_type_mixed_reef to "Karma Resif",
        R.string.aquarium_tank_type_sps to "SPS",
        R.string.aquarium_tank_type_coral to "Mercan",
        R.string.aquarium_tank_type_other to "Diğer",
        R.string.aquarium_text_nature_aquarium to "Doğa Akvaryumu",
        R.string.aquarium_style_iwagumi to "Iwagumi",
        R.string.aquarium_style_dutch to "Hollanda",
        R.string.aquarium_style_jungle to "Jungle",
        R.string.aquarium_style_biotope to "Biyotop",
        R.string.aquarium_style_blackwater to "Blackwater",
        R.string.aquarium_style_forest to "Orman",
        R.string.aquarium_style_mountain to "Dağ",
        R.string.aquarium_style_island to "Ada"
    )

    private fun labelFor(resId: Int): String = requireNotNull(turkishLabels[resId])

    @Test
    fun translatedTankTypeIsConvertedToStableCode() {
        assertEquals(
            AquariumTankTaxonomy.TYPE_SHRIMP,
            AquariumTankTaxonomyText.canonicalTankType("Karides", ::labelFor)
        )
        assertEquals(
            AquariumTankTaxonomy.TYPE_SHRIMP,
            AquariumTankTaxonomyText.canonicalTankType("Shrimp", ::labelFor)
        )
        assertEquals(
            "Karides",
            AquariumTankTaxonomyText.tankTypeLabel("Shrimp", ::labelFor)
        )
        assertNull(AquariumTankTaxonomyText.canonicalTankType("Bilinmeyen", ::labelFor))
    }

    @Test
    fun presetStyleUsesStableCodeWhileCustomStyleRemainsUserText() {
        assertEquals(
            AquariumTankTaxonomy.STYLE_DUTCH,
            AquariumTankTaxonomyText.canonicalTankStyle("Hollanda", ::labelFor)
        )
        assertEquals(
            "Hollanda",
            AquariumTankTaxonomyText.tankStyleLabel("Dutch", ::labelFor)
        )
        assertEquals(
            "Benim Stilim",
            AquariumTankTaxonomyText.canonicalTankStyle(" Benim Stilim ", ::labelFor)
        )
    }
}
'''
)

print("final commercial fix applied")
