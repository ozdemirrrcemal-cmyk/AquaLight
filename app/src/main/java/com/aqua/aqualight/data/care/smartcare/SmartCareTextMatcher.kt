package com.aqua.aqualight.data.care.smartcare

import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import java.util.Locale

internal object SmartCareTextMatcher {

  fun hasMaterialKeyword(
    materials: List<SavedAquariumMaterial>,
    keywords: Array<String>
  ): Boolean {
    return materials.any { material ->
      containsAnyKeyword(
        listOf(
          material.productId,
          material.categoryKey,
          material.categoryTitle,
          material.name,
          material.brand,
          material.note
        ).joinToString(" "),
        keywords
      )
    }
  }

  fun hasMaterialCategory(
    materials: List<SavedAquariumMaterial>,
    category: String
  ): Boolean = materials.any { material -> normalize(material.categoryKey) == category }

  fun hasLivestockKeyword(
    tank: SavedAquariumTank,
    keywords: Array<String>
  ): Boolean = tank.livestock.any { livestock ->
    containsAnyKeyword(livestock.toString(), keywords)
  }

  fun normalize(value: String): String {
    return value.lowercase(Locale.ROOT).replace("₂", "2").replace("ı", "i")
  }

  private fun containsAnyKeyword(
    value: String,
    keywords: Array<String>
  ): Boolean {
    val normalizedValue = normalize(value)
    return keywords.any { keyword -> normalizedValue.contains(normalize(keyword)) }
  }
}
