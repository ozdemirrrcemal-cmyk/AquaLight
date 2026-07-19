package com.aqua.aqualight.ui.tabs.maintenance.text

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.care.CareTaskType

data class CareTaskTypeDefinition(
  val type: CareTaskType,
  @StringRes val titleRes: Int,
  @StringRes val categoryRes: Int,
  val iconRes: Int,
  @ColorRes val accentColorRes: Int,
  @StringRes val defaultDescriptionRes: Int
) {
  fun title(context: Context): String = context.getString(titleRes)

  fun category(context: Context): String = context.getString(categoryRes)

  fun defaultDescription(context: Context): String =
    context.getString(defaultDescriptionRes)
}

object CareTaskTypeCatalog {
  val all: List<CareTaskTypeDefinition> = listOf(
    CareTaskTypeDefinition(
      type = CareTaskType.WATER_CHANGE,
      titleRes = R.string.maintenance_task_type_water_change,
      categoryRes = R.string.maintenance_category_water_care,
      iconRes = R.drawable.ic_care_water_change_24,
      accentColorRes = R.color.aqua_accent_primary,
      defaultDescriptionRes = R.string.maintenance_task_desc_water_change
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.WATER_TEST,
      titleRes = R.string.maintenance_task_type_water_test,
      categoryRes = R.string.maintenance_category_water_care,
      iconRes = R.drawable.ic_care_water_test_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color,
      defaultDescriptionRes = R.string.maintenance_task_desc_water_test
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.TEMPERATURE_CHECK,
      titleRes = R.string.maintenance_task_type_temperature_check,
      categoryRes = R.string.maintenance_category_water_care,
      iconRes = R.drawable.ic_care_temperature_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_2,
      defaultDescriptionRes = R.string.maintenance_task_desc_temperature_check
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.SUBSTRATE_CLEANING,
      titleRes = R.string.maintenance_task_type_substrate_cleaning,
      categoryRes = R.string.maintenance_category_water_care,
      iconRes = R.drawable.ic_care_substrate_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_3,
      defaultDescriptionRes = R.string.maintenance_task_desc_substrate_cleaning
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.FILTER_MAINTENANCE,
      titleRes = R.string.maintenance_task_type_filter_maintenance,
      categoryRes = R.string.maintenance_category_equipment,
      iconRes = R.drawable.ic_care_filter_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_4,
      defaultDescriptionRes = R.string.maintenance_task_desc_filter_maintenance
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.FILTER_CHANGE,
      titleRes = R.string.maintenance_task_type_filter_change,
      categoryRes = R.string.maintenance_category_equipment,
      iconRes = R.drawable.ic_care_filter_change_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_5,
      defaultDescriptionRes = R.string.maintenance_task_desc_filter_change
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.PRE_FILTER_CLEANING,
      titleRes = R.string.maintenance_task_type_pre_filter_cleaning,
      categoryRes = R.string.maintenance_category_equipment,
      iconRes = R.drawable.ic_care_prefilter_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_6,
      defaultDescriptionRes = R.string.maintenance_task_desc_pre_filter_cleaning
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.PIPE_CLEANING,
      titleRes = R.string.maintenance_task_type_pipe_cleaning,
      categoryRes = R.string.maintenance_category_equipment,
      iconRes = R.drawable.ic_care_pipe_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_7,
      defaultDescriptionRes = R.string.maintenance_task_desc_pipe_cleaning
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.DIFFUSER_CLEANING,
      titleRes = R.string.maintenance_task_type_diffuser_cleaning,
      categoryRes = R.string.maintenance_category_equipment,
      iconRes = R.drawable.ic_care_diffuser_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_8,
      defaultDescriptionRes = R.string.maintenance_task_desc_diffuser_cleaning
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.HOSE_CLEANING,
      titleRes = R.string.maintenance_task_type_hose_cleaning,
      categoryRes = R.string.maintenance_category_equipment,
      iconRes = R.drawable.ic_care_hose_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_9,
      defaultDescriptionRes = R.string.maintenance_task_desc_hose_cleaning
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.DEVICE_CHECK,
      titleRes = R.string.maintenance_task_type_device_check,
      categoryRes = R.string.maintenance_category_equipment,
      iconRes = R.drawable.ic_care_device_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_10,
      defaultDescriptionRes = R.string.maintenance_task_desc_device_check
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.GLASS_CLEANING,
      titleRes = R.string.maintenance_task_type_glass_cleaning,
      categoryRes = R.string.maintenance_category_cleaning,
      iconRes = R.drawable.ic_care_glass_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_11,
      defaultDescriptionRes = R.string.maintenance_task_desc_glass_cleaning
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.ALGAE_CLEANING,
      titleRes = R.string.maintenance_task_type_algae_cleaning,
      categoryRes = R.string.maintenance_category_cleaning,
      iconRes = R.drawable.ic_care_algae_24,
      accentColorRes = R.color.aqua_status_success,
      defaultDescriptionRes = R.string.maintenance_task_desc_algae_cleaning
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.PLANT_TRIM,
      titleRes = R.string.maintenance_task_type_plant_trim,
      categoryRes = R.string.maintenance_category_plants,
      iconRes = R.drawable.ic_care_trim_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_12,
      defaultDescriptionRes = R.string.maintenance_task_desc_plant_trim
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.FERTILIZER_DOSING,
      titleRes = R.string.maintenance_task_type_fertilizer_dosing,
      categoryRes = R.string.maintenance_category_plants,
      iconRes = R.drawable.ic_care_fertilizer_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_13,
      defaultDescriptionRes = R.string.maintenance_task_desc_fertilizer_dosing
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.PLANT_HEALTH_CHECK,
      titleRes = R.string.maintenance_task_type_plant_health_check,
      categoryRes = R.string.maintenance_category_plants,
      iconRes = R.drawable.ic_care_plant_health_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_14,
      defaultDescriptionRes = R.string.maintenance_task_desc_plant_health_check
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.CO2_CHECK,
      titleRes = R.string.maintenance_task_type_co2_check,
      categoryRes = R.string.maintenance_category_equipment,
      iconRes = R.drawable.ic_care_co2_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_15,
      defaultDescriptionRes = R.string.maintenance_task_desc_co2_check
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.LIGHT_CHECK,
      titleRes = R.string.maintenance_task_type_light_check,
      categoryRes = R.string.maintenance_category_equipment,
      iconRes = R.drawable.ic_care_light_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_16,
      defaultDescriptionRes = R.string.maintenance_task_desc_light_check
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.FEEDING,
      titleRes = R.string.maintenance_task_type_feeding,
      categoryRes = R.string.maintenance_category_livestock,
      iconRes = R.drawable.ic_care_feeding_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_17,
      defaultDescriptionRes = R.string.maintenance_task_desc_feeding
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.LIVESTOCK_CHECK,
      titleRes = R.string.maintenance_task_type_livestock_check,
      categoryRes = R.string.maintenance_category_livestock,
      iconRes = R.drawable.ic_care_livestock_24,
      accentColorRes = R.color.aqua_care_task_type_catalog_color_variant_18,
      defaultDescriptionRes = R.string.maintenance_task_desc_livestock_check
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.CUSTOM,
      titleRes = R.string.maintenance_task_type_custom,
      categoryRes = R.string.maintenance_category_other,
      iconRes = R.drawable.ic_care_custom_24,
      accentColorRes = R.color.aqua_content_secondary,
      defaultDescriptionRes = R.string.maintenance_task_desc_custom
    )
  )

  val categoryResIds: List<Int> = listOf(
    R.string.maintenance_category_water_care,
    R.string.maintenance_category_equipment,
    R.string.maintenance_category_cleaning,
    R.string.maintenance_category_plants,
    R.string.maintenance_category_livestock,
    R.string.maintenance_category_other
  )

  fun get(type: CareTaskType): CareTaskTypeDefinition =
    all.firstOrNull { item -> item.type == type } ?: all.last()

  fun byCategoryRes(@StringRes categoryRes: Int): List<CareTaskTypeDefinition> =
    all.filter { item -> item.categoryRes == categoryRes }
}
