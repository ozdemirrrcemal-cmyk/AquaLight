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
    TWO_HOUR_GREEN_WATER,
    TWO_HOUR_FILAMENTOUS_ALGAE,
    AQUASABI_ALGAE_OVERVIEW,
    AQUASABI_GREEN_COATS,
    AQUASABI_FUZZ_ALGAE,
    AQUASABI_THREAD_ALGAE,
    AQUASABI_HAIR_ALGAE
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
            title = "Green Dust Algae",
            sourceUrl = "https://www.2hraquarist.com/blogs/algae-control/" +
                "green-dust-algae-gda-a-focused-study"
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
        ),
        record(
            id = AlgaeEvidenceId.TWO_HOUR_FILAMENTOUS_ALGAE,
            organization = "The 2Hr Aquarist",
            title = "Green Hair, Fuzz, String and Thread Algae",
            sourceUrl = "https://www.2hraquarist.com/blogs/algae-control/" +
                "hair-algae-planted-aquarium-causes-fixes"
        ),
        record(
            id = AlgaeEvidenceId.AQUASABI_ALGAE_OVERVIEW,
            organization = "Aquasabi",
            title = "Algae in the aquarium",
            sourceUrl = "https://www.aquasabi.com/aquascaping-wiki_algae_algae-in-the-aquarium"
        ),
        record(
            id = AlgaeEvidenceId.AQUASABI_GREEN_COATS,
            organization = "Aquasabi",
            title = "Green algae coats",
            sourceUrl = "https://www.aquasabi.com/aquascaping-wiki_algae_green-algae-coats"
        ),
        record(
            id = AlgaeEvidenceId.AQUASABI_FUZZ_ALGAE,
            organization = "Aquasabi",
            title = "Fuzz algae",
            sourceUrl = "https://www.aquasabi.com/aquascaping-wiki_algae_fuzz-algae"
        ),
        record(
            id = AlgaeEvidenceId.AQUASABI_THREAD_ALGAE,
            organization = "Aquasabi",
            title = "Green thread algae",
            sourceUrl = "https://www.aquasabi.com/aquascaping-wiki_algae_green-thread-algae"
        ),
        record(
            id = AlgaeEvidenceId.AQUASABI_HAIR_ALGAE,
            organization = "Aquasabi",
            title = "Hair algae",
            sourceUrl = "https://www.aquasabi.com/aquascaping-wiki_algae_hair-algae"
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
