package com.aqua.aqualight.ui.tabs.aquarium.careprofile

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.catalog.material.MaterialCategoryCatalog
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
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
    tank: SavedAquariumTank
  ): Result {
    val items = mutableListOf<Item>()

    items.add(
      Item(
        title = context.getString(R.string.aquarium_care_profile_tank_name),
        subtitle = if (tank.name.isNotBlank()) tank.name else context.getString(R.string.aquarium_care_profile_missing_tank_name),
        completed = tank.name.isNotBlank(),
        actionKey = ActionKey.TANK_NAME
      )
    )

    items.add(
      Item(
        title = context.getString(R.string.aquarium_care_profile_tank_type),
        subtitle = if (tank.tankType.isNotBlank()) tank.tankType else context.getString(R.string.aquarium_care_profile_missing_tank_type),
        completed = tank.tankType.isNotBlank(),
        actionKey = ActionKey.TANK_TYPE
      )
    )

    items.add(
      Item(
        title = context.getString(R.string.aquarium_care_profile_tank_size),
        subtitle = if (hasValidTankSize(tank)) {
          context.getString(
            R.string.tank_pdf_size_format,
            tank.widthCm,
            tank.lengthCm,
            tank.heightCm
          )
        } else {
          context.getString(R.string.aquarium_care_profile_missing_tank_dimensions)
        },
        completed = hasValidTankSize(tank),
        actionKey = ActionKey.TANK_SIZE
      )
    )

    items.add(
      Item(
        title = context.getString(R.string.aquarium_care_profile_setup_date),
        subtitle = if (tank.setupDateMillis != null) context.getString(R.string.aquarium_care_profile_selected) else context.getString(R.string.aquarium_care_profile_missing_setup_date),
        completed = tank.setupDateMillis != null,
        actionKey = ActionKey.SETUP_DATE
      )
    )

    items.add(
      Item(
        title = context.getString(R.string.aquarium_care_profile_tank_style),
        subtitle = if (tank.tankStyle.isNotBlank()) tank.tankStyle else context.getString(R.string.aquarium_care_profile_missing_tank_style),
        completed = tank.tankStyle.isNotBlank(),
        actionKey = ActionKey.TANK_STYLE
      )
    )

    items.add(
      Item(
        title = context.getString(R.string.aquarium_care_profile_plants),
        subtitle = if (tank.plants.isNotEmpty()) {
          context.resources.getQuantityString(R.plurals.aquarium_care_profile_plants_selected, tank.plants.size, tank.plants.size)
        } else {
          context.getString(R.string.aquarium_care_profile_missing_plants)
        },
        completed = tank.plants.isNotEmpty(),
        actionKey = ActionKey.PLANTS
      )
    )

    items.add(
      Item(
        title = context.getString(R.string.aquarium_care_profile_livestock),
        subtitle = if (tank.livestock.isNotEmpty()) {
          context.resources.getQuantityString(R.plurals.aquarium_care_profile_livestock_selected, tank.livestock.size, tank.livestock.size)
        } else {
          context.getString(R.string.aquarium_care_profile_missing_livestock)
        },
        completed = tank.livestock.isNotEmpty(),
        actionKey = ActionKey.LIVESTOCK
      )
    )

    items.add(
      createMaterialItem(
        context = context,
        title = context.getString(R.string.aquarium_care_profile_lighting),
        missingSubtitle = context.getString(R.string.aquarium_care_profile_missing_lighting),
        tank = tank,
        keywords = arrayOf(
          "light",
          "lighting",
          "led",
          "lamba",
          "aydınlatma"
        )
      )
    )

    items.add(
      createMaterialItem(
        context = context,
        title = context.getString(R.string.aquarium_care_profile_filter),
        missingSubtitle = context.getString(R.string.aquarium_care_profile_missing_filter),
        tank = tank,
        keywords = arrayOf(
          "filter",
          "filtre"
        )
      )
    )

    items.add(
      createMaterialItem(
        context = context,
        title = context.getString(R.string.aquarium_care_profile_substrate),
        missingSubtitle = context.getString(R.string.aquarium_care_profile_missing_substrate),
        tank = tank,
        keywords = arrayOf(
          "substrate",
          "soil",
          "aqua soil",
          "sand",
          "gravel",
          "kum",
          "toprak",
          "zemin"
        )
      )
    )

    items.add(
      createMaterialItem(
        context = context,
        title = context.getString(R.string.aquarium_care_profile_co2),
        missingSubtitle = context.getString(R.string.aquarium_care_profile_missing_co2),
        tank = tank,
        keywords = arrayOf(
          "co2",
          "co₂",
          "carbon dioxide"
        )
      )
    )

    items.add(
      createMaterialItem(
        context = context,
        title = context.getString(R.string.aquarium_care_profile_fertilizer),
        missingSubtitle = context.getString(R.string.aquarium_care_profile_missing_fertilizer),
        tank = tank,
        keywords = arrayOf(
          "fertilizer",
          "fertiliser",
          "fert",
          "gübre",
          "nutrition"
        )
      )
    )

    val completedCount = items.count { item ->
      item.completed
    }

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
    tank: SavedAquariumTank,
    keywords: Array<String>
  ): Item {
    val completed = hasMaterial(
      tank = tank,
      keywords = keywords
    )

    val category = findMaterialCategory(
      keywords = keywords
    )

    return Item(
      title = title,
      subtitle = if (completed) {
        getMaterialMatchSummary(
          context = context,
          tank = tank,
          keywords = keywords
        )
      } else {
        missingSubtitle
      },
      completed = completed,
      materialCategoryKey = category?.first,
      materialCategoryTitle = category?.second
    )
  }

  private fun hasValidTankSize(
    tank: SavedAquariumTank
  ): Boolean {
    return tank.widthCm > 0 &&
      tank.lengthCm > 0 &&
      tank.heightCm > 0
  }

  private fun hasMaterial(
    tank: SavedAquariumTank,
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
    tank: SavedAquariumTank,
    keywords: Array<String>
  ): String {
    val matchedMaterials = tank.materials.filter { material ->
      containsAnyCareKeyword(
        value = "${material.categoryKey} ${material.name}",
        keywords = keywords
      )
    }

    if (matchedMaterials.isEmpty()) {
      return context.getString(R.string.aquarium_care_profile_selected)
    }

    if (matchedMaterials.size == 1) {
      return matchedMaterials.first().name
    }

    return context.getString(R.string.material_picker_more_count, matchedMaterials.first().name, matchedMaterials.size - 1)
  }

  private fun findMaterialCategory(
    keywords: Array<String>
  ): Pair<String, String>? {
    val categories = MaterialCategoryCatalog.bioCategories +
      MaterialCategoryCatalog.hardwareCategories

    val category = categories.firstOrNull { category ->
      containsAnyCareKeyword(
        value = "${category.key} ${category.title}",
        keywords = keywords
      )
    }

    return category?.let {
      it.key to it.title
    }
  }

  private fun containsAnyCareKeyword(
    value: String,
    keywords: Array<String>
  ): Boolean {
    val normalizedValue = normalizeCareText(value)

    return keywords.any { keyword ->
      normalizedValue.contains(
        normalizeCareText(keyword)
      )
    }
  }

  private fun normalizeCareText(
    value: String
  ): String {
    return value
      .lowercase(Locale.ROOT)
      .replace("₂", "2")
      .replace("ı", "i")
  }
}