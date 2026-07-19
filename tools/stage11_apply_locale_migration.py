#!/usr/bin/env python3
"""Apply the verified Stage 11 app-locale migration atomically."""
from __future__ import annotations

import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def path(relative: str) -> Path:
    return ROOT / relative


def read(relative: str) -> str:
    return path(relative).read_text(encoding="utf-8")


def write(relative: str, content: str) -> None:
    path(relative).write_text(content, encoding="utf-8")


def replace_exact(relative: str, old: str, new: str, expected: int = 1) -> None:
    source = read(relative)
    actual = source.count(old)
    if actual != expected:
        raise RuntimeError(
            f"{relative}: expected {expected} exact matches, found {actual}: {old!r}"
        )
    write(relative, source.replace(old, new))


def ensure_import(relative: str, import_line: str, anchor: str = "import com.aqua.aqualight.R\n") -> None:
    source = read(relative)
    if import_line in source:
        return
    if anchor not in source:
        raise RuntimeError(f"{relative}: import anchor missing: {anchor!r}")
    write(relative, source.replace(anchor, anchor + import_line, 1))


def remove_unused_locale_import(relative: str) -> None:
    source = read(relative)
    body = source.replace("import java.util.Locale\n", "")
    if "Locale." not in body:
        write(relative, body)


def replace_default_locale(relative: str, expression: str, expected: int) -> None:
    ensure_import(relative, "import com.aqua.aqualight.localization.LocaleFormatters\n")
    replace_exact(relative, "Locale.getDefault()", expression, expected)
    remove_unused_locale_import(relative)


def migrate_bottom_sheet() -> None:
    relative = "app/src/main/java/com/aqua/aqualight/ui/common/bottomsheet/TankSettingsEditorBottomSheet.kt"
    ensure_import(relative, "import com.aqua.aqualight.localization.LocaleFormatters\n")
    replace_exact(relative, "import java.text.DecimalFormat\n", "")
    replace_exact(
        relative,
        '        val formatter = DecimalFormat("#.##")\n',
        "        val locale = LocaleFormatters.currentLocale(requireContext())\n",
    )
    replace_exact(
        relative,
        "            return formatter.format(value)\n",
        "            return LocaleFormatters.formatNumber(value, locale)\n",
    )
    replace_exact(
        relative,
        "            val width = binding.inputWidth.text.toString().trim().toDoubleOrNull()\n"
        "            val length = binding.inputLength.text.toString().trim().toDoubleOrNull()\n"
        "            val height = binding.inputHeight.text.toString().trim().toDoubleOrNull()\n",
        "            val width = LocaleFormatters.parseNumber(binding.inputWidth.text, locale)?.toDouble()\n"
        "            val length = LocaleFormatters.parseNumber(binding.inputLength.text, locale)?.toDouble()\n"
        "            val height = LocaleFormatters.parseNumber(binding.inputHeight.text, locale)?.toDouble()\n",
    )
    replace_exact(
        relative,
        "        val locale = Locale.forLanguageTag(args.getString(ARG_LOCALE_TAG).orEmpty())\n"
        "            .takeUnless { it.language.isBlank() }\n"
        "            ?: Locale.getDefault()\n",
        "        val locale = LocaleFormatters.currentLocale(requireContext())\n",
    )
    replace_exact(relative, '        private const val ARG_LOCALE_TAG = "arg_locale_tag"\n', "")
    replace_exact(relative, "            locale: Locale = Locale.getDefault(),\n", "")
    replace_exact(relative, "                    ARG_LOCALE_TAG to locale.toLanguageTag(),\n", "")
    remove_unused_locale_import(relative)

    for caller in (
        "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/create/steps/TankInfoFragment.kt",
        "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/settings/TankSettingsBasicFragment.kt",
    ):
        source = read(caller)
        updated, count = re.subn(
            r",?\n\s*locale\s*=\s*AquariumDatePolicy\.setupDateLocale",
            "",
            source,
        )
        if count != 1:
            raise RuntimeError(f"{caller}: expected one setupDateLocale argument, found {count}")
        write(caller, updated)


def migrate_date_policy() -> None:
    relative = "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/common/AquariumDatePolicy.kt"
    write(
        relative,
        """package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import com.aqua.aqualight.localization.LocaleFormatters
import java.util.Calendar

object AquariumDatePolicy {
    private const val SETUP_DATE_PATTERN = "dd MMM yyyy"
    private const val MIN_SETUP_YEAR = 2000
    private const val MAX_YEAR_OFFSET = 10

    fun minSetupYear(): Int = MIN_SETUP_YEAR

    fun maxSetupYear(): Int {
        return Calendar.getInstance().get(Calendar.YEAR) + MAX_YEAR_OFFSET
    }

    fun formatSetupDate(
        context: Context,
        millis: Long?,
        emptyText: String
    ): String {
        if (millis == null) {
            return emptyText
        }

        return LocaleFormatters.formatPattern(
            context = context,
            millis = millis,
            pattern = SETUP_DATE_PATTERN
        )
    }
}
""",
    )

    call_count = 0
    for kotlin_file in (ROOT / "app/src/main/java/com/aqua/aqualight/ui").rglob("*.kt"):
        source = kotlin_file.read_text(encoding="utf-8")
        updated, count = re.subn(
            r"AquariumDatePolicy\.formatSetupDate\(\n(?P<indent>\s*)millis\s*=",
            r"AquariumDatePolicy.formatSetupDate(\n\g<indent>context = requireContext(),\n\g<indent>millis =",
            source,
        )
        if count:
            kotlin_file.write_text(updated, encoding="utf-8")
            call_count += count
    if call_count != 3:
        raise RuntimeError(f"AquariumDatePolicy.formatSetupDate: expected 3 call sites, found {call_count}")

    for kotlin_file in (ROOT / "app/src/main/java").rglob("*.kt"):
        if "setupDateLocale" in kotlin_file.read_text(encoding="utf-8"):
            raise RuntimeError(f"legacy setupDateLocale remains in {kotlin_file.relative_to(ROOT)}")


def migrate_dimension_formatter() -> None:
    relative = "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/common/AquariumDimensionFormatter.kt"
    source = read(relative)
    source = source.replace("import java.text.DecimalFormat\n", "")
    source = source.replace("import java.text.DecimalFormatSymbols\n", "")
    source = source.replace("import java.util.Locale\n", "")
    source = source.replace(
        "import com.aqua.aqualight.R\n",
        "import com.aqua.aqualight.R\nimport com.aqua.aqualight.localization.LocaleFormatters\n",
        1,
    )
    source = source.replace(
        "\n    private val preciseFormatter = DecimalFormat(\n"
        "        \"#0.##\",\n"
        "        DecimalFormatSymbols(Locale.US)\n"
        "    )\n",
        "",
        1,
    )
    source = source.replace("preciseFormatter.format(widthIn)", "LocaleFormatters.formatNumber(context, widthIn)")
    source = source.replace("preciseFormatter.format(lengthIn)", "LocaleFormatters.formatNumber(context, lengthIn)")
    source = source.replace("preciseFormatter.format(heightIn)", "LocaleFormatters.formatNumber(context, heightIn)")
    source = source.replace("widthCm.toString()", "LocaleFormatters.formatInteger(context, widthCm.toLong())")
    source = source.replace("lengthCm.toString()", "LocaleFormatters.formatInteger(context, lengthCm.toLong())")
    source = source.replace("heightCm.toString()", "LocaleFormatters.formatInteger(context, heightCm.toLong())")
    source = source.replace(
        "if (rounded) gallons.roundToInt().toString() else preciseFormatter.format(gallons)",
        "if (rounded) {\n"
        "                    LocaleFormatters.formatInteger(context, gallons.roundToInt().toLong())\n"
        "                } else {\n"
        "                    LocaleFormatters.formatNumber(context, gallons)\n"
        "                }",
    )
    source = source.replace(
        "if (rounded) liters.roundToInt().toString() else preciseFormatter.format(liters)",
        "if (rounded) {\n"
        "                    LocaleFormatters.formatInteger(context, liters.roundToInt().toLong())\n"
        "                } else {\n"
        "                    LocaleFormatters.formatNumber(context, liters)\n"
        "                }",
    )
    if "DecimalFormat" in source or "Locale.US" in source or "preciseFormatter" in source:
        raise RuntimeError("AquariumDimensionFormatter migration incomplete")
    write(relative, source)


def migrate_maintenance_boundary() -> None:
    interface = "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/text/MaintenanceTextResolver.kt"
    replace_exact(
        interface,
        "    fun unknownAquarium(): String\n",
        "    fun unknownAquarium(): String\n\n    fun formatTime(millis: Long): String\n",
    )

    resolver = "app/src/main/java/com/aqua/aqualight/platform/text/AndroidMaintenanceTextResolver.kt"
    ensure_import(resolver, "import com.aqua.aqualight.localization.LocaleFormatters\n")
    replace_exact(
        resolver,
        "    override fun unknownAquarium(): String =\n"
        "        appContext.getString(R.string.maintenance_unknown_aquarium)\n",
        "    override fun unknownAquarium(): String =\n"
        "        appContext.getString(R.string.maintenance_unknown_aquarium)\n\n"
        "    override fun formatTime(millis: Long): String =\n"
        "        LocaleFormatters.formatPattern(\n"
        "            context = appContext,\n"
        "            millis = millis,\n"
        "            pattern = \"HH:mm\"\n"
        "        )\n",
    )

    view_model = "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/MaintenanceViewModel.kt"
    source = read(view_model)
    for import_line in (
        "import java.text.SimpleDateFormat\n",
        "import java.util.Date\n",
        "import java.util.Locale\n",
    ):
        source = source.replace(import_line, "")
    source = source.replace("textResolver.completedTime(formatTime(completedAt))", "textResolver.completedTime(textResolver.formatTime(completedAt))", 1)
    source = source.replace("val timeText = formatTime(task.dueAtMillis)", "val timeText = textResolver.formatTime(task.dueAtMillis)", 1)
    block = (
        "\n    private fun formatTime(millis: Long): String = SimpleDateFormat(\n"
        "        \"HH:mm\",\n"
        "        Locale.getDefault()\n"
        "    ).format(Date(millis))\n"
    )
    if source.count(block) != 1:
        raise RuntimeError("MaintenanceViewModel formatTime block mismatch")
    source = source.replace(block, "")
    write(view_model, source)

    test = "app/src/test/java/com/aqua/aqualight/ui/tabs/maintenance/MaintenanceViewModelBoundaryTest.kt"
    replace_exact(
        test,
        '        override fun unknownAquarium() = "unknown"\n',
        '        override fun unknownAquarium() = "unknown"\n'
        '        override fun formatTime(millis: Long) = "00:00"\n',
    )


def migrate_pdf() -> None:
    relative = "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/export/TankPdfExporter.kt"
    source = read(relative)
    source = source.replace(
        "import com.aqua.aqualight.R\n",
        "import com.aqua.aqualight.R\nimport com.aqua.aqualight.localization.LocaleFormatters\n",
        1,
    )
    for import_line in (
        "import java.text.DecimalFormat\n",
        "import java.text.SimpleDateFormat\n",
        "import java.util.Date\n",
    ):
        source = source.replace(import_line, "")
    source = source.replace("\n  private val volumeFormatter = DecimalFormat(\"#.##\")\n", "", 1)
    source = source.replace("generatedDate = getGeneratedDateText()", "generatedDate = getGeneratedDateText(context)", 1)
    source = source.replace(
        "getSetupDateText(\n      setupDateMillis =",
        "getSetupDateText(\n      context = context,\n      setupDateMillis =",
        1,
    )
    source = source.replace(
        "  private fun getGeneratedDateText(): String {\n"
        "    return SimpleDateFormat(\n"
        "      \"dd MMM yyyy HH:mm\",\n"
        "      Locale.getDefault()\n"
        "    ).format(Date())\n"
        "  }\n",
        "  private fun getGeneratedDateText(context: Context): String =\n"
        "    LocaleFormatters.formatPattern(\n"
        "      context = context,\n"
        "      millis = System.currentTimeMillis(),\n"
        "      pattern = \"dd MMM yyyy HH:mm\"\n"
        "    )\n",
        1,
    )
    source = source.replace(
        "  private fun getSetupDateText(\n"
        "    setupDateMillis: Long?,\n"
        "    noValue: String\n"
        "  ): String {\n"
        "    if (setupDateMillis == null) {\n"
        "      return noValue\n"
        "    }\n\n"
        "    return SimpleDateFormat(\n"
        "      \"dd MMM yyyy\",\n"
        "      Locale.getDefault()\n"
        "    ).format(Date(setupDateMillis))\n"
        "  }\n",
        "  private fun getSetupDateText(\n"
        "    context: Context,\n"
        "    setupDateMillis: Long?,\n"
        "    noValue: String\n"
        "  ): String {\n"
        "    if (setupDateMillis == null) {\n"
        "      return noValue\n"
        "    }\n\n"
        "    return LocaleFormatters.formatPattern(\n"
        "      context = context,\n"
        "      millis = setupDateMillis,\n"
        "      pattern = \"dd MMM yyyy\"\n"
        "    )\n"
        "  }\n",
        1,
    )
    source = source.replace("volumeFormatter.format(liter * 0.264172)", "LocaleFormatters.formatNumber(context, liter * 0.264172)")
    source = source.replace("volumeFormatter.format(liter)", "LocaleFormatters.formatNumber(context, liter)")
    source = source.replace(
        "  private data class TankPdfTexts(\n",
        "  private data class TankPdfTexts(\n    val locale: Locale,\n",
        1,
    )
    source = source.replace(
        "      return String.format(\n"
        "        Locale.getDefault(),\n"
        "        pageFormat,\n"
        "        pageNumber\n"
        "      )\n",
        "      return LocaleFormatters.formatTemplate(\n"
        "        locale = locale,\n"
        "        template = pageFormat,\n"
        "        pageNumber\n"
        "      )\n",
        1,
    )
    source = source.replace(
        "        return TankPdfTexts(\n",
        "        return TankPdfTexts(\n          locale = LocaleFormatters.currentLocale(context),\n",
        1,
    )
    if any(token in source for token in ("DecimalFormat", "Locale.getDefault()", "String.format", "volumeFormatter")):
        raise RuntimeError("TankPdfExporter migration incomplete")
    write(relative, source)


def migrate_feedback() -> None:
    relative = "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/feedback/FeedbackViewModel.kt"
    ensure_import(relative, "import com.aqua.aqualight.localization.LocaleFormatters\n")
    replace_exact(
        relative,
        "private val localeTagProvider: () -> String = { Locale.getDefault().toLanguageTag() }",
        "private val localeTagProvider: () -> String = LocaleFormatters::currentLanguageTag",
    )
    replace_exact(
        relative,
        "localeTagProvider: () -> String = { Locale.getDefault().toLanguageTag() }",
        "localeTagProvider: () -> String = LocaleFormatters::currentLanguageTag",
    )
    remove_unused_locale_import(relative)


def migrate_direct_locale_calls() -> None:
    mappings = {
        "app/src/main/java/com/aqua/aqualight/ui/common/material/AquaMaterialCategoryRowFactory.kt": ("LocaleFormatters.currentLocale(context)", 1),
        "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/AquariumTankAdapter.kt": ("LocaleFormatters.currentLocale(context)", 1),
        "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/TankDetailActivityFragment.kt": ("LocaleFormatters.currentLocale(requireContext())", 4),
        "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/TankDetailLifeFragment.kt": ("LocaleFormatters.currentLocale(requireContext())", 1),
        "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/TankDetailLivestockFormFragment.kt": ("LocaleFormatters.currentLocale(requireContext())", 1),
        "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/TankDetailTankFragment.kt": ("LocaleFormatters.currentLocale(requireContext())", 1),
        "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/AddCareTaskFragment.kt": ("LocaleFormatters.currentLocale(requireContext())", 2),
        "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/AquariumMaintenanceFragment.kt": ("LocaleFormatters.currentLocale(requireContext())", 4),
        "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/CareTaskAdapter.kt": ("LocaleFormatters.currentLocale(context)", 2),
        "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/TaskDetailFragment.kt": ("LocaleFormatters.currentLocale(requireContext())", 3),
        "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/usage/UsageFragment.kt": ("LocaleFormatters.currentLocale(requireContext())", 1),
    }
    for relative, (expression, expected) in mappings.items():
        replace_default_locale(relative, expression, expected)

    replace_exact(
        "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/device/DeviceStatusSnapshotMapper.kt",
        "Locale.US",
        "Locale.ROOT",
    )


def main() -> None:
    migrate_bottom_sheet()
    migrate_date_policy()
    migrate_dimension_formatter()
    migrate_maintenance_boundary()
    migrate_pdf()
    migrate_feedback()
    migrate_direct_locale_calls()

    subprocess.run(
        ["python3", "tools/ui_locale_boundary_guard.py"],
        cwd=ROOT,
        check=True,
    )
    subprocess.run(
        ["python3", "tools/localization_placeholder_guard.py"],
        cwd=ROOT,
        check=True,
    )
    print("STAGE11_LOCALE_MIGRATION_PASS")


if __name__ == "__main__":
    main()
