package com.aqua.aqualight.application.devices.light.quicksetup

/**
 * Internal policy categories persisted with recommendations; calibration is separate.
 */
object DeviceLightQuickSetupEvidence {
    const val ALGORITHM_REVISION = 1

    const val PLANT_LIGHT_REQUIREMENT = "plant-light-requirement-v2"
    const val PHOTOPERIOD_POLICY = "photoperiod-policy-v1"
    const val LIGHT_PAR_TARGET = "light-par-target-v1"
    const val CO2_PRECHARGE_POLICY = "co2-precharge-policy-v1"

    val records: Map<String, DeviceLightQuickSetupEvidenceRecord> = listOf(
        DeviceLightQuickSetupEvidenceRecord(
            id = PLANT_LIGHT_REQUIREMENT,
            policyUse = "Plant-specific light-demand context"
        ),
        DeviceLightQuickSetupEvidenceRecord(
            id = PHOTOPERIOD_POLICY,
            policyUse = "New-tank and established-tank photoperiod progression"
        ),
        DeviceLightQuickSetupEvidenceRecord(
            id = LIGHT_PAR_TARGET,
            policyUse = "Conservative planted-aquarium PAR target bands"
        ),
        DeviceLightQuickSetupEvidenceRecord(
            id = CO2_PRECHARGE_POLICY,
            policyUse = "CO2 precharge confirmation before light-on"
        )
    ).associateBy(DeviceLightQuickSetupEvidenceRecord::id)
}

data class DeviceLightQuickSetupEvidenceRecord(
    val id: String,
    val policyUse: String
) {
    init {
        require(id.isNotBlank())
        require(policyUse.isNotBlank())
    }
}
