package com.aqua.aqualight.application.aquarium.health.algae

enum class AlgaeEvidenceId {
    TROPICA_TYPES_OF_ALGAE,
    TROPICA_PREVENTING_ALGAE,
    TROPICA_GROWING_IN,
    AQUARIUM_COOP_COMMON_ALGAE,
    TWO_HOUR_FRESHWATER_ALGAE_TYPES,
    TWO_HOUR_GREEN_DUST,
    TWO_HOUR_BLACK_BEARD,
    TWO_HOUR_CLADOPHORA,
    TWO_HOUR_GREEN_WATER
}

data class AlgaeEvidenceRecord(
    val id: AlgaeEvidenceId,
    val organization: String,
    val title: String,
    val sourceUrl: String
)

object AlgaeEvidenceCatalog {

    val records: List<AlgaeEvidenceRecord> = listOf(
        record(
            id = AlgaeEvidenceId.TROPICA_TYPES_OF_ALGAE,
            organization = "Tropica Aquarium Plants",
            title = "Types of algae",
            sourceUrl = "https://tropica.com/en/guide/algae-control/types-of-algae/"
        ),
        record(
            id = AlgaeEvidenceId.TROPICA_PREVENTING_ALGAE,
            organization = "Tropica Aquarium Plants",
            title = "Preventing algae",
            sourceUrl = "https://tropica.com/en/guide/algae-control/preventing-algae/"
        ),
        record(
            id = AlgaeEvidenceId.TROPICA_GROWING_IN,
            organization = "Tropica Aquarium Plants",
            title = "Growing-in",
            sourceUrl = "https://tropica.com/en/guide/get-the-right-start/growing-in/"
        ),
        record(
            id = AlgaeEvidenceId.AQUARIUM_COOP_COMMON_ALGAE,
            organization = "Aquarium Co-Op",
            title = "How to Fight the 6 Most Common Types of Algae in Your Fish Tank",
            sourceUrl = "https://www.aquariumcoop.com/blogs/aquarium/aquarium-algae"
        ),
        record(
            id = AlgaeEvidenceId.TWO_HOUR_FRESHWATER_ALGAE_TYPES,
            organization = "The 2Hr Aquarist",
            title = "Freshwater Aquarium Algae Types, Causes and Fixes",
            sourceUrl = "https://www.2hraquarist.com/blogs/beginners-planted-tank-101/" +
                "freshwater-aquarium-algae-types-causes"
        ),
        record(
            id = AlgaeEvidenceId.TWO_HOUR_GREEN_DUST,
            organization = "The 2Hr Aquarist",
            title = "How to get rid of green dust algae",
            sourceUrl = "https://www.2hraquarist.com/blogs/algae-control/" +
                "how-to-control-green-dust-algae"
        ),
        record(
            id = AlgaeEvidenceId.TWO_HOUR_BLACK_BEARD,
            organization = "The 2Hr Aquarist",
            title = "Black Beard Algae Removal",
            sourceUrl = "https://www.2hraquarist.com/blogs/algae-control/how-to-control-bba"
        ),
        record(
            id = AlgaeEvidenceId.TWO_HOUR_CLADOPHORA,
            organization = "The 2Hr Aquarist",
            title = "How to get rid of cladophora algae",
            sourceUrl = "https://www.2hraquarist.com/blogs/algae-control/" +
                "how-to-control-cladophora"
        ),
        record(
            id = AlgaeEvidenceId.TWO_HOUR_GREEN_WATER,
            organization = "The 2Hr Aquarist",
            title = "Aquarium Green Water",
            sourceUrl = "https://www.2hraquarist.com/blogs/algae-control/" +
                "control-green-water-algae"
        )
    )

    private val byId = records.associateBy(AlgaeEvidenceRecord::id)

    init {
        require(records.size == AlgaeEvidenceId.entries.size)
        require(byId.size == records.size)
        require(records.all { record -> record.sourceUrl.startsWith("https://") })
    }

    fun requireRecord(id: AlgaeEvidenceId): AlgaeEvidenceRecord =
        requireNotNull(byId[id]) {
            "Missing algae evidence record for $id"
        }

    private fun record(
        id: AlgaeEvidenceId,
        organization: String,
        title: String,
        sourceUrl: String
    ) = AlgaeEvidenceRecord(
        id = id,
        organization = organization,
        title = title,
        sourceUrl = sourceUrl
    )
}
