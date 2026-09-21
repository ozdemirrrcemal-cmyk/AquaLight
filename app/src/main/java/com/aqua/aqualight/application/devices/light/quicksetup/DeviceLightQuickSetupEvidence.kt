package com.aqua.aqualight.application.devices.light.quicksetup

/**
 * Reviewed policy evidence. IDs are persisted with recommendations; URLs are audit metadata.
 *
 * These sources inform biological product policy only. They are not AquaLight fixture calibration.
 */
object DeviceLightQuickSetupEvidence {
    const val ALGORITHM_REVISION = 1

    const val TROPICA_LIGHT_DEMAND = "tropica-light-demand-v1"
    const val GREEN_AQUA_PHOTOPERIOD = "green-aqua-photoperiod-v1"
    const val TWO_HR_PAR_BANDS = "2hraquarist-par-bands-v1"
    const val TWO_HR_CO2_PRECHARGE = "2hraquarist-co2-precharge-v1"

    val records: Map<String, DeviceLightQuickSetupEvidenceRecord> = listOf(
        DeviceLightQuickSetupEvidenceRecord(
            id = TROPICA_LIGHT_DEMAND,
            organization = "Tropica Aquarium Plants",
            url = "https://tropica.com/en/guide/make-your-aquarium-a-success/light/",
            policyUse = "Plant-specific light-demand context"
        ),
        DeviceLightQuickSetupEvidenceRecord(
            id = GREEN_AQUA_PHOTOPERIOD,
            organization = "Green Aqua",
            url = "https://greenaqua.hu/en/technika/vilagitas.html",
            policyUse = "New-tank and established-tank photoperiod progression"
        ),
        DeviceLightQuickSetupEvidenceRecord(
            id = TWO_HR_PAR_BANDS,
            organization = "The 2Hr Aquarist",
            url = "https://www.2hraquarist.com/blogs/light-3pillars/par_for_planted_tank",
            policyUse = "Conservative planted-aquarium PAR target bands"
        ),
        DeviceLightQuickSetupEvidenceRecord(
            id = TWO_HR_CO2_PRECHARGE,
            organization = "The 2Hr Aquarist",
            url = "https://www.2hraquarist.com/blogs/hot-topics/injecting-enough",
            policyUse = "CO2 precharge confirmation before light-on"
        )
    ).associateBy(DeviceLightQuickSetupEvidenceRecord::id)
}

data class DeviceLightQuickSetupEvidenceRecord(
    val id: String,
    val organization: String,
    val url: String,
    val policyUse: String
) {
    init {
        require(id.isNotBlank())
        require(organization.isNotBlank())
        require(url.startsWith("https://"))
        require(policyUse.isNotBlank())
    }
}
