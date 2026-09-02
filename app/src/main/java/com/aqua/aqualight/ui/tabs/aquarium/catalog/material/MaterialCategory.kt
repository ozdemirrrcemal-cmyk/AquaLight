package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import android.content.Context
import androidx.annotation.StringRes

data class MaterialCategory(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val shortCodeRes: Int
) {
    fun title(context: Context): String = context.getString(titleRes)

    fun shortCode(context: Context): String = context.getString(shortCodeRes)
}
