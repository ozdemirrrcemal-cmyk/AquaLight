package com.aqua.aqualight.data.aquarium

import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank

internal fun SavedAquariumTank.hasIndependentLivestockPhotos(duplicate: SavedAquariumTank): Boolean {
    val copied = duplicate.livestock.associateBy { it.id }
    return livestock.all { item ->
        val photo = item.photoUri
        val replacement = copied[item.id]?.photoUri
        photo.isNullOrBlank() || (!replacement.isNullOrBlank() && replacement != photo)
    }
}
