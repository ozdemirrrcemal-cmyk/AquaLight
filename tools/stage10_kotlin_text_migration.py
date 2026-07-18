#!/usr/bin/env python3
"""Wire remaining Kotlin UI call sites to Stage 10 string resources."""

from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java"
RES = ROOT / "app/src/main/res/values"


def file(path: str) -> Path:
    return ROOT / path


def replace(path: str, old: str, new: str) -> None:
    target = file(path)
    text = target.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Expected text not found in {path}: {old[:100]!r}")
    target.write_text(text.replace(old, new), encoding="utf-8")


def replace_all(path: str, old: str, new: str) -> None:
    target = file(path)
    text = target.read_text(encoding="utf-8")
    if old not in text:
        return
    target.write_text(text.replace(old, new), encoding="utf-8")


def ensure_strings() -> None:
    target = RES / "strings_device_menu.xml"
    text = target.read_text(encoding="utf-8")
    entries = {
        "device_provisioning_step_format": "%1$s %2$s",
        "reauth_google_error_title": "Google Error",
        "tank_settings_field_type_title": "Tank Type",
        "tank_settings_field_setup_date_title": "Setup Date",
        "tank_settings_field_style_title": "Tank Style",
    }
    additions = []
    for name, value in entries.items():
        if f'name="{name}"' not in text:
            additions.append(f'    <string name="{name}">{value}</string>')
    if additions:
        text = text.replace(
            "</resources>",
            "\n    <!-- Stage 10: remaining Kotlin UI labels -->\n"
            + "\n".join(additions)
            + "\n</resources>",
        )
        target.write_text(text, encoding="utf-8")


def wire_factory() -> None:
    path = "app/src/main/java/com/aqua/aqualight/composition/OwnerViewModelFactory.kt"
    replace_all(path, "routeResolver = DeviceRouteResolver()", "routeResolver = DeviceRouteResolver(appTextResolver)")
    replace(
        path,
        """DeviceLightRootViewModel(
                    rootOperations = DefaultDeviceRootOperations(repository),
                    firmwareUpdateOperations = DefaultDeviceFirmwareUpdateOperations(repository)
                )""",
        """DeviceLightRootViewModel(
                    rootOperations = DefaultDeviceRootOperations(repository),
                    firmwareUpdateOperations = DefaultDeviceFirmwareUpdateOperations(repository),
                    textResolver = appTextResolver
                )""",
    )
    replace(
        path,
        "DeviceCoolingRootViewModel(DefaultDeviceRootOperations(repository))",
        """DeviceCoolingRootViewModel(
                    operations = DefaultDeviceRootOperations(repository),
                    textResolver = appTextResolver
                )""",
    )
    replace(
        path,
        "DeviceTimerRootViewModel(DefaultDeviceRootOperations(repository))",
        """DeviceTimerRootViewModel(
                    operations = DefaultDeviceRootOperations(repository),
                    textResolver = appTextResolver
                )""",
    )
    replace(
        path,
        "DeviceDosingRootViewModel(DefaultDeviceRootOperations(repository))",
        """DeviceDosingRootViewModel(
                    operations = DefaultDeviceRootOperations(repository),
                    textResolver = appTextResolver
                )""",
    )
    replace(
        path,
        "DeviceRootOverviewViewModel(DefaultDeviceRootOperations(repository))",
        """DeviceRootOverviewViewModel(
                    operations = DefaultDeviceRootOperations(repository),
                    textResolver = appTextResolver
                )""",
    )


def migrate_root_fragment(path: str, title_res: str, count_format: str = "device_runtime_labeled_value_format") -> None:
    replace_all(
        path,
        "setupHeader(title = args.deviceTitle.ifBlank { DEFAULT_TITLE })",
        f"setupHeader(title = args.deviceTitle.ifBlank {{ getString(R.string.{title_res}) }})",
    )
    replace_all(
        path,
        'binding.tvDeviceUid.text = state.deviceUid.ifBlank { "Unknown device" }',
        "binding.tvDeviceUid.text = state.deviceUid.ifBlank { getString(R.string.device_runtime_unknown_device) }",
    )
    replace_all(path, 'binding.tvIp.text = "IP: ${state.ipText}"', "binding.tvIp.text = getString(R.string.device_runtime_ip_format, state.ipText)")
    replace_all(path, 'binding.tvFirmware.text = "Firmware: ${state.firmwareText}"', "binding.tvFirmware.text = getString(R.string.device_runtime_firmware_format, state.firmwareText)")
    replace_all(path, 'binding.tvModel.text = "Model: ${state.modelText}"', "binding.tvModel.text = getString(R.string.device_runtime_model_format, state.modelText)")
    replace_all(path, 'binding.tvPrimaryCount.text = "${state.primaryCountLabel}: ${state.primaryCountText}"', f"binding.tvPrimaryCount.text = getString(R.string.{count_format}, state.primaryCountLabel, state.primaryCountText)")
    replace_all(path, 'binding.tvFeatures.text = "Features: ${state.featuresText}"', "binding.tvFeatures.text = getString(R.string.device_runtime_features_format, state.featuresText)")
    target = file(path)
    text = target.read_text(encoding="utf-8")
    text = re.sub(
        r"\n\s*private companion object \{\s*const val DEFAULT_TITLE = \"(?:Dosing|Timer|Cooling)\"\s*}\s*\n",
        "\n",
        text,
    )
    target.write_text(text, encoding="utf-8")


def migrate_light_fragment() -> None:
    path = "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/DeviceLightRootFragment.kt"
    replace_all(path, 'setupHeader(title = args.deviceTitle.ifBlank { "Light" })', "setupHeader(title = args.deviceTitle.ifBlank { getString(R.string.device_root_light_title) })")
    replace_all(path, 'binding.tvDeviceUid.text = state.deviceUid.ifBlank { "Unknown device" }', "binding.tvDeviceUid.text = state.deviceUid.ifBlank { getString(R.string.device_runtime_unknown_device) }")
    replace_all(path, 'binding.tvIp.text = "IP: ${state.ipText}"', "binding.tvIp.text = getString(R.string.device_runtime_ip_format, state.ipText)")
    replace_all(path, 'binding.tvFirmware.text = "Firmware: ${state.firmwareText}"', "binding.tvFirmware.text = getString(R.string.device_runtime_firmware_format, state.firmwareText)")
    replace_all(path, 'binding.tvModel.text = "Model: ${state.modelText}"', "binding.tvModel.text = getString(R.string.device_runtime_model_format, state.modelText)")
    replace_all(path, 'binding.tvChannelCount.text = "Light channels: ${state.channelCountText}"', "binding.tvChannelCount.text = getString(R.string.device_runtime_light_channels_format, state.channelCountText)")
    replace_all(path, 'binding.tvFeatures.text = "Features: ${state.featuresText}"', "binding.tvFeatures.text = getString(R.string.device_runtime_features_format, state.featuresText)")


def migrate_categories() -> None:
    replace_all(
        "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/create/steps/TankMaterialFragment.kt",
        "item.title",
        "getString(item.titleRes)",
    )
    replace_all(
        "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/settings/TankSettingsDetailsFragment.kt",
        "category.title",
        "getString(category.titleRes)",
    )
    replace_all(
        "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/TankDetailTankFragment.kt",
        "category.title",
        "getString(category.titleRes)",
    )
    replace_all(
        "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/export/TankPdfExporter.kt",
        "title = category.title",
        "title = context.getString(category.titleRes)",
    )
    path = "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/careprofile/CareProfileCalculator.kt"
    replace_all(path, "val category = findMaterialCategory(keywords)", "val category = findMaterialCategory(context, keywords)")
    replace_all(
        path,
        """private fun findMaterialCategory(
    keywords: Array<String>
  ): Pair<String, String>?""",
        """private fun findMaterialCategory(
    context: Context,
    keywords: Array<String>
  ): Pair<String, String>?""",
    )
    replace_all(path, 'value = "${item.key} ${item.title}"', 'value = "${item.key} ${context.getString(item.titleRes)}"')
    replace_all(path, "return category?.let { it.key to it.title }", "return category?.let { it.key to context.getString(it.titleRes) }")


def migrate_small_ui_sinks() -> None:
    replace_all(
        "app/src/main/java/com/aqua/aqualight/ui/auth/security/ReAuthenticateFragment.kt",
        'title = "Google Error"',
        "title = getString(R.string.reauth_google_error_title)",
    )
    basic = "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/settings/TankSettingsBasicFragment.kt"
    replace_all(basic, 'title = "Tank Type"', "title = getString(R.string.tank_settings_field_type_title)")
    replace_all(basic, 'title = "Setup Date"', "title = getString(R.string.tank_settings_field_setup_date_title)")
    replace_all(basic, 'title = "Tank Style"', "title = getString(R.string.tank_settings_field_style_title)")
    candidate = "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/add/DeviceAddCandidateAdapter.kt"
    replace_all(candidate, 'binding.tvDeviceSerial.text = "Serial: ${candidate.serial}"', "binding.tvDeviceSerial.text = binding.root.context.getString(R.string.device_runtime_serial_format, candidate.serial)")
    progress = "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/add/DeviceProvisioningProgressFragment.kt"
    replace_all(progress, 'binding.tvStepOne.text = "✓ ${state.stepOne}"', 'binding.tvStepOne.text = getString(R.string.device_provisioning_step_format, "✓", state.stepOne)')
    replace_all(progress, 'binding.tvStepTwo.text = "✓ ${state.stepTwo}"', 'binding.tvStepTwo.text = getString(R.string.device_provisioning_step_format, "✓", state.stepTwo)')
    replace_all(progress, 'binding.tvStepThree.text = "${state.currentStepIcon()} ${state.stepThree}"', 'binding.tvStepThree.text = getString(R.string.device_provisioning_step_format, state.currentStepIcon(), state.stepThree)')
    replace_all(progress, "message = UNSUPPORTED_FAMILY_MESSAGE", "message = getString(R.string.device_route_unsupported_family)")
    target = file(progress)
    text = target.read_text(encoding="utf-8")
    text = re.sub(
        r"\n\s*const val UNSUPPORTED_FAMILY_MESSAGE =\s*\n?\s*\"Unsupported AquaLight device family\. Firmware did not provide a known product\.family value\.\"",
        "",
        text,
    )
    target.write_text(text, encoding="utf-8")


def remove_night_legacy_styles() -> None:
    target = file("app/src/main/res/values-night/styles.xml")
    target.write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources />\n', encoding="utf-8")


def main() -> None:
    ensure_strings()
    wire_factory()
    migrate_root_fragment(
        "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/DeviceDosingRootFragment.kt",
        "device_root_dosing_title",
    )
    migrate_root_fragment(
        "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/timer/DeviceTimerRootFragment.kt",
        "device_root_timer_title",
    )
    migrate_root_fragment(
        "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/DeviceCoolingRootFragment.kt",
        "device_root_cooling_title",
    )
    migrate_light_fragment()
    migrate_categories()
    migrate_small_ui_sinks()
    remove_night_legacy_styles()
    print("Stage 10 Kotlin UI text wiring migration complete.")


if __name__ == "__main__":
    main()
