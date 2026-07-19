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
        raise SystemExit(
            f"{path}: expected one occurrence, found {count}: {old[:140]!r}"
        )
    return text.replace(old, new, 1)


def replace_between(
    text: str,
    start_marker: str,
    end_marker: str,
    replacement: str,
    path: str,
) -> str:
    if text.count(start_marker) != 1 or text.count(end_marker) != 1:
        raise SystemExit(
            f"{path}: non-unique block markers: {start_marker!r}, {end_marker!r}"
        )
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    return text[:start] + replacement + text[end:]


locale_path = "app/src/main/java/com/aqua/aqualight/i18n/LocaleFormatter.kt"
text = read(locale_path)
text = replace_once(
    text,
    "import java.text.DateFormat as JavaDateFormat\nimport java.text.NumberFormat",
    "import java.text.DateFormat as JavaDateFormat\n"
    "import java.text.DecimalFormatSymbols\n"
    "import java.text.NumberFormat",
    locale_path,
)
text = replace_once(
    text,
    "object LocaleFormatter {\n",
    "object LocaleFormatter {\n\n"
    '    private val decimalInputPattern = Regex('
    '"^[+-]?(?:\\\\d+(?:\\\\.\\\\d+)?|\\\\.\\\\d+)$"'
    ")\n",
    locale_path,
)
public_decimal_marker = """    fun formatDecimal(
        context: Context,
        value: Number,
        maximumFractionDigits: Int = 2
    ): String {
        return formatDecimal(value, appLocale(context), maximumFractionDigits)
    }
"""
text = replace_once(
    text,
    public_decimal_marker,
    public_decimal_marker
    + """
    /**
     * Parses a decimal input using the active app locale. The alternate dot/comma separator is
     * accepted for IME compatibility, but grouping, mixed separators and partial values are not.
     */
    fun parseDecimal(context: Context, value: CharSequence): Double? {
        return parseDecimal(value.toString(), appLocale(context))
    }
""",
    locale_path,
)
percent_marker = "    internal fun formatPercent(\n"
text = replace_once(
    text,
    percent_marker,
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

    internal fun formatPercent(
""",
    locale_path,
)
write(locale_path, text)

measurement_path = (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/common/"
    "AquariumMeasurementPolicy.kt"
)
write(
    measurement_path,
    """package com.aqua.aqualight.ui.tabs.aquarium.common

object AquariumMeasurementPolicy {
    const val MIN_DIMENSION_CM = 1
    const val MAX_DIMENSION_CM = 5000

    fun isValidDimensionCm(value: Int): Boolean {
        return value in MIN_DIMENSION_CM..MAX_DIMENSION_CM
    }

    fun isValidDimensionCm(value: Double): Boolean {
        return value.isFinite() &&
            value >= MIN_DIMENSION_CM.toDouble() &&
            value <= MAX_DIMENSION_CM.toDouble()
    }

    fun areValidDimensions(
        widthCm: Int,
        lengthCm: Int,
        heightCm: Int
    ): Boolean {
        return isValidDimensionCm(widthCm) &&
            isValidDimensionCm(lengthCm) &&
            isValidDimensionCm(heightCm)
    }
}
""",
)

input_policy_path = (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/common/"
    "AquariumDimensionInputPolicy.kt"
)
write(
    input_policy_path,
    """package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import com.aqua.aqualight.i18n.LocaleFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Keeps displayed tank dimensions, locale parsing and canonical centimeter storage consistent. */
object AquariumDimensionInputPolicy {
    private const val CM_PER_INCH = 2.54
    private const val UNIT_IN = "in"

    fun format(context: Context, centimeters: Int, unit: String): String {
        return format(centimeters.toDouble(), unit, LocaleFormatter.appLocale(context))
    }

    fun parseCentimeters(context: Context, value: CharSequence, unit: String): Int? {
        return parseCentimeters(value.toString(), unit, LocaleFormatter.appLocale(context))
    }

    fun convert(
        context: Context,
        value: CharSequence,
        fromUnit: String,
        toUnit: String
    ): String? {
        return convert(
            value = value.toString(),
            fromUnit = fromUnit,
            toUnit = toUnit,
            locale = LocaleFormatter.appLocale(context)
        )
    }

    internal fun format(centimeters: Double, unit: String, locale: Locale): String {
        return LocaleFormatter.formatDecimal(
            value = fromCentimeters(centimeters, unit),
            locale = locale
        )
    }

    internal fun parseCentimeters(value: String, unit: String, locale: Locale): Int? {
        val displayedValue = LocaleFormatter.parseDecimal(value, locale) ?: return null
        val centimeters = toCentimeters(displayedValue, unit)
        if (!AquariumMeasurementPolicy.isValidDimensionCm(centimeters)) return null

        return centimeters.roundToInt()
            .takeIf(AquariumMeasurementPolicy::isValidDimensionCm)
    }

    internal fun convert(
        value: String,
        fromUnit: String,
        toUnit: String,
        locale: Locale
    ): String? {
        val displayedValue = LocaleFormatter.parseDecimal(value, locale) ?: return null
        val centimeters = toCentimeters(displayedValue, fromUnit)
        if (!AquariumMeasurementPolicy.isValidDimensionCm(centimeters)) return null

        return LocaleFormatter.formatDecimal(
            value = fromCentimeters(centimeters, toUnit),
            locale = locale
        )
    }

    private fun toCentimeters(value: Double, unit: String): Double {
        return if (isInches(unit)) value * CM_PER_INCH else value
    }

    private fun fromCentimeters(value: Double, unit: String): Double {
        return if (isInches(unit)) value / CM_PER_INCH else value
    }

    private fun isInches(unit: String): Boolean {
        return unit.equals(UNIT_IN, ignoreCase = true)
    }
}
""",
)

bottom_sheet_path = (
    "app/src/main/java/com/aqua/aqualight/ui/common/bottomsheet/"
    "TankSettingsEditorBottomSheet.kt"
)
text = read(bottom_sheet_path)
text = replace_once(
    text,
    "import com.aqua.aqualight.databinding.DialogSettingsBottomSheetBinding\n"
    "import com.google.android.material.bottomsheet.BottomSheetDialogFragment",
    "import com.aqua.aqualight.databinding.DialogSettingsBottomSheetBinding\n"
    "import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDimensionInputPolicy\n"
    "import com.google.android.material.bottomsheet.BottomSheetDialogFragment",
    bottom_sheet_path,
)
text = replace_once(text, "import java.text.DecimalFormat\n", "", bottom_sheet_path)
text = replace_once(text, "import kotlin.math.roundToInt\n", "", bottom_sheet_path)
size_block = """    private fun bindSizeEditor() {
        val binding = ContentSheetTankSizeBinding.inflate(layoutInflater)
        val validationMessage = requireArguments().getString(ARG_VALIDATION_MESSAGE).orEmpty()

        fun unitLabel(): String = getString(
            if (selectedUnit == UNIT_IN) {
                R.string.aquarium_unit_inches
            } else {
                R.string.aquarium_unit_centimeters
            }
        )

        fun formatValue(cmValue: Int): String {
            return AquariumDimensionInputPolicy.format(
                context = requireContext(),
                centimeters = cmValue,
                unit = selectedUnit
            )
        }

        fun renderUnit() {
            binding.tvUnitValue.text = unitLabel()
        }

        fun inputViews() = listOf(
            binding.inputWidth,
            binding.inputLength,
            binding.inputHeight
        )

        fun convertInputsTo(newUnit: String): Boolean {
            val inputs = inputViews()
            val converted = inputs.map { input ->
                AquariumDimensionInputPolicy.convert(
                    context = requireContext(),
                    value = input.text,
                    fromUnit = selectedUnit,
                    toUnit = newUnit
                )
            }

            if (converted.any { it == null }) {
                converted.forEachIndexed { index, value ->
                    if (value == null) inputs[index].error = validationMessage
                }
                return false
            }

            inputs.forEachIndexed { index, input ->
                input.error = null
                input.setText(requireNotNull(converted[index]))
            }
            return true
        }

        binding.inputWidth.setText(formatValue(requireArguments().getInt(ARG_WIDTH_CM)))
        binding.inputLength.setText(formatValue(requireArguments().getInt(ARG_LENGTH_CM)))
        binding.inputHeight.setText(formatValue(requireArguments().getInt(ARG_HEIGHT_CM)))
        renderUnit()

        binding.unitRow.setOnClickListener {
            val newUnit = if (selectedUnit == UNIT_IN) UNIT_CM else UNIT_IN
            if (convertInputsTo(newUnit)) {
                selectedUnit = newUnit
                renderUnit()
            }
        }
        binding.btnCancel.setOnClickListener { cancelAndDismiss() }
        binding.btnSave.setOnClickListener {
            val widthCm = AquariumDimensionInputPolicy.parseCentimeters(
                requireContext(),
                binding.inputWidth.text,
                selectedUnit
            )
            val lengthCm = AquariumDimensionInputPolicy.parseCentimeters(
                requireContext(),
                binding.inputLength.text,
                selectedUnit
            )
            val heightCm = AquariumDimensionInputPolicy.parseCentimeters(
                requireContext(),
                binding.inputHeight.text,
                selectedUnit
            )

            var invalid = false
            if (widthCm == null) {
                binding.inputWidth.error = validationMessage
                invalid = true
            }
            if (lengthCm == null) {
                binding.inputLength.error = validationMessage
                invalid = true
            }
            if (heightCm == null) {
                binding.inputHeight.error = validationMessage
                invalid = true
            }
            if (invalid) return@setOnClickListener

            publishResult(
                status = RESULT_SAVED,
                widthCm = requireNotNull(widthCm),
                lengthCm = requireNotNull(lengthCm),
                heightCm = requireNotNull(heightCm),
                unit = selectedUnit
            )
            dismiss()
        }
        attachContent(binding.root)
    }

"""
text = replace_between(
    text,
    "    private fun bindSizeEditor() {",
    "    private fun bindSetupDateEditor() {",
    size_block,
    bottom_sheet_path,
)
text = replace_between(
    text,
    "    private fun toCentimeters(value: Double): Int {",
    "    private fun publishResult(",
    "",
    bottom_sheet_path,
)
text = replace_once(
    text,
    "        private const val CM_PER_INCH = 2.54\n",
    "",
    bottom_sheet_path,
)
write(bottom_sheet_path, text)

calculator_path = (
    "app/src/main/java/com/aqua/aqualight/application/aquarium/"
    "AquariumVolumeCalculator.kt"
)
write(
    calculator_path,
    """package com.aqua.aqualight.application.aquarium

/** Single overflow-safe source for tank volume calculations. */
object AquariumVolumeCalculator {
    private const val GALLONS_PER_LITER = 0.264172

    fun grossLiters(widthCm: Int, lengthCm: Int, heightCm: Int): Double {
        if (widthCm <= 0 || lengthCm <= 0 || heightCm <= 0) return 0.0

        return widthCm.toDouble() *
            lengthCm.toDouble() *
            heightCm.toDouble() / 1000.0
    }

    fun litersToGallons(liters: Double): Double {
        return if (liters.isFinite() && liters > 0.0) {
            liters * GALLONS_PER_LITER
        } else {
            0.0
        }
    }
}
""",
)

dimension_formatter_path = (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/common/"
    "AquariumDimensionFormatter.kt"
)
text = read(dimension_formatter_path)
text = replace_once(
    text,
    "import com.aqua.aqualight.R\nimport com.aqua.aqualight.i18n.LocaleFormatter",
    "import com.aqua.aqualight.R\n"
    "import com.aqua.aqualight.application.aquarium.AquariumVolumeCalculator\n"
    "import com.aqua.aqualight.i18n.LocaleFormatter",
    dimension_formatter_path,
)
text = replace_once(
    text,
    "    private const val GALLON_PER_LITER = 0.264172\n\n",
    "",
    dimension_formatter_path,
)
text = replace_once(
    text,
    "        val liters = (widthCm * lengthCm * heightCm) / 1000.0",
    """        val liters = AquariumVolumeCalculator.grossLiters(
            widthCm = widthCm,
            lengthCm = lengthCm,
            heightCm = heightCm
        )""",
    dimension_formatter_path,
)
text = replace_once(
    text,
    "            val gallons = liters * GALLON_PER_LITER",
    "            val gallons = AquariumVolumeCalculator.litersToGallons(liters)",
    dimension_formatter_path,
)
write(dimension_formatter_path, text)

smart_path = (
    "app/src/main/java/com/aqua/aqualight/data/care/smartcare/"
    "SmartCareProfileBuilder.kt"
)
text = read(smart_path)
text = replace_once(
    text,
    "package com.aqua.aqualight.data.care.smartcare\n\n"
    "import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial",
    "package com.aqua.aqualight.data.care.smartcare\n\n"
    "import com.aqua.aqualight.application.aquarium.AquariumVolumeCalculator\n"
    "import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial",
    smart_path,
)
text = replace_once(text, "import kotlin.math.roundToInt", "import kotlin.math.round", smart_path)
smart_volume_block = """  private fun calculateGrossVolumeL(
    tank: SavedAquariumTank
  ): Double {
    val volume = AquariumVolumeCalculator.grossLiters(
      widthCm = tank.widthCm,
      lengthCm = tank.lengthCm,
      heightCm = tank.heightCm
    )

    return round(volume * 10.0) / 10.0
  }

"""
text = replace_between(
    text,
    "  private fun calculateGrossVolumeL(",
    "  private fun buildConditions(",
    smart_volume_block,
    smart_path,
)
write(smart_path, text)

pdf_path = (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/export/"
    "TankPdfExporter.kt"
)
text = read(pdf_path)
text = replace_once(
    text,
    "import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection\n"
    "import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot",
    "import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection\n"
    "import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot\n"
    "import com.aqua.aqualight.application.aquarium.AquariumVolumeCalculator",
    pdf_path,
)
text = replace_once(text, "import java.text.DecimalFormat\n", "", pdf_path)
text = replace_once(
    text,
    '  private val volumeFormatter = DecimalFormat("#.##")\n\n',
    "",
    pdf_path,
)
pdf_volume_block = """  private fun getVolumeText(
    context: Context,
    tank: AquariumTankSnapshot
  ): String {
    val liters = AquariumVolumeCalculator.grossLiters(
      widthCm = tank.widthCm,
      lengthCm = tank.lengthCm,
      heightCm = tank.heightCm
    )

    return if (tank.volumeUnit.equals("gal", ignoreCase = true)) {
      context.getString(
        R.string.aquarium_volume_gallon_format,
        LocaleFormatter.formatDecimal(
          context,
          AquariumVolumeCalculator.litersToGallons(liters)
        )
      )
    } else {
      context.getString(
        R.string.aquarium_volume_liter_format,
        LocaleFormatter.formatDecimal(context, liters)
      )
    }
  }

"""
text = replace_between(
    text,
    "  private fun getVolumeText(",
    "  private fun createSafeFileName(",
    pdf_volume_block,
    pdf_path,
)
write(pdf_path, text)

locale_test_path = "app/src/test/java/com/aqua/aqualight/i18n/LocaleFormatterTest.kt"
text = read(locale_test_path)
text = replace_once(
    text,
    "import org.junit.Assert.assertNotEquals\nimport org.junit.Assert.assertTrue",
    "import org.junit.Assert.assertNotEquals\n"
    "import org.junit.Assert.assertNull\n"
    "import org.junit.Assert.assertTrue",
    locale_test_path,
)
decimal_test_marker = "    @Test\n    fun percentagesDatesAndTimesAreLocaleAware() {"
decimal_tests = """    @Test
    fun decimalInputAcceptsAppAndImeSeparatorsButRejectsGroupingAndMixedValues() {
        val turkish = Locale("tr", "TR")

        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12,5", turkish)), 0.0)
        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12.5", turkish)), 0.0)
        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12.5", Locale.US)), 0.0)
        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12,5", Locale.US)), 0.0)
        assertNull(LocaleFormatter.parseDecimal("1,234.5", Locale.US))
        assertNull(LocaleFormatter.parseDecimal("1.234,5", turkish))
        assertNull(LocaleFormatter.parseDecimal("12,", turkish))
        assertNull(LocaleFormatter.parseDecimal("NaN", Locale.US))
    }

"""
text = replace_once(
    text,
    decimal_test_marker,
    decimal_tests + decimal_test_marker,
    locale_test_path,
)
write(locale_test_path, text)

input_test_path = (
    "app/src/test/java/com/aqua/aqualight/ui/tabs/aquarium/common/"
    "AquariumDimensionInputPolicyTest.kt"
)
write(
    input_test_path,
    """package com.aqua.aqualight.ui.tabs.aquarium.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AquariumDimensionInputPolicyTest {

    @Test
    fun turkishAndEnglishValuesRoundTripAcrossCentimetersAndInches() {
        val turkish = Locale("tr", "TR")

        assertEquals("23,62", AquariumDimensionInputPolicy.format(60.0, "in", turkish))
        assertEquals(
            60,
            AquariumDimensionInputPolicy.parseCentimeters("23,62", "in", turkish)
        )
        assertEquals(
            "60",
            AquariumDimensionInputPolicy.convert("23,62", "in", "cm", turkish)
        )

        assertEquals("23.62", AquariumDimensionInputPolicy.format(60.0, "in", Locale.US))
        assertEquals(
            60,
            AquariumDimensionInputPolicy.parseCentimeters("23.62", "in", Locale.US)
        )
        assertEquals(
            "23.62",
            AquariumDimensionInputPolicy.convert("60", "cm", "in", Locale.US)
        )
    }

    @Test
    fun invalidOrOutOfRangeDimensionsAreRejectedBeforePersistence() {
        val turkish = Locale("tr", "TR")

        assertNull(AquariumDimensionInputPolicy.parseCentimeters("0", "cm", turkish))
        assertNull(AquariumDimensionInputPolicy.parseCentimeters("5000,1", "cm", turkish))
        assertNull(AquariumDimensionInputPolicy.parseCentimeters("1.000,5", "cm", turkish))
        assertNull(AquariumDimensionInputPolicy.convert("", "cm", "in", turkish))
    }
}
""",
)

calculator_test_path = (
    "app/src/test/java/com/aqua/aqualight/application/aquarium/"
    "AquariumVolumeCalculatorTest.kt"
)
write(
    calculator_test_path,
    """package com.aqua.aqualight.application.aquarium

import org.junit.Assert.assertEquals
import org.junit.Test

class AquariumVolumeCalculatorTest {

    @Test
    fun commercialMaximumDimensionsDoNotOverflowIntArithmetic() {
        assertEquals(
            125_000_000.0,
            AquariumVolumeCalculator.grossLiters(5000, 5000, 5000),
            0.0
        )
    }

    @Test
    fun standardVolumeAndGallonConversionRemainDeterministic() {
        val liters = AquariumVolumeCalculator.grossLiters(60, 40, 40)

        assertEquals(96.0, liters, 0.0)
        assertEquals(25.360512, AquariumVolumeCalculator.litersToGallons(liters), 0.000001)
        assertEquals(0.0, AquariumVolumeCalculator.grossLiters(0, 40, 40), 0.0)
    }
}
""",
)

architecture_test_path = (
    "app/src/test/java/com/aqua/aqualight/ui/tabs/aquarium/common/"
    "AquariumMeasurementArchitectureTest.kt"
)
write(
    architecture_test_path,
    """package com.aqua.aqualight.ui.tabs.aquarium.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumMeasurementArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun onlyTheSharedCalculatorOwnsThreeDimensionMultiplication() {
        val productionRoot = File(repositoryRoot, "app/src/main/java")
        val directVolumePattern = Regex(
            "(?:[A-Za-z0-9_]+\\.)?widthCm\\s*\\*\\s*" +
                "(?:[A-Za-z0-9_]+\\.)?lengthCm\\s*\\*\\s*" +
                "(?:[A-Za-z0-9_]+\\.)?heightCm"
        )
        val owners = productionRoot.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "kt" }
            .filter { directVolumePattern.containsMatchIn(it.readText()) }
            .mapTo(linkedSetOf()) { it.relativeTo(repositoryRoot).invariantSeparatorsPath }

        assertEquals(
            setOf(
                "app/src/main/java/com/aqua/aqualight/application/aquarium/" +
                    "AquariumVolumeCalculator.kt"
            ),
            owners
        )
    }

    @Test
    fun tankEditorAndPdfCannotBypassLocaleAndMeasurementBoundaries() {
        val editor = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/common/bottomsheet/" +
                "TankSettingsEditorBottomSheet.kt"
        ).readText()
        val pdf = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/export/" +
                "TankPdfExporter.kt"
        ).readText()

        assertTrue(editor.contains("AquariumDimensionInputPolicy.parseCentimeters("))
        assertTrue(editor.contains("AquariumDimensionInputPolicy.convert("))
        assertFalse(editor.contains("toDoubleOrNull()"))
        assertFalse(editor.contains("DecimalFormat("))

        assertTrue(pdf.contains("AquariumVolumeCalculator.grossLiters("))
        assertTrue(pdf.contains("LocaleFormatter.formatDecimal("))
        assertFalse(pdf.contains("DecimalFormat("))
    }

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
""",
)

print("Stage 5 measurement codemod completed")
