package com.aqua.aqualight.ui.tabs.aquarium.careprofile

import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryCatalog
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import java.util.Locale
import kotlin.math.roundToInt

object CareProfileCalculator {

  data class Result(
    val percent: Int,
    val completedCount: Int,
    val totalCount: Int,
    val items: List<Item>
  )

  data class Item(
    val title: String,
    val subtitle: String,
    val completed: Boolean
  )

  fun calculate(
    tank: SavedAquariumTank
  ): Result {
    val items = mutableListOf<Item>()

    items.add(
      Item(
        title = "Tank name",
        subtitle = if (tank.name.isNotBlank()) tank.name else "Missing tank name",
        completed = tank.name.isNotBlank()
      )
    )

    items.add(
      Item(
        title = "Tank type",
        subtitle = if (tank.tankType.isNotBlank()) tank.tankType else "Missing tank type",
        completed = tank.tankType.isNotBlank()
      )
    )

    items.add(
      Item(
        title = "Tank size",
        subtitle = if (hasValidTankSize(tank)) {
          "${tank.widthCm} W x ${tank.lengthCm} L x ${tank.heightCm} H"
        } else {
          "Missing tank dimensions"
        },
        completed = hasValidTankSize(tank)
      )
    )

    items.add(
      Item(
        title = "Setup date",
        subtitle = if (tank.setupDateMillis != null) "Selected" else "Missing setup date",
        completed = tank.setupDateMillis != null
      )
    )

    items.add(
      Item(
        title = "Tank style",
        subtitle = if (tank.tankStyle.isNotBlank()) tank.tankStyle else "Missing tank style",
        completed = tank.tankStyle.isNotBlank()
      )
    )

    items.add(
      Item(
        title = "Plants",
        subtitle = if (tank.plants.isNotEmpty()) {
          "${tank.plants.size} plants selected"
        } else {
          "Missing plant information"
        },
        completed = tank.plants.isNotEmpty()
      )
    )

    items.add(
      Item(
        title = "Livestock",
        subtitle = if (tank.livestock.isNotEmpty()) {
          "${tank.livestock.size} livestock selected"
        } else {
          "Missing fish / shrimp information"
        },
        completed = tank.livestock.isNotEmpty()
      )
    )

    items.add(
      createMaterialItem(
        title = "Lighting",
        missingSubtitle = "Missing lighting information",
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
        title = "Filter",
        missingSubtitle = "Missing filter information",
        tank = tank,
        keywords = arrayOf(
          "filter",
          "filtre"
        )
      )
    )

    items.add(
      createMaterialItem(
        title = "Substrate / soil",
        missingSubtitle = "Missing substrate or soil information",
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
        title = "CO₂",
        missingSubtitle = "Missing CO₂ information",
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
        title = "Fertilizer",
        missingSubtitle = "Missing fertilizer information",
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
    title: String,
    missingSubtitle: String,
    tank: SavedAquariumTank,
    keywords: Array<String>
  ): Item {
    val completed = hasMaterial(
      tank = tank,
      keywords = keywords
    )

    return Item(
      title = title,
      subtitle = if (completed) {
        getMaterialMatchSummary(
          tank = tank,
          keywords = keywords
        )
      } else {
        missingSubtitle
      },
      completed = completed
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
      return "Selected"
    }

    if (matchedMaterials.size == 1) {
      return matchedMaterials.first().name
    }

    return "${matchedMaterials.first().name} +${matchedMaterials.size - 1} more"
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