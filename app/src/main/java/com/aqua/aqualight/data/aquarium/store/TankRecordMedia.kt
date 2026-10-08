package com.aqua.aqualight.data.aquarium.store

internal fun StoredTank.recordPhotoUris(): List<String> =
    (listOf(photoUri) + plantsList.map { it.photoUri } + livestockList.map { it.photoUri })
        .filter(String::isNotBlank)
