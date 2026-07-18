package com.aqua.aqualight.data.aquarium.catalog.material

import androidx.annotation.StringRes

data class MaterialCategory(
    val key: String,
    @StringRes val titleRes: Int,
    val shortCode: String
)
