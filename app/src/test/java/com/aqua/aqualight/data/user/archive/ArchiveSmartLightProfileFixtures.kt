package com.aqua.aqualight.data.user.archive

internal fun emptyArchiveSmartLightProfile() = ArchiveSmartLightProfile(
    plantDensity = "",
    highestPlantLightDemand = "",
    co2Status = "",
    isActiveSoil = null,
    waterDepthCm = null,
    fixtureMountHeightCm = null,
    preferredViewingStartMinuteOfDay = null,
    preferredViewingEndMinuteOfDay = null,
    algaeObservation = "",
    plantStressObservation = "",
    observationDateEpochDay = null
)
