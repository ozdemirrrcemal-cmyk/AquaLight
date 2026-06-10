# AquaLight unused file cleanup report

Date: 2026-06-10

## Removed files

The following files were removed because they had no static references from Kotlin/XML resources or generated ViewBinding names. The cleanup intentionally stayed conservative and did not remove the uCrop layout override (`ucrop_activity_photobox.xml`) because external library resource lookup can consume it after resource merge.

- `app/src/main/res/layout/item_light_curve_point_row.xml`
- `app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/settings/model/TemperatureSettingOption.kt`
- `app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/curve/calculator/LightCurveStatsCalculator.kt`
- `app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/light/curve/model/LightCurveStats.kt`
- `app/src/main/res/drawable/bg_device_remove_icon_circle.xml`
- `app/src/main/res/drawable/bg_light_card.xml`
- `app/src/main/res/drawable/bg_light_card_elevated.xml`
- `app/src/main/res/drawable/bg_light_editor_input.xml`
- `app/src/main/res/drawable/bg_light_mode_chip_offline.xml`
- `app/src/main/res/drawable/bg_light_online_chip.xml`
- `app/src/main/res/drawable/ic_close_20.xml`
- `app/src/main/res/drawable/ic_device_co2.png`
- `app/src/main/res/drawable/ic_device_doser.png`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable-v24/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_light_settings.xml`
- `app/src/main/res/drawable/ic_light_settings_24.xml`
- `app/src/main/res/drawable/ic_light_settings_thermometer_24.xml`
- `app/src/main/res/drawable/theme_bottomsheet_item.xml`


## Validation performed

- Static reference scan for `R.layout.*`, `@layout/*`, generated `*Binding` names.
- Static reference scan for `R.drawable.*`, `@drawable/*`.
- Kotlin class/object/interface reference scan with one iterative pass for classes made unused by prior removals.
- XML well-formedness check after deletion.
- Gradle build was attempted in this environment, but could not run because the Gradle distribution could not be downloaded without internet access.

## Non-runtime design reference fixes

- Fixed `tools:layout` typo in `nav_app.xml`: `fragment_device_light_program_editör` -> `fragment_device_light_program_editor`.
- Replaced missing design-time `tools:src` in `layout_aqua_header.xml`: `ic_device_status_24` -> `ic_status_wifi`.

## Final static scan result

- Remaining local layout candidates: `ucrop_activity_photobox.xml` only, intentionally kept for uCrop compatibility.
- Remaining local drawable candidates: 0.
- Remaining Kotlin class candidates in `app/src/main/java`: 0.
- References to removed `R.layout`, `@layout`, `R.drawable`, `@drawable`, and Kotlin class names: 0.
- XML parse errors: 0.

## Build attempt result

`./gradlew :app:assembleDebug --stacktrace` was attempted. It could not start in this sandbox because the Gradle wrapper needs to download `gradle-8.11.1-all.zip` from `services.gradle.org`, and network access is unavailable in this environment.
