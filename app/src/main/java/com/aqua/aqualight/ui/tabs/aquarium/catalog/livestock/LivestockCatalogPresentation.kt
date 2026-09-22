package com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock

import android.content.Context
import androidx.core.os.ConfigurationCompat
import com.aqua.aqualight.application.aquarium.LivestockCatalogItem

fun LivestockCatalogItem.localizedName(
    context: Context
): String {
    val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0]
    return displayName(locale?.language.orEmpty())
}
