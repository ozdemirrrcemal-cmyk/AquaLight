package com.aqua.aqualight.data.care.catalog

import com.aqua.aqualight.R
import com.aqua.aqualight.data.care.model.CareTaskType

data class CareTaskTypeDefinition(
  val type: CareTaskType,
  val title: String,
  val category: String,
  val iconRes: Int,
  val accentColor: String,
  val defaultDescription: String
)

object CareTaskTypeCatalog {

  const val CATEGORY_WATER = "Water Care"
  const val CATEGORY_EQUIPMENT = "Equipment"
  const val CATEGORY_CLEANING = "Cleaning"
  const val CATEGORY_PLANTS = "Plants"
  const val CATEGORY_LIVESTOCK = "Livestock"
  const val CATEGORY_OTHER = "Other"

  val all: List<CareTaskTypeDefinition> = listOf(
    CareTaskTypeDefinition(
      type = CareTaskType.WATER_CHANGE,
      title = "Water Change",
      category = CATEGORY_WATER,
      iconRes = R.drawable.ic_care_water_change_24,
      accentColor = "#2196F3",
      defaultDescription = "Change part of the aquarium water."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.WATER_TEST,
      title = "Water Test",
      category = CATEGORY_WATER,
      iconRes = R.drawable.ic_care_water_test_24,
      accentColor = "#5C7CFA",
      defaultDescription = "Check water parameters."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.TEMPERATURE_CHECK,
      title = "Temperature Check",
      category = CATEGORY_WATER,
      iconRes = R.drawable.ic_care_temperature_24,
      accentColor = "#FF8A4C",
      defaultDescription = "Check aquarium temperature."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.SUBSTRATE_CLEANING,
      title = "Substrate Cleaning",
      category = CATEGORY_WATER,
      iconRes = R.drawable.ic_care_substrate_24,
      accentColor = "#B7793E",
      defaultDescription = "Clean substrate surface if needed."
    ),

    CareTaskTypeDefinition(
      type = CareTaskType.FILTER_MAINTENANCE,
      title = "Filter Maintenance",
      category = CATEGORY_EQUIPMENT,
      iconRes = R.drawable.ic_care_filter_24,
      accentColor = "#F2A900",
      defaultDescription = "Check filter flow and media condition."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.FILTER_CHANGE,
      title = "Filter Change",
      category = CATEGORY_EQUIPMENT,
      iconRes = R.drawable.ic_care_filter_change_24,
      accentColor = "#D99A00",
      defaultDescription = "Replace or maintain filter media carefully."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.PRE_FILTER_CLEANING,
      title = "Pre-filter Cleaning",
      category = CATEGORY_EQUIPMENT,
      iconRes = R.drawable.ic_care_prefilter_24,
      accentColor = "#8EA9A0",
      defaultDescription = "Clean pre-filter sponge or intake guard."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.PIPE_CLEANING,
      title = "Pipe Cleaning",
      category = CATEGORY_EQUIPMENT,
      iconRes = R.drawable.ic_care_pipe_24,
      accentColor = "#D8F3B0",
      defaultDescription = "Clean aquarium pipes."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.DIFFUSER_CLEANING,
      title = "Diffuser Cleaning",
      category = CATEGORY_EQUIPMENT,
      iconRes = R.drawable.ic_care_diffuser_24,
      accentColor = "#45D6B4",
      defaultDescription = "Clean CO2 diffuser."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.HOSE_CLEANING,
      title = "Hose Cleaning",
      category = CATEGORY_EQUIPMENT,
      iconRes = R.drawable.ic_care_hose_24,
      accentColor = "#7B2CBF",
      defaultDescription = "Clean aquarium hoses."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.DEVICE_CHECK,
      title = "Device Check",
      category = CATEGORY_EQUIPMENT,
      iconRes = R.drawable.ic_care_device_24,
      accentColor = "#4A90E2",
      defaultDescription = "Check connected aquarium devices."
    ),

    CareTaskTypeDefinition(
      type = CareTaskType.GLASS_CLEANING,
      title = "Glass Cleaning",
      category = CATEGORY_CLEANING,
      iconRes = R.drawable.ic_care_glass_24,
      accentColor = "#66C7F4",
      defaultDescription = "Clean aquarium glass."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.ALGAE_CLEANING,
      title = "Algae Cleaning",
      category = CATEGORY_CLEANING,
      iconRes = R.drawable.ic_care_algae_24,
      accentColor = "#4CAF50",
      defaultDescription = "Remove visible algae if needed."
    ),

    CareTaskTypeDefinition(
      type = CareTaskType.PLANT_TRIM,
      title = "Plant Trim",
      category = CATEGORY_PLANTS,
      iconRes = R.drawable.ic_care_trim_24,
      accentColor = "#4DD6A7",
      defaultDescription = "Trim aquarium plants."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.FERTILIZER_DOSING,
      title = "Fertilizer Dosing",
      category = CATEGORY_PLANTS,
      iconRes = R.drawable.ic_care_fertilizer_24,
      accentColor = "#8BC34A",
      defaultDescription = "Dose aquarium fertilizer."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.PLANT_HEALTH_CHECK,
      title = "Plant Health Check",
      category = CATEGORY_PLANTS,
      iconRes = R.drawable.ic_care_plant_health_24,
      accentColor = "#66BB6A",
      defaultDescription = "Check plant growth and leaf condition."
    ),

    CareTaskTypeDefinition(
      type = CareTaskType.CO2_CHECK,
      title = "CO2 Check",
      category = CATEGORY_EQUIPMENT,
      iconRes = R.drawable.ic_care_co2_24,
      accentColor = "#00BCD4",
      defaultDescription = "Check CO2 flow and diffuser bubbles."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.LIGHT_CHECK,
      title = "Light Check",
      category = CATEGORY_EQUIPMENT,
      iconRes = R.drawable.ic_care_light_24,
      accentColor = "#FFD54F",
      defaultDescription = "Check aquarium lighting schedule."
    ),

    CareTaskTypeDefinition(
      type = CareTaskType.FEEDING,
      title = "Feeding",
      category = CATEGORY_LIVESTOCK,
      iconRes = R.drawable.ic_care_feeding_24,
      accentColor = "#7C4DFF",
      defaultDescription = "Feed aquarium livestock."
    ),
    CareTaskTypeDefinition(
      type = CareTaskType.LIVESTOCK_CHECK,
      title = "Livestock Check",
      category = CATEGORY_LIVESTOCK,
      iconRes = R.drawable.ic_care_livestock_24,
      accentColor = "#FF6B6B",
      defaultDescription = "Observe livestock behavior and health."
    ),

    CareTaskTypeDefinition(
      type = CareTaskType.CUSTOM,
      title = "Custom Task",
      category = CATEGORY_OTHER,
      iconRes = R.drawable.ic_care_custom_24,
      accentColor = "#8FA4BE",
      defaultDescription = "Custom aquarium care task."
    )
  )

  val categories: List<String> = listOf(
    CATEGORY_WATER,
    CATEGORY_EQUIPMENT,
    CATEGORY_CLEANING,
    CATEGORY_PLANTS,
    CATEGORY_LIVESTOCK,
    CATEGORY_OTHER
  )

  fun get(
    type: CareTaskType
  ): CareTaskTypeDefinition {
    return all.firstOrNull { item ->
      item.type == type
    } ?: all.last()
  }

  fun byCategory(
    category: String
  ): List<CareTaskTypeDefinition> {
    return all.filter { item ->
      item.category == category
    }
  }
}