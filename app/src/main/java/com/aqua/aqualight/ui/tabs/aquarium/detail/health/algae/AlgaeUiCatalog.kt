package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTypeId

data class AlgaeUiDefinition(
    val id: AlgaeTypeId,
    @StringRes val nameRes: Int,
    @StringRes val shortDescriptionRes: Int,
    @DrawableRes val imageRes: Int
)

object AlgaeUiCatalog {

    val definitions: List<AlgaeUiDefinition> = listOf(
        definition(
            AlgaeTypeId.BROWN_DIATOM,
            R.string.algae_brown_diatom_name,
            R.string.algae_brown_diatom_short,
            R.drawable.algae_brown_diatom
        ),
        definition(
            AlgaeTypeId.GREEN_SPOT,
            R.string.algae_green_spot_name,
            R.string.algae_green_spot_short,
            R.drawable.algae_green_spot
        ),
        definition(
            AlgaeTypeId.BLACK_BEARD,
            R.string.algae_black_beard_name,
            R.string.algae_black_beard_short,
            R.drawable.algae_black_beard
        ),
        definition(
            AlgaeTypeId.HAIR_THREAD,
            R.string.algae_hair_thread_name,
            R.string.algae_hair_thread_short,
            R.drawable.algae_hair_thread
        ),
        definition(
            AlgaeTypeId.STAGHORN,
            R.string.algae_staghorn_name,
            R.string.algae_staghorn_short,
            R.drawable.algae_staghorn
        ),
        definition(
            AlgaeTypeId.GREEN_DUST,
            R.string.algae_green_dust_name,
            R.string.algae_green_dust_short,
            R.drawable.algae_green_dust
        ),
        definition(
            AlgaeTypeId.CYANOBACTERIA,
            R.string.algae_cyanobacteria_name,
            R.string.algae_cyanobacteria_short,
            R.drawable.algae_cyanobacteria
        ),
        definition(
            AlgaeTypeId.GREEN_WATER,
            R.string.algae_green_water_name,
            R.string.algae_green_water_short,
            R.drawable.algae_green_water
        ),
        definition(
            AlgaeTypeId.CLADOPHORA,
            R.string.algae_cladophora_name,
            R.string.algae_cladophora_short,
            R.drawable.algae_cladophora
        )
    )

    private val byId = definitions.associateBy(AlgaeUiDefinition::id)

    init {
        require(definitions.size == AlgaeTypeId.entries.size)
        require(byId.size == definitions.size)
        require(definitions.map(AlgaeUiDefinition::imageRes).distinct().size == definitions.size)
    }

    fun requireDefinition(id: AlgaeTypeId): AlgaeUiDefinition =
        requireNotNull(byId[id]) {
            "Missing algae UI definition for $id"
        }

    private fun definition(
        id: AlgaeTypeId,
        @StringRes nameRes: Int,
        @StringRes shortDescriptionRes: Int,
        @DrawableRes imageRes: Int
    ) = AlgaeUiDefinition(
        id = id,
        nameRes = nameRes,
        shortDescriptionRes = shortDescriptionRes,
        imageRes = imageRes
    )
}
