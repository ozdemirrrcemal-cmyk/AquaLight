package com.aqua.aqualight.ui.tabs.aquarium.careprofile

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.ui.tabs.aquarium.catalog.material.MaterialCategoryCatalog
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDimensionFormatter
import java.util.Locale
import kotlin.math.roundToInt

object CareProfileCalculator {

  data class Result(
    val percent: Int,
    val completedCount: Int,
    val totalCount: Int,
    val items: List<Item>
  )

  enum class ActionKey {
    TANK_NAME,
    TANK_TYPE,
    TANK_SIZE,
    SETUP_DATE,
    TANK_STYLE,
    PLANTS,
    LIVESTOCK
  }

  data class Item(
    val title: String,
    val subtitle: String,
    val completed: Boolean,
    val actionKey: ActionKey? = null,
    val materialCategoryKey: String? = null,
    val materialCategoryTitle: String? = null
  )

  fun calculate(
    context: Context,
    tank: AquariumTankSnapshot
  ): Result {
    val items = mutableListOf<Item>()

    items += Item(
      title = context.getString(R.string.aquarium_care_profile_tank_name),
      subtitle = tank.name.ifBlank {
        context.getString(R.string.aquarium_care_profile_missing_tank_name)
      },
      completed = tank.name.isNotBlank(),
      actionKey = ActionKey.TANK_NAME
    )

    items += Item(
      title = context.getString(R.string.aquarium_care_profile_tank_type),
      subtitle = tank.tankType.ifBlank {
        context.getString(R.string.aquarium_care_profile_missing_tank_type)
      },
      completed = tank.tankType.isNotBlank(),
      actionKey = ActionKey.TANK_TYPE
    )

    items += Item(
      title = context.getString(R.string.aquarium_care_profile_tank_size),
      subtitle = if (hasValidTankSize(tank)) {
        AquariumDimensionFormatter.labeledSizeText(
          context = context,
          widthCm = tank.widthCm,
          lengthCm = tank.lengthCm,
          heightCm = tank.heightCm,
          sizeUnit = tank.sizeUnit,
          formatRes = R.string.tank_pdf_size_localized_format
        )
      } else {
        context.getString(R.string.aquarium_care_profile_missing_tank_dimensions)
      },
      completed = hasValidTankSize(tank),
      actionKey = ActionKey.TANK_SIZE
    )

    items += Item(
      title = context.getString(R.string.aquarium_care_profile_setup_date),
      subtitle = if (tank.setupDateEpochDay != null) {
        context.getString(R.string.aquarium_care_profile_selected)
      } else {
        context.getString(R.string.aquarium_care_profile_missing_setup_date)
      },
      completed = tank.setupDateEpochDay != null,
      actionKey = ActionKey.SETUP_DATE
    )

    items += Item(
      title = context.getString(R.string.aquarium_care_profile_tank_style),
      subtitle = tank.tankStyle.ifBlank {
        context.getString(R.string.aquarium_care_profile_missing_tank_style)
      },
      completed = tank.tankStyle.isNotBlank(),
      actionKey = ActionKey.TANK_STYLE
    )

    items += Item(
      title = context.getString(R.string.aquarium_care_profile_plants),
      subtitle = if (tank.plants.isNotEmpty()) {
        context.resources.getQuantityString(
          R.plurals.aquarium_care_profile_plants_selected,
          tank.plants.size,
          tank.plants.size
        )
      } else {
        context.getString(R.string.aquarium_care_profile_missing_plants)
      },
      completed = tank.plants.isNotEmpty(),
      actionKey = ActionKey.PLANTS
    )

    items += Item(
      title = context.getString(R.string.aquarium_care_profile_livestock),
      subtitle = if (tank.livestock.isNotEmpty()) {
        context.resources.getQuantityString(
          R.plurals.aquarium_care_profile_livestock_selected,
          tank.livestock.size,
          tank.livestock.size
        )
      } else {
        context.getString(R.string.aquarium_care_profile_missing_livestock)
      },
      completed = tank.livestock.isNotEmpty(),
      actionKey = ActionKey.LIVESTOCK
    )

    items += createMaterialItem(
      context,
      context.getString(R.string.aquarium_care_profile_lighting),
      context.getString(R.string.aquarium_care_profile_missing_lighting),
      tank,
      arrayOf("light", "lighting", "led", "lamba", "aydınlatma")
    )
    items += createMaterialItem(
      context,
      context.getString(R.string.aquarium_care_profile_filter),
      context.getString(R.string.aquarium_care_profile_missing_filter),
      tank,
      arrayOf("filter", "filtre")
    )
    items += createMaterialItem(
      context,
      context.getString(R.string.aquarium_care_profile_substrate),
      context.getString(R.string.aquarium_care_profile_missing_substrate),
      tank,
      arrayOf("substrate", "soil", "aqua soil", "sand", "gravel", "kum", "toprak", "zemin")
    )
    items += createMaterialItem(
      context,
      context.getString(R.string.aquarium_care_profile_co2),
      context.getString(R.string.aquarium_care_profile_missing_co2),
      tank,
      arrayOf("co2", "co₂", "carbon dioxide")
    )
    items += createMaterialItem(
      context,
      context.getString(R.string.aquarium_care_profile_fertilizer),
      context.getString(R.string.aquarium_care_profile_missing_fertilizer),
      tank,
      arrayOf("fertilizer", "fertiliser", "fert", "gübre", "nutrition")
    )

    val completedCount = items.count(Item::completed)
    val totalCount = items.size
    val percent = if (totalCount == 0) {
      0
    } else {
      ((completedCount * 100f) / totalCount).roundToInt()
    }

    return Result(
      percent = percent,
      completedCount = completedCount,
      totalCount = totalCount,
      items = items
    )
  }

  private fun createMaterialItem(
    context: Context,
    title: String,
    missingSubtitle: String,
    tank: AquariumTankSnapshot,
    keywords: Array<String>
  ): Item {
    val completed = hasMaterial(tank, keywords)
    val category = findMaterialCategory(context, keywords)
    return Item(
      title = title,
      subtitle = if (completed) {
        getMaterialMatchSummary(context, tank, keywords)
      } else {
        missingSubtitle
      },
      completed = completed,
      materialCategoryKey = category?.first,
      materialCategoryTitle = category?.second
    )
  }

  private fun hasValidTankSize(tank: AquariumTankSnapshot): Boolean =
    tank.widthCm > 0 && tank.lengthCm > 0 && tank.heightCm > 0

  private fun hasMaterial(
    tank: AquariumTankSnapshot,
    keywords: Array<String>
  ): Boolean {
    return tank.materials.any { material ->
      containsAnyCareKeyword(
        value = "${material.categoryKey} ${material.name}",
        keywords = keywords
      )
    }
  }

  private fun getMaterialMatchSummary(
    context: Context,
    tank: AquariumTankSnapshot,
    keywords: Array<String>
  ): String {
    val matchedMaterials = tank.materials.filter { material ->
      containsAnyCareKeyword(
        value = "${material.categoryKey} ${material.name}",
        keywords = keywords
      )
    }

    return when (matchedMaterials.size) {
      0 -> context.getString(R.string.aquarium_care_profile_selected)
      1 -> matchedMaterials.first().name
      else -> context.getString(
        R.string.material_picker_more_count,
        matchedMaterials.first().name,
        matchedMaterials.size - 1
      )
    }
  }

  private fun findMaterialCategory(
    context: Context,
    keywords: Array<String>
  ): Pair<String, String>? {
    val categories = MaterialCategoryCatalog.bioCategories +
      MaterialCategoryCatalog.hardwareCategories
    val category = categories.firstOrNull { item ->
      containsAnyCareKeyword(
        value = "${item.key} ${item.title(context)}",
        keywords = keywords
      )
    }
    return category?.let { it.key to it.title(context) }
  }

  private fun containsAnyCareKeyword(
    value: String,
    keywords: Array<String>
  ): Boolean {
    val normalizedValue = normalizeCareText(value)
    return keywords.any { keyword ->
      normalizedValue.contains(normalizeCareText(keyword))
    }
  }

  private fun normalizeCareText(value: String): String {
    return value
      .lowercase(Locale.ROOT)
      .replace("₂", "2")
      .replace("ı", "i")
  }
}
