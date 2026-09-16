package com.aqua.aqualight.application.aquarium

/**
 * One reviewed, locale-independent light-demand record for a catalog plant.
 *
 * [sourceRecordId] is the immutable Tropica detail identifier used to reconstruct the exact
 * manufacturer page included in an automation recommendation's audit trail.
 */
data class AquariumPlantLightCatalogRecord(
    val catalogId: String,
    val lightDemand: AquariumPlantLightDemand,
    val sourceRecordId: String,
    val catalogRevision: Int = 1
) {
    val evidenceSourceId: String
        get() = "tropica_plant_" + sourceRecordId

    val sourceUrl: String
        get() = "https://tropica.com/en/plants/plantdetails/" +
            sourceRecordId + "/" + sourceRecordId
}

data class AquariumPlantLightSelection(
    val effectiveDemand: AquariumPlantLightDemand,
    val reviewedDemandFloor: AquariumPlantLightDemand,
    val requiresUserInput: Boolean
)

/**
 * Source-backed plant requirements. No category, display name, growth form, or genus fallback is
 * allowed: an unmatched catalog entry remains UNKNOWN until an exact reviewed record is added.
 */
object AquariumPlantLightCatalog {

    private fun record(
        catalogId: String,
        demand: AquariumPlantLightDemand,
        sourceRecordId: String
    ) = AquariumPlantLightCatalogRecord(
        catalogId = catalogId,
        lightDemand = demand,
        sourceRecordId = sourceRecordId
    )

    val records: List<AquariumPlantLightCatalogRecord> = listOf(
        record("plant:alternanthera_reineckii_mini", AquariumPlantLightDemand.MEDIUM, "4439"),
        record("plant:alternanthera_reineckii_rosanervig", AquariumPlantLightDemand.MEDIUM, "4440"),
        record("plant:anubias_barteri", AquariumPlantLightDemand.LOW, "4551"),
        record("plant:anubias_barteri_var_caladiifolia", AquariumPlantLightDemand.LOW, "30578"),
        record("plant:anubias_barteri_var_coffeefolia", AquariumPlantLightDemand.LOW, "4553"),
        record("plant:anubias_barteri_var_nana", AquariumPlantLightDemand.LOW, "30584"),
        record("plant:anubias_barteri_var_nana_petite", AquariumPlantLightDemand.LOW, "30576"),
        record("plant:anubias_gracilis", AquariumPlantLightDemand.LOW, "30574"),
        record("plant:aponogeton_boivinianus", AquariumPlantLightDemand.MEDIUM, "4534"),
        record("plant:aponogeton_madagascariensis", AquariumPlantLightDemand.MEDIUM, "4535"),
        record("plant:aponogeton_ulvaceus", AquariumPlantLightDemand.MEDIUM, "4533"),
        record("plant:bacopa_australis", AquariumPlantLightDemand.MEDIUM, "4466"),
        record("plant:bacopa_caroliniana", AquariumPlantLightDemand.LOW, "4465"),
        record("plant:blyxa_japonica", AquariumPlantLightDemand.MEDIUM, "22542"),
        record("plant:bolbitis_heudelotii", AquariumPlantLightDemand.MEDIUM, "30544"),
        record("plant:bucephalandra_sp_needle_leaf", AquariumPlantLightDemand.LOW, "29517"),
        record("plant:cabomba_aquatica", AquariumPlantLightDemand.MEDIUM, "4431"),
        record("plant:ceratopteris_thalictroides", AquariumPlantLightDemand.MEDIUM, "31137"),
        record("plant:cladophora_aegagropila", AquariumPlantLightDemand.LOW, "4385"),
        record("plant:crinum_calamistratum", AquariumPlantLightDemand.LOW, "4541"),
        record("plant:crinum_thaianum", AquariumPlantLightDemand.LOW, "4537"),
        record("plant:cryptocoryne_crispatula", AquariumPlantLightDemand.LOW, "18756"),
        record("plant:cryptocoryne_parva", AquariumPlantLightDemand.MEDIUM, "18755"),
        record("plant:cryptocoryne_wendtii_green", AquariumPlantLightDemand.LOW, "19226"),
        record("plant:cryptocoryne_wendtii_mi_oya", AquariumPlantLightDemand.LOW, "19540"),
        record("plant:cryptocoryne_wendtii_tropica", AquariumPlantLightDemand.LOW, "4564"),
        record("plant:cryptocoryne_willisii", AquariumPlantLightDemand.LOW, "30580"),
        record("plant:echinodorus_bleheri", AquariumPlantLightDemand.LOW, "4513"),
        record("plant:echinodorus_ozelot", AquariumPlantLightDemand.LOW, "4521"),
        record("plant:echinodorus_red_diamond", AquariumPlantLightDemand.MEDIUM, "4525"),
        record("plant:echinodorus_reni", AquariumPlantLightDemand.LOW, "18818"),
        record("plant:egeria_densa", AquariumPlantLightDemand.LOW, "4506"),
        record("plant:eleocharis_acicularis", AquariumPlantLightDemand.LOW, "28401"),
        record("plant:eleocharis_parvula", AquariumPlantLightDemand.LOW, "4572"),
        record("plant:eriocaulon_cinereum", AquariumPlantLightDemand.HIGH, "19547"),
        record("plant:eriocaulon_sp_vietnam", AquariumPlantLightDemand.MEDIUM, "29740"),
        record("plant:fissidens_fontanus", AquariumPlantLightDemand.MEDIUM, "4390"),
        record("plant:glossostigma_elatinoides", AquariumPlantLightDemand.HIGH, "4470"),
        record("plant:helanthium_tenellum_green", AquariumPlantLightDemand.LOW, "4757"),
        record("plant:hemianthus_callitrichoides_cuba", AquariumPlantLightDemand.HIGH, "4478"),
        record("plant:heteranthera_zosterifolia", AquariumPlantLightDemand.LOW, "4544"),
        record("plant:hydrocotyle_tripartita_japan", AquariumPlantLightDemand.MEDIUM, "18751"),
        record("plant:hydrocotyle_verticillata", AquariumPlantLightDemand.HIGH, "4457"),
        record("plant:hygrophila_corymbosa_compact", AquariumPlantLightDemand.LOW, "18774"),
        record("plant:hygrophila_corymbosa", AquariumPlantLightDemand.LOW, "4490"),
        record("plant:hygrophila_difformis", AquariumPlantLightDemand.LOW, "4485"),
        record("plant:hygrophila_pinnatifida", AquariumPlantLightDemand.MEDIUM, "19228"),
        record("plant:hygrophila_polysperma", AquariumPlantLightDemand.LOW, "4483"),
        record("plant:hygrophila_polysperma_rosanervig", AquariumPlantLightDemand.LOW, "4484"),
        record("plant:lagenandra_meeboldii_red", AquariumPlantLightDemand.LOW, "30579"),
        record("plant:leptodictyum_riparium", AquariumPlantLightDemand.LOW, "28671"),
        record("plant:lilaeopsis_brasiliensis", AquariumPlantLightDemand.LOW, "18808"),
        record("plant:limnobium_laevigatum", AquariumPlantLightDemand.LOW, "4761"),
        record("plant:limnophila_hippuridoides", AquariumPlantLightDemand.MEDIUM, "4474"),
        record("plant:limnophila_sessiliflora", AquariumPlantLightDemand.LOW, "30571"),
        record("plant:ludwigia_glandulosa", AquariumPlantLightDemand.MEDIUM, "4452"),
        record("plant:ludwigia_palustris_super_red", AquariumPlantLightDemand.LOW, "30570"),
        record("plant:marsilea_hirsuta", AquariumPlantLightDemand.LOW, "4428"),
        record("plant:marsilea_minuta", AquariumPlantLightDemand.MEDIUM, "4762"),
        record("plant:mayaca_fluviatilis", AquariumPlantLightDemand.MEDIUM, "19792"),
        record("plant:micranthemum_tweediei_monte_carlo", AquariumPlantLightDemand.MEDIUM, "4442"),
        record("plant:microsorum_pteropus", AquariumPlantLightDemand.LOW, "30567"),
        record("plant:microsorum_pteropus_narrow", AquariumPlantLightDemand.LOW, "31248"),
        record("plant:microsorum_pteropus_trident", AquariumPlantLightDemand.LOW, "4425"),
        record("plant:microsorum_pteropus_windelov", AquariumPlantLightDemand.LOW, "31249"),
        record("plant:monosolenium_tenerum", AquariumPlantLightDemand.LOW, "18747"),
        record("plant:myriophyllum_mattogrossense", AquariumPlantLightDemand.MEDIUM, "4454"),
        record("plant:nymphoides_hydrophylla_taiwan", AquariumPlantLightDemand.LOW, "4463"),
        record("plant:phyllanthus_fluitans", AquariumPlantLightDemand.LOW, "22848"),
        record("plant:pogostemon_deccanensis", AquariumPlantLightDemand.MEDIUM, "4497"),
        record("plant:pogostemon_helferi", AquariumPlantLightDemand.MEDIUM, "19680"),
        record("plant:pogostemon_stellatus", AquariumPlantLightDemand.HIGH, "4498"),
        record("plant:ranunculus_inundatus", AquariumPlantLightDemand.MEDIUM, "18225"),
        record("plant:riccardia_chamedryfolia", AquariumPlantLightDemand.HIGH, "30334"),
        record("plant:riccia_fluitans", AquariumPlantLightDemand.MEDIUM, "4386"),
        record("plant:rotala_indica_bonsai", AquariumPlantLightDemand.MEDIUM, "4451"),
        record("plant:rotala_macrandra", AquariumPlantLightDemand.HIGH, "4445"),
        record("plant:rotala_rotundifolia_green", AquariumPlantLightDemand.MEDIUM, "22545"),
        record("plant:rotala_rotundifolia_h_ra", AquariumPlantLightDemand.MEDIUM, "19550"),
        record("plant:rotala_rotundifolia", AquariumPlantLightDemand.LOW, "18810"),
        record("plant:rotala_wallichii", AquariumPlantLightDemand.HIGH, "18748"),
        record("plant:sagittaria_subulata", AquariumPlantLightDemand.LOW, "18270"),
        record("plant:salvinia_minima", AquariumPlantLightDemand.LOW, "4767"),
        record("plant:staurogyne_repens", AquariumPlantLightDemand.LOW, "19617"),
        record("plant:taxiphyllum_barbieri", AquariumPlantLightDemand.LOW, "4391"),
        record("plant:taxiphyllum_sp_flame_moss", AquariumPlantLightDemand.LOW, "4403"),
        record("plant:taxiphyllum_sp_spiky_moss", AquariumPlantLightDemand.LOW, "4402"),
        record("plant:utricularia_graminifolia", AquariumPlantLightDemand.MEDIUM, "4480"),
        record("plant:vallisneria_gigantea", AquariumPlantLightDemand.LOW, "4501"),
        record("plant:vesicularia_ferriei_weeping_moss", AquariumPlantLightDemand.MEDIUM, "4398"),
        record("plant:vesicularia_montagnei_christmas_moss", AquariumPlantLightDemand.MEDIUM, "4395")
    )

    private val byCatalogId = records.associateBy(AquariumPlantLightCatalogRecord::catalogId)

    init {
        require(byCatalogId.size == records.size)
        require(records.all { record -> record.catalogId.startsWith("plant:") })
        require(records.all { record -> record.sourceRecordId.all(Char::isDigit) })
        require(records.none { record ->
            record.lightDemand == AquariumPlantLightDemand.UNKNOWN
        })
    }

    fun resolve(catalogId: String): AquariumPlantLightDemand =
        byCatalogId[catalogId]?.lightDemand ?: AquariumPlantLightDemand.UNKNOWN

    fun record(catalogId: String): AquariumPlantLightCatalogRecord? = byCatalogId[catalogId]

    /**
     * Resolves a mixed reviewed/unreviewed selection without allowing a user answer to lower a
     * demand already proven by an exact catalog record.
     */
    fun resolveSelection(
        catalogIds: Iterable<String>,
        unreviewedDemand: AquariumPlantLightDemand
    ): AquariumPlantLightSelection {
        val demands = catalogIds.map(::resolve)
        val reviewedFloor = demands
            .filterNot { demand -> demand == AquariumPlantLightDemand.UNKNOWN }
            .maxByOrNull(AquariumPlantLightDemand::rank)
            ?: AquariumPlantLightDemand.UNKNOWN
        val hasUnreviewed = AquariumPlantLightDemand.UNKNOWN in demands
        val requiresUserInput = hasUnreviewed &&
            reviewedFloor != AquariumPlantLightDemand.HIGH
        val effective = when {
            demands.isEmpty() -> AquariumPlantLightDemand.UNKNOWN
            reviewedFloor == AquariumPlantLightDemand.HIGH -> AquariumPlantLightDemand.HIGH
            !hasUnreviewed -> reviewedFloor
            unreviewedDemand == AquariumPlantLightDemand.UNKNOWN ->
                AquariumPlantLightDemand.UNKNOWN
            unreviewedDemand.rank() < reviewedFloor.rank() ->
                AquariumPlantLightDemand.UNKNOWN
            else -> unreviewedDemand
        }
        return AquariumPlantLightSelection(
            effectiveDemand = effective,
            reviewedDemandFloor = reviewedFloor,
            requiresUserInput = requiresUserInput
        )
    }

    fun evidenceSourceIds(catalogIds: Iterable<String>): Set<String> = catalogIds
        .mapNotNull { catalogId -> byCatalogId[catalogId]?.evidenceSourceId }
        .toSet()
}

private fun AquariumPlantLightDemand.rank(): Int = when (this) {
    AquariumPlantLightDemand.UNKNOWN -> 0
    AquariumPlantLightDemand.LOW -> 1
    AquariumPlantLightDemand.MEDIUM -> 2
    AquariumPlantLightDemand.HIGH -> 3
}
