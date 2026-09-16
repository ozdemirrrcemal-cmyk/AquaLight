package com.aqua.aqualight.application.aquarium

/**
 * Reviewed minimum light-demand class for a catalog plant.
 *
 * These values are categorical care requirements. They must not be presented as PPFD, DLI, or a
 * device channel percentage without a fixture-specific calibration model.
 */
enum class AquariumPlantLightDemand {
    LOW,
    MEDIUM,
    HIGH
}

/** One locale-independent plant-light record with an auditable, plant-specific source. */
data class AquariumPlantLightCatalogRecord(
    val catalogId: String,
    val lightDemand: AquariumPlantLightDemand,
    val sourceOrganization: String,
    val sourceRecordId: String,
    val sourceUrl: String,
    val catalogRevision: Int = AquariumPlantLightCatalog.CATALOG_REVISION
)

/**
 * Complete reviewed light-demand catalog for every selectable plant.
 *
 * There is deliberately no genus, category, display-name, or user-answer fallback. A missing exact
 * record is a catalog defect and fails closed during development/runtime resolution.
 */
object AquariumPlantLightCatalog {

    const val CATALOG_REVISION: Int = 1
    const val EXPECTED_RECORD_COUNT: Int = 203

    private val low = AquariumPlantLightDemand.LOW
    private val medium = AquariumPlantLightDemand.MEDIUM
    private val high = AquariumPlantLightDemand.HIGH

    private fun source(
        catalogKey: String,
        demand: AquariumPlantLightDemand,
        organization: String,
        sourceRecordId: String,
        sourceUrl: String
    ) = AquariumPlantLightCatalogRecord(
        catalogId = "plant:$catalogKey",
        lightDemand = demand,
        sourceOrganization = organization,
        sourceRecordId = sourceRecordId,
        sourceUrl = sourceUrl
    )

    private fun tropica(
        catalogKey: String,
        demand: AquariumPlantLightDemand,
        recordId: String
    ) = source(
        catalogKey = catalogKey,
        demand = demand,
        organization = "Tropica Aquarium Plants",
        sourceRecordId = recordId,
        sourceUrl = "https://tropica.com/en/plants/plantdetails/$recordId/$recordId"
    )

    private fun bucePlant(
        catalogKey: String,
        demand: AquariumPlantLightDemand,
        handle: String
    ) = source(
        catalogKey = catalogKey,
        demand = demand,
        organization = "Buce Plant",
        sourceRecordId = handle,
        sourceUrl = "https://buceplant.com/products/$handle"
    )

    private fun aquariumPlantsFactory(
        catalogKey: String,
        demand: AquariumPlantLightDemand,
        handle: String
    ) = source(
        catalogKey = catalogKey,
        demand = demand,
        organization = "Aquarium Plants Factory",
        sourceRecordId = handle,
        sourceUrl = "https://www.aquariumplantsfactory.com/products/$handle"
    )

    private fun glassAqua(
        catalogKey: String,
        demand: AquariumPlantLightDemand,
        handle: String
    ) = source(
        catalogKey = catalogKey,
        demand = demand,
        organization = "Glass Aqua",
        sourceRecordId = handle,
        sourceUrl = "https://shop.glassaqua.com/products/$handle"
    )

    val records: List<AquariumPlantLightCatalogRecord> = listOf(
        bucePlant("alternanthera_reineckii", medium, "alternanthera-reineckii"),
        tropica("alternanthera_reineckii_mini", medium, "4439"),
        aquariumPlantsFactory("alternanthera_reineckii_red_ruby", medium, "alternanthera-reineckii-red-ruby"),
        tropica("alternanthera_reineckii_rosanervig", medium, "4440"),
        source("amblystegium_serpens", low, "Dennerle Plants", "30305-22970", "https://dennerleplants.com/en/plants/plantdetails/Amblystegiumserpens(30305)/22970"),
        bucePlant("ammannia_gracilis", high, "ammania-gracilis"),
        aquariumPlantsFactory("ammannia_pedicellata_golden", high, "ammannia-pedicellata-golden"),
        aquariumPlantsFactory("ammannia_praetermissa", high, "ammannia-praetermissa"),
        bucePlant("ammannia_senegalensis", high, "ammania-senegalensis"),
        tropica("anubias_barteri", low, "4551"),
        tropica("anubias_barteri_var_caladiifolia", low, "30578"),
        tropica("anubias_barteri_var_coffeefolia", low, "4553"),
        tropica("anubias_barteri_var_nana", low, "30584"),
        source("anubias_barteri_var_nana_mini", low, "Aquasabi", "anubias-barteri-var-nana-mini", "https://www.aquasabi.de/Anubias-barteri-var-nana-Mini-Topf"),
        aquariumPlantsFactory("anubias_barteri_var_nana_pangolino", low, "anubias-pangolino"),
        tropica("anubias_barteri_var_nana_petite", low, "30576"),
        tropica("anubias_gracilis", low, "30574"),
        aquariumPlantsFactory("anubias_hastifolia", low, "anubias-hastifolia"),
        aquariumPlantsFactory("anubias_heterophylla", low, "anubias-heterophylla"),
        tropica("aponogeton_boivinianus", medium, "4534"),
        bucePlant("aponogeton_crispus", medium, "aponogeton-crispus"),
        tropica("aponogeton_madagascariensis", medium, "4535"),
        tropica("aponogeton_ulvaceus", medium, "4533"),
        bucePlant("azolla_caroliniana", medium, "azolla-caroliniana"),
        source("azolla_filiculoides", low, "Dennerle Plants", "351-27945", "https://dennerleplants.com/en/plants/plantdetails/Azollafiliculoides(351)/27945"),
        tropica("bacopa_australis", medium, "4466"),
        tropica("bacopa_caroliniana", low, "4465"),
        aquariumPlantsFactory("bacopa_compacta", low, "bacopa-compacta"),
        bucePlant("bacopa_monnieri", medium, "bacopa-monnieri"),
        bucePlant("barclaya_longifolia", low, "barclaya-longifolia"),
        aquariumPlantsFactory("barclaya_longifolia_red", medium, "barclaya-longifolia-red"),
        tropica("blyxa_japonica", medium, "22542"),
        bucePlant("bolbitis_heteroclita_difformis", low, "bolbitis-difformis"),
        tropica("bolbitis_heudelotii", medium, "30544"),
        bucePlant("bucephalandra_sp_biblis", low, "bucephalandra-biblis"),
        bucePlant("bucephalandra_sp_black_pearl", low, "bucephalandra-black-pearl"),
        bucePlant("bucephalandra_sp_brownie_blue", low, "bucephalandra-brownie-blue"),
        bucePlant("bucephalandra_sp_brownie_ghost", low, "brownie-ghost"),
        aquariumPlantsFactory("bucephalandra_sp_catarina", low, "bucephalandra-catarina"),
        bucePlant("bucephalandra_sp_dark_skeleton_king", medium, "skeleton-king"),
        bucePlant("bucephalandra_sp_godzilla", low, "godzilla"),
        bucePlant("bucephalandra_sp_kedagang", low, "bucephalandra-kedagang-uns-tissue-culture"),
        bucePlant("bucephalandra_sp_mini_coin", low, "mini-coin"),
        tropica("bucephalandra_sp_needle_leaf", low, "29517"),
        bucePlant("bucephalandra_sp_red_mini", low, "bucephalandra-red-mini-aquatic-farmer-tissue-culture"),
        source("bucephalandra_sp_silver_powder", low, "Mobids Plants", "bucephalandra-silver-powder", "https://mobidsplants.com.ua/en/bucephalandra/silver-powder"),
        bucePlant("bucephalandra_sp_theia_green", low, "bucephalandra-theia-green"),
        bucePlant("bucephalandra_sp_wavy_green", low, "bucephalandra-green-wavy-farmer-tissue-culture"),
        tropica("cabomba_aquatica", medium, "4431"),
        aquariumPlantsFactory("cabomba_caroliniana", medium, "cabomba-caroliniana"),
        source("ceratopteris_cornuta", low, "Dennerle Plants", "618-27833", "https://dennerleplants.com/en/plants/plantdetails/Ceratopteriscornuta(618)/27833"),
        tropica("ceratopteris_thalictroides", medium, "31137"),
        tropica("cladophora_aegagropila", low, "4385"),
        tropica("crinum_calamistratum", low, "4541"),
        source("crinum_natans", medium, "Agripet Garden", "crinum-natans", "https://www.agripetgarden.it/crinum-natans.html"),
        tropica("crinum_thaianum", low, "4537"),
        bucePlant("cryptocoryne_albida", medium, "cryptocoryne-albida-red"),
        bucePlant("cryptocoryne_balansae", low, "cryptocoryne-balansae"),
        bucePlant("cryptocoryne_beckettii", low, "cryptocoryne-becketii"),
        aquariumPlantsFactory("cryptocoryne_cordata_rosanervig", low, "cryptocoryne-cordata-rosanervig"),
        tropica("cryptocoryne_crispatula", low, "18756"),
        aquariumPlantsFactory("cryptocoryne_flamingo", medium, "cryptocoryne-flamingo"),
        source("cryptocoryne_lucens", low, "Dennerle Plants", "648-27840", "https://dennerleplants.com/en/plants/plantdetails/Cryptocorynelucens(648)/27840"),
        bucePlant("cryptocoryne_lutea", low, "cryptocoryne-lutea"),
        aquariumPlantsFactory("cryptocoryne_nurii", low, "cryptocoryne-nurii"),
        tropica("cryptocoryne_parva", medium, "18755"),
        bucePlant("cryptocoryne_pontederiifolia", low, "cryptocoryne-pontederiifolia"),
        bucePlant("cryptocoryne_spiralis", low, "cryptocoryne-spiralis-red"),
        bucePlant("cryptocoryne_undulata", low, "cryptocoryne-undulata"),
        bucePlant("cryptocoryne_wendtii_brown", low, "cryptocoryne-wendtii-brown"),
        tropica("cryptocoryne_wendtii_green", low, "19226"),
        tropica("cryptocoryne_wendtii_mi_oya", low, "19540"),
        tropica("cryptocoryne_wendtii_tropica", low, "4564"),
        tropica("cryptocoryne_willisii", low, "30580"),
        source("didiplis_diandra", high, "Dennerle Plants", "30159-22995", "https://dennerleplants.com/en/plants/plantdetails/Didiplisdiandra(30159)/22995"),
        tropica("echinodorus_bleheri", low, "4513"),
        aquariumPlantsFactory("echinodorus_grisebachii", low, "echinodorus-grisebachii"),
        tropica("echinodorus_ozelot", low, "4521"),
        tropica("echinodorus_red_diamond", medium, "4525"),
        tropica("echinodorus_reni", low, "18818"),
        tropica("egeria_densa", low, "4506"),
        aquariumPlantsFactory("eichhornia_diversifolia", high, "eichhornia-diversifolia"),
        tropica("eleocharis_acicularis", low, "28401"),
        bucePlant("eleocharis_acicularis_mini", medium, "eleocharis-parvula-mini-aquatic-farmer-tissue-culture"),
        tropica("eleocharis_parvula", low, "4572"),
        source("eleocharis_pusilla", low, "Dennerle Plants", "139-23007", "https://dennerleplants.com/en/plants/plantdetails/Eleocharispusilla(139)/23007"),
        aquariumPlantsFactory("elodea_canadensis", low, "elodea-canadensis"),
        tropica("eriocaulon_cinereum", high, "19547"),
        glassAqua("eriocaulon_setaceum", high, "eriocaulon-setaceum"),
        aquariumPlantsFactory("eriocaulon_sp_japan_needle_leaf", high, "eriocaulon-sp-japan-needle-leaf"),
        tropica("eriocaulon_sp_vietnam", medium, "29740"),
        tropica("fissidens_fontanus", medium, "4390"),
        bucePlant("fissidens_nobilis", low, "fissidens-nobilis"),
        tropica("glossostigma_elatinoides", high, "4470"),
        aquariumPlantsFactory("helanthium_tenellum", medium, "helanthium-tenellum"),
        tropica("helanthium_tenellum_green", low, "4757"),
        tropica("hemianthus_callitrichoides_cuba", high, "4478"),
        tropica("heteranthera_zosterifolia", low, "4544"),
        aquariumPlantsFactory("hydrocharis_morsus_ranae", medium, "hydrocharis-morsus-ranae"),
        source("hydrocotyle_leucocephala", low, "Dennerle Plants", "797-27875", "https://dennerleplants.com/en/plants/plantdetails/Hydrocotyleleucocephala(797)/27875"),
        tropica("hydrocotyle_tripartita_japan", medium, "18751"),
        tropica("hydrocotyle_verticillata", high, "4457"),
        tropica("hygrophila_corymbosa", low, "4490"),
        tropica("hygrophila_corymbosa_compact", low, "18774"),
        tropica("hygrophila_difformis", low, "4485"),
        tropica("hygrophila_pinnatifida", medium, "19228"),
        tropica("hygrophila_polysperma", low, "4483"),
        tropica("hygrophila_polysperma_rosanervig", low, "4484"),
        aquariumPlantsFactory("hygroryza_aristata", medium, "hygroryza-aristata"),
        bucePlant("lagenandra_keralensis", low, "lagenandra-keralensis-aquatic-farmer-tissue-culture"),
        bucePlant("lagenandra_meeboldii_green", low, "lagenandra-meeboldii-green-uns-tissue-culture"),
        tropica("lagenandra_meeboldii_red", low, "30579"),
        bucePlant("lagenandra_thwaitesii", medium, "lagenandra-thwaitesii-uns-tissue-culture"),
        glassAqua("lemna_minor", low, "duckweed-lemna-minor"),
        tropica("leptodictyum_riparium", low, "28671"),
        tropica("lilaeopsis_brasiliensis", low, "18808"),
        bucePlant("lilaeopsis_mauritiana", medium, "lilaeopsis-mauritiana-tissue-culture"),
        bucePlant("lilaeopsis_novae_zelandiae", medium, "micro-sword-lilaeopsis-novaezelandiae"),
        tropica("limnobium_laevigatum", low, "4761"),
        bucePlant("limnophila_aromatica", medium, "limnophila-aromatica"),
        tropica("limnophila_hippuridoides", medium, "4474"),
        tropica("limnophila_sessiliflora", low, "30571"),
        tropica("lobelia_cardinalis_mini", low, "28647"),
        source("lomariopsis_lineata", low, "Dennerle Plants", "30276-27959", "https://dennerleplants.com/en/plants/plantdetails/Lomariopsislineata(30276)/27959"),
        bucePlant("ludwigia_arcuata", medium, "ludwigia-arcuata"),
        tropica("ludwigia_glandulosa", medium, "4452"),
        bucePlant("ludwigia_inclinata", high, "ludwigia-inclinata"),
        aquariumPlantsFactory("ludwigia_inclinata_var_verticillata_cuba", high, "ludwigia-inclinata-cuba"),
        bucePlant("ludwigia_inclinata_var_verticillata_pantanal", high, "ludwigia-inclinata-var-verticillata-pantanal"),
        source("ludwigia_inclinata_var_verticillata_white", high, "Aquasabi", "ludwigia-inclinata-var-verticillata-white", "https://www.aquasabi.com/Ludwigia-inclinata-var-verticillata-White-in-Vitro"),
        tropica("ludwigia_palustris_super_red", low, "30570"),
        source("ludwigia_repens", low, "Dennerle Plants", "30178-23024", "https://dennerleplants.com/en/plants/plantdetails/Ludwigiarepens(30178)/23024"),
        aquariumPlantsFactory("ludwigia_sp_white", high, "ludwigia-sp-white"),
        bucePlant("marsilea_crenata", medium, "marsilea-crenata"),
        tropica("marsilea_hirsuta", low, "4428"),
        tropica("marsilea_minuta", medium, "4762"),
        aquariumPlantsFactory("marsilea_quadrifolia", low, "marsilea-quadrifolia"),
        tropica("mayaca_fluviatilis", medium, "19792"),
        tropica("micranthemum_tweediei_monte_carlo", medium, "4442"),
        tropica("microsorum_pteropus", low, "30567"),
        tropica("microsorum_pteropus_narrow", low, "31248"),
        bucePlant("microsorum_pteropus_needle_leaf", low, "java-fern-needle-leaf"),
        tropica("microsorum_pteropus_trident", low, "4425"),
        tropica("microsorum_pteropus_windelov", low, "31249"),
        tropica("monosolenium_tenerum", low, "18747"),
        bucePlant("myriophyllum_aquaticum", medium, "myriophyllum-aquaticum"),
        tropica("myriophyllum_mattogrossense", medium, "4454"),
        bucePlant("myriophyllum_tuberculatum", high, "myriophyllum-tuberculatum"),
        aquariumPlantsFactory("nymphaea_lotus_red", medium, "nymphaea-lotus-red"),
        aquariumPlantsFactory("nymphaea_micrantha", high, "nymphaea-micrantha"),
        bucePlant("nymphaea_stellata", medium, "nymphaea-stellata-1"),
        tropica("nymphoides_hydrophylla_taiwan", low, "4463"),
        tropica("phyllanthus_fluitans", low, "22848"),
        bucePlant("pistia_stratiotes", medium, "pistia-stratiotes-water-lettuce"),
        tropica("pogostemon_deccanensis", medium, "4497"),
        bucePlant("pogostemon_erectus", medium, "pogostemon-erectus-1"),
        tropica("pogostemon_helferi", medium, "19680"),
        aquariumPlantsFactory("pogostemon_kimberley", high, "pogostemon-kimberley"),
        tropica("pogostemon_stellatus", high, "4498"),
        bucePlant("pogostemon_stellatus_octopus", medium, "pogostemon-stellatus-octopus"),
        aquariumPlantsFactory("pogostemon_yatabeanus", medium, "pogostemon-yatabeanus"),
        bucePlant("proserpinaca_palustris", medium, "proserpinaca-palustris"),
        tropica("ranunculus_inundatus", medium, "18225"),
        tropica("riccardia_chamedryfolia", high, "30334"),
        source("riccardia_graeffei", low, "Tank Logbook", "mini-pellia", "https://tanklogbook.com/plant-detail/mini-pellia"),
        tropica("riccia_fluitans", medium, "4386"),
        tropica("rotala_indica_bonsai", medium, "4451"),
        tropica("rotala_macrandra", high, "4445"),
        bucePlant("rotala_macrandra_green", medium, "rotala-macrandra-green"),
        bucePlant("rotala_macrandra_mini_butterfly", high, "rotala-macrandra-mini-butterfly-uns-tissue-culture"),
        aquariumPlantsFactory("rotala_ramosior_florida", medium, "rotala-ramosior-florida"),
        tropica("rotala_rotundifolia", low, "18810"),
        tropica("rotala_rotundifolia_green", medium, "22545"),
        tropica("rotala_rotundifolia_h_ra", medium, "19550"),
        bucePlant("rotala_rotundifolia_orange_juice", medium, "rotala-rotundifolia-orange-juice"),
        bucePlant("rotala_sp_blood_red", high, "rotala-blood-red"),
        bucePlant("rotala_sp_pearl", high, "rotala-sp-pearl"),
        aquariumPlantsFactory("rotala_sp_vietnam_h_ra", medium, "rotala-sp-vietnam-h-ra"),
        tropica("rotala_wallichii", high, "18748"),
        tropica("sagittaria_subulata", low, "18270"),
        aquariumPlantsFactory("salvinia_auriculata", medium, "salvinia-auriculata"),
        tropica("salvinia_minima", low, "4767"),
        bucePlant("salvinia_natans", medium, "salvinia-natans"),
        glassAqua("spirodela_polyrhiza", low, "giant-duckweed-spirodela-polyrhiza"),
        tropica("staurogyne_repens", low, "19617"),
        aquariumPlantsFactory("syngonanthus_macrocaulon", high, "syngonanthus-macrocaulon"),
        aquariumPlantsFactory("syngonanthus_sp_belem", high, "syngonanthus-sp-belem"),
        tropica("taxiphyllum_barbieri", low, "4391"),
        tropica("taxiphyllum_sp_flame_moss", low, "4403"),
        bucePlant("taxiphyllum_sp_peacock_moss", low, "peacock-moss"),
        tropica("taxiphyllum_sp_spiky_moss", low, "4402"),
        bucePlant("tonina_fluviatilis", high, "tonina-fluviatilis"),
        glassAqua("tonina_sp_belem", high, "tonina-belem"),
        source("tonina_sp_manaus", high, "Aquamoos", "tonina-sp-manaus", "https://aquamoos.de/en/aquarium-plants/mid-ground/syngonanthus-sp-manaus-"),
        tropica("utricularia_graminifolia", medium, "4480"),
        bucePlant("vallisneria_americana", low, "vallisneria-americana"),
        tropica("vallisneria_gigantea", low, "4501"),
        bucePlant("vallisneria_nana", low, "vallisneria-nana"),
        source("vallisneria_spiralis", low, "Dennerle Plants", "753-27940", "https://dennerleplants.com/en/plants/plantdetails/Vallisneriaspiralis(753)/27940"),
        aquariumPlantsFactory("vesicularia_dubyana", low, "java-moss"),
        tropica("vesicularia_ferriei_weeping_moss", medium, "4398"),
        tropica("vesicularia_montagnei_christmas_moss", medium, "4395"),
        bucePlant("vesicularia_sp_mini_christmas_moss", low, "mini-christmas-moss")
    )

    private val byCatalogId: Map<String, AquariumPlantLightCatalogRecord> =
        records.associateBy(AquariumPlantLightCatalogRecord::catalogId)

    val catalogIds: Set<String>
        get() = byCatalogId.keys

    init {
        require(records.size == EXPECTED_RECORD_COUNT) {
            "Plant-light catalog must contain exactly $EXPECTED_RECORD_COUNT records"
        }
        require(byCatalogId.size == records.size) {
            "Plant-light catalog contains duplicate catalog IDs"
        }
        require(records.all { record -> record.catalogId.startsWith("plant:") })
        require(records.all { record -> record.sourceOrganization.isNotBlank() })
        require(records.all { record -> record.sourceRecordId.isNotBlank() })
        require(records.all { record -> record.sourceUrl.startsWith("https://") })
        require(records.all { record -> record.catalogRevision == CATALOG_REVISION })
    }

    fun record(catalogId: String): AquariumPlantLightCatalogRecord? = byCatalogId[catalogId]

    fun requireRecord(catalogId: String): AquariumPlantLightCatalogRecord =
        requireNotNull(byCatalogId[catalogId]) {
            "Missing reviewed plant-light record for $catalogId"
        }

    fun resolve(catalogId: String): AquariumPlantLightDemand =
        requireRecord(catalogId).lightDemand
}
