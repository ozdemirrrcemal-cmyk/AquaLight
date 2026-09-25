package com.aqua.aqualight.application.aquarium

enum class AquariumPlantLightDemand { LOW, MEDIUM, HIGH }

data class AquariumPlantLightCatalogRecord(
    val catalogId: String,
    val lightDemand: AquariumPlantLightDemand,
    val lightRequirement: String,
    val catalogRevision: Int = AquariumPlantLightCatalog.CATALOG_REVISION
)

/** Exact catalog identities. A range uses its lowest supported light as the minimum demand. */
object AquariumPlantLightCatalog {
    const val CATALOG_REVISION: Int = 2
    const val EXPECTED_RECORD_COUNT: Int = 271

    val records: List<AquariumPlantLightCatalogRecord> = buildList(EXPECTED_RECORD_COUNT) {
        addAll(catalogRecordsPart1)
        addAll(catalogRecordsPart2)
        addAll(catalogRecordsPart3)
    }

    private val byCatalogId = records.associateBy(AquariumPlantLightCatalogRecord::catalogId)
    val catalogIds: Set<String> get() = byCatalogId.keys

    init {
        check(records.size == EXPECTED_RECORD_COUNT)
        check(byCatalogId.size == records.size)
    }

    fun record(catalogId: String): AquariumPlantLightCatalogRecord? = byCatalogId[catalogId]
    fun requireRecord(catalogId: String): AquariumPlantLightCatalogRecord =
        requireNotNull(record(catalogId)) { "Missing plant-light record for $catalogId" }
    fun resolve(catalogId: String): AquariumPlantLightDemand = requireRecord(catalogId).lightDemand
}

private val catalogRecordsPart1: List<AquariumPlantLightCatalogRecord> = listOf(
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:acmella_repens",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:alternanthera_aquatica",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:alternanthera_reineckii_mini",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:alternanthera_reineckii_red_ruby",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:alternanthera_reineckii_rosanervig",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:amblystegium_serpens",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ammannia_capitellata",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ammannia_crassicaulis",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ammannia_gracilis",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ammannia_latifolia",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ammannia_pedicellata",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ammannia_praetermissa",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ammannia_senegalensis",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_nangi",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_afzelii",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_barteri",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_barteri_var_angustifolia",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_barteri_var_caladiifolia",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_barteri_var_glabra",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_barteri_var_nana",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_barteri_var_nana_kirin",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_barteri_var_nana_petite",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_barteri_var_nana_pinto",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_gilletii",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_gracilis",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:anubias_heterophylla",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:aponogeton_boivinianus",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:aponogeton_crispus",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:aponogeton_longiplumulosus",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:aponogeton_madagascariensis",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:aponogeton_natans",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:aponogeton_rigidifolius",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:aponogeton_ulvaceum",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:aponogeton_undulatus",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:aquarius_cordifolius",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:aquarius_grisebachii",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:aquarius_uruguayensis",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:azolla_filiculoides",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bacopa_australis",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bacopa_caroliniana",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bacopa_madagascariensis",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bacopa_monnieri",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bacopa_myriophylloides",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:barclaya_longifolia",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:blyxa_aubertii",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:blyxa_japonica",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bolbitis_heteroclita_difformis",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bolbitis_heudelotii",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_pygmaea_bukit_kelam",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sordidula_blue",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_brownie_athena",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_brownie_blue",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_brownie_ghost",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_brownie_jade",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_brownie_kapuas",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_brownie_phoenix",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_kedagang",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_lamandau_mini_red",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_melawi_blue",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_mini_red",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_needle_leaf",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_red_scorpio",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_red",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_serimbu_brown",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_skeleton_king",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:bucephalandra_sp_theia",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cabomba_aquatica",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cabomba_caroliniana",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cabomba_furcata",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cardamine_lyrata",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ceratophyllum_demersum",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ceratophyllum_submersum",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ceratopteris_cornuta",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ceratopteris_thalictroides",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:crinum_calamistratum",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:crinum_natans",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:crinum_thaianum",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_affinis",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_aponogetifolia",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_beckettii",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_ciliata",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_crispatula",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_crispatula_var_balansae",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_hudoroi",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_keei",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_moehlmannii",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_nurii",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_parva",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_pontederiifolia",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_retrospiralis",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_undulata",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_usteriana",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_walkeri",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_wendtii_brown",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_wendtii_flamingo",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_wendtii_green_gecko",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_wendtii_green",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_wendtii_mi_oya",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_wendtii_tropica",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:cryptocoryne_x_willisii",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    )
)

private val catalogRecordsPart2: List<AquariumPlantLightCatalogRecord> = listOf(
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:didiplis_diandra",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:drepanocladus_aduncus",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:echinodorus_indian_red",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:echinodorus_oriental",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:echinodorus_ozelot",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:echinodorus_red_diamond",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:echinodorus_red_flame",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:echinodorus_red_special",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:echinodorus_reni",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:echinodorus_ros",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:echinodorus_rubin",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:echinodorus_berteroi",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ectropothecium_barbieri",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:elatine_hydropiper",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:eleocharis_acicularis",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:eleocharis_parvula",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:eleocharis_pusilla",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:eleocharis_vivipara",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:elodea_canadensis",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:elodea_densa",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:elodea_nuttallii",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:eriocaulon_cinereum",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:eriocaulon_setaceum",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:eriocaulon_sp_vietnam",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:fissidens_fontanus",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:fissidens_nobilis",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:fontinalis_antipyretica",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:fontinalis_antipyretica_var_gigantea",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:glossostigma_elatinoides",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:gymnocoronis_spilanthoides",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:helanthium_bolivianum",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:helanthium_tenellum",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:helanthium_tenellum_green",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hemianthus_callitrichoides_cuba",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:heteranthera_dubia",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:heteranthera_zosterifolia",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hottonia_palustris",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hydrilla_verticillata",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hydrocharis_laevigata",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hydrocotyle_leucocephala",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hydrocotyle_sibthorpioides",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hydrocotyle_verticillata",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hydrocotyle_vulgaris",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hygrophila_balsamica",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hygrophila_corymbosa",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hygrophila_corymbosa_compact",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hygrophila_corymbosa_siamensis",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hygrophila_costata",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hygrophila_difformis",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hygrophila_lancea_araguaia",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hygrophila_odora",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hygrophila_pinnatifida",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hygrophila_polysperma",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hygrophila_polysperma_rosanervig",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:hygroryza_aristata",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:juncus_repens",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lagarosiphon_madagascariensis",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lagarosiphon_major",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lagenandra_meeboldii_red",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lemna_minor",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lemna_trisulca",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:leptochilus_pteropus",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:leptodictyum_riparium",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lilaeopsis_mauritiana",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:limnophila_aquatica",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:limnophila_aromatica",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:limnophila_hippuridoides",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:limnophila_indica",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:limnophila_rugosa",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:limnophila_sessiliflora",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lindernia_grandiflora",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lindernia_rotundifolia",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:littorella_uniflora",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lobelia_cardinalis",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lobelia_cardinalis_mini",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lomariopsis_lineata",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ludwigia_arcuata",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ludwigia_brevipes",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ludwigia_glandulosa",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ludwigia_helminthorrhiza",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ludwigia_inclinata_var_verticillata_cuba",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ludwigia_inclinata_var_verticillata_pantanal",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ludwigia_ovalis",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ludwigia_palustris_super_red",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ludwigia_repens",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ludwigia_repens_rubin",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ludwigia_senegalensis",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lysimachia_nummularia",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:lysimachia_nummularia_aurea",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:marsilea_angustifolia",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:marsilea_crenata",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:marsilea_hirsuta",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:mayaca_fluviatilis",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:micranthemum_glomeratum",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:micranthemum_tweediei_monte_carlo",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:micranthemum_umbrosum",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:microsorum_pteropus_narrow",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:microsorum_pteropus_needle_leaf",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:microsorum_pteropus_trident",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:microsorum_pteropus_windelov",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    )
)

private val catalogRecordsPart3: List<AquariumPlantLightCatalogRecord> = listOf(
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:monosolenium_tenerum",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:myriophyllum_mattogrossense",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:myriophyllum_pinnatum",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:myriophyllum_spicatum",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:myriophyllum_tuberculatum",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:najas_guadalupensis",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:najas_indica",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:najas_marina",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:nuphar_japonica",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:nymphaea_lotus",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:nymphaea_lotus_red",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:nymphaea_pubescens",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:nymphoides_aquatica",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:nymphoides_hydrophylla_taiwan",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:nymphoides_indica",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ottelia_alismoides",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ottelia_ulvifolia",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:penthorum_sedoides",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:phyllanthus_fluitans",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:pistia_stratiotes",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:pogostemon_deccanensis",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:pogostemon_helferi",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:pogostemon_stellatus",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:potamogeton_gayi",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:potamogeton_perfoliatus",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:potamogeton_wrightii",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:proserpinaca_palustris",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ranunculus_inundatus",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:riccardia_chamedryfolia",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:riccia_fluitans",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:ricciocarpos_natans",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rorippa_aquatica",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_hippuris",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_indica",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_indica_bonsai",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_macrandra",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_macrandra_green",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_ramosior",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_rotundifolia",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_rotundifolia_ceylon",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_rotundifolia_colorata",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_rotundifolia_green",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_rotundifolia_h_ra",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_rotundifolia_orange_juice",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_sp_nanjenshan",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:rotala_wallichii",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:sagittaria_platyphylla",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:sagittaria_subulata",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:salvinia_cucullata",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:salvinia_minima",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:salvinia_molesta",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:salvinia_oblongifolia",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:schismatoglottis_prietoi",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:schismatoglottis_roseospatha",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:solenostoma_tetragonum_pearl_moss",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:spirodela_polyrhiza",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:staurogyne_repens",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:stratiotes_aloides",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:taxiphyllum_alternans_taiwan_moss",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:taxiphyllum_sp_flame_moss",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:taxiphyllum_sp_peacock_moss",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:taxiphyllum_sp_spiky_moss",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:utricularia_graminifolia",
        lightDemand = AquariumPlantLightDemand.HIGH,
        lightRequirement = "HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:vallisneria_nana",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:vallisneria_spiralis",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:vesicularia_dubyana",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:vesicularia_ferriei_weeping_moss",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:vesicularia_montagnei_christmas_moss",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:vesicularia_montagnei_mini_christmas_moss",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:vesicularia_reticulata_erect_moss",
        lightDemand = AquariumPlantLightDemand.MEDIUM,
        lightRequirement = "MEDIUM"
    ),
    AquariumPlantLightCatalogRecord(
        catalogId = "plant:wolffia_arrhiza",
        lightDemand = AquariumPlantLightDemand.LOW,
        lightRequirement = "LOW_HIGH"
    )
)
