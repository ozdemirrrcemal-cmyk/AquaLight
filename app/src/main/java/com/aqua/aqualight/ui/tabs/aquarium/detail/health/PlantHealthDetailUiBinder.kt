package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.widget.ImageView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantCare
import com.aqua.aqualight.application.aquarium.AquariumPlantCatalogRecord
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.databinding.FragmentPlantHealthDetailBinding

internal fun FragmentPlantHealthDetailBinding.renderPlantHealthCatalog(
    fragment: Fragment,
    plant: AquariumPlantTag
) {
    val context = fragment.requireContext()
    val catalog = PlantHealthCatalogUi.record(context, plant.catalogId)
    val care = catalog?.care
    renderPlantIdentity(plant)
    bindPlantPhoto(imgPlantHero, plant.photoUri)
    renderCatalogMetrics(fragment, care)
    renderCatalogOverview(fragment, plant, catalog)
    renderCatalogCare(fragment, care)
}

private fun FragmentPlantHealthDetailBinding.renderPlantIdentity(
    plant: AquariumPlantTag
) {
    tvPlantName.text = plant.plantName
    tvPlantCategory.text = plant.category
}

private fun FragmentPlantHealthDetailBinding.renderCatalogMetrics(
    fragment: Fragment,
    care: AquariumPlantCare?
) {
    val context = fragment.requireContext()
    tvMetricGrowthValue.text = PlantHealthCatalogUi.growth(context, care)
    tvMetricDifficultyValue.text = PlantHealthCatalogUi.difficulty(context, care)
    tvMetricLightValue.text = PlantHealthCatalogUi.light(context, care)
    tvMetricCo2Value.text = PlantHealthCatalogUi.co2(context, care)
}

private fun FragmentPlantHealthDetailBinding.renderCatalogOverview(
    fragment: Fragment,
    plant: AquariumPlantTag,
    catalog: AquariumPlantCatalogRecord?
) {
    val context = fragment.requireContext()
    val care = catalog?.care
    tvSpecies.text = row(fragment, R.string.plant_health_info_species, catalog?.displayName ?: plant.plantName)
    tvPlacement.text = row(
        fragment,
        R.string.plant_health_info_placement,
        PlantHealthCatalogUi.placement(context, catalog).ifBlank { plant.category }
    )
    tvTemperature.text = row(
        fragment,
        R.string.plant_health_info_temperature,
        PlantHealthCatalogUi.range(context, care, PlantHealthCatalogRange.TEMPERATURE)
    )
    tvPh.text = row(
        fragment,
        R.string.plant_health_info_ph,
        PlantHealthCatalogUi.range(context, care, PlantHealthCatalogRange.PH)
    )
    tvKh.text = row(
        fragment,
        R.string.plant_health_info_kh,
        PlantHealthCatalogUi.range(context, care, PlantHealthCatalogRange.KH)
    )
    tvGh.text = row(
        fragment,
        R.string.plant_health_info_gh,
        PlantHealthCatalogUi.range(context, care, PlantHealthCatalogRange.GH)
    )
    tvNutrients.text = row(
        fragment,
        R.string.plant_health_info_nutrients,
        PlantHealthCatalogUi.nutrientDemand(context, care)
    )
}

private fun FragmentPlantHealthDetailBinding.renderCatalogCare(
    fragment: Fragment,
    care: AquariumPlantCare?
) {
    val context = fragment.requireContext()
    tvCareLight.text = row(
        fragment,
        R.string.plant_health_metric_light,
        PlantHealthCatalogUi.light(context, care)
    )
    tvCareCo2.text = row(
        fragment,
        R.string.plant_health_metric_co2,
        PlantHealthCatalogUi.co2(context, care)
    )
    tvCareNutrients.text = row(
        fragment,
        R.string.plant_health_info_nutrients,
        PlantHealthCatalogUi.nutrientDemand(context, care)
    )
    tvCareSubstrate.text = row(
        fragment,
        R.string.plant_health_info_substrate,
        PlantHealthCatalogUi.substrate(context, care)
    )
}

private fun row(fragment: Fragment, labelRes: Int, value: String): String =
    fragment.getString(labelRes) + "   " + value

private fun bindPlantPhoto(imageView: ImageView, photoUri: String?) {
    if (photoUri.isNullOrBlank()) {
        imageView.scaleType = ImageView.ScaleType.CENTER
        imageView.setImageResource(R.drawable.ic_health_plant_24)
        return
    }
    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
    imageView.load(photoUri.toUri()) {
        crossfade(true)
        error(R.drawable.ic_health_plant_24)
    }
}
