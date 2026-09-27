package com.aqua.aqualight.application.aquarium.health

import java.net.URI
import java.time.LocalDate

/** A revision identifies one immutable manufacturer/method definition, not a localized label. */
data class WaterMethodProfileKey(val productId: String, val revision: Int) {
    init {
        require(WaterMethodProfileRules.isStableId(productId)) { "Invalid water product id." }
        require(revision > 0) { "Water product revision must be positive." }
    }
}

data class WaterMethodProduct(val brand: String, val model: String, val variant: String) {
    init {
        require(listOf(brand, model, variant).all(WaterMethodProfileRules::isCanonicalText)) {
            "An exact brand, model and variant are required."
        }
    }
}

enum class WaterSampleMatrix { FRESHWATER, BRACKISH, MARINE }

enum class WaterMethodEvidenceKind { MANUFACTURER_MANUAL, MANUFACTURER_PRODUCT_PAGE, PRIMARY_METHOD }

data class WaterMethodEvidence(
    val kind: WaterMethodEvidenceKind,
    val url: String,
    val documentRevision: String,
    val section: String,
    val retrievedOn: LocalDate,
    val publishedOn: LocalDate? = null
) {
    init {
        require(WaterMethodProfileRules.isEvidenceUrl(url)) {
            "Water method evidence requires an HTTPS source URL."
        }
        require(listOf(url, documentRevision, section).all(WaterMethodProfileRules::isCanonicalText)) {
            "Evidence must retain the source revision and relevant section."
        }
        require(publishedOn == null || publishedOn <= retrievedOn) {
            "Evidence cannot be retrieved before its publication."
        }
    }
}

data class WaterMethodSourceSemantic(
    val parameter: WaterParameter,
    val basis: WaterMeasurementBasis,
    val unit: WaterMeasurementUnit,
    val analyticalScope: WaterMethodAnalyticalScope
) {
    init {
        require(parameter != WaterParameter.AMMONIA_AMMONIUM) {
            "An ambiguous legacy ammonia field cannot define a published method."
        }
        require(analyticalScope in WaterMethodAnalyticalScope.allowedFor(parameter)) {
            "The analytical scope does not describe its parameter."
        }
        require(basis in WaterParameterDefinitions.basisOptions(parameter)) {
            "Method reporting basis does not describe its parameter."
        }
        require(
            unit in WaterParameterDefinitions.unitOptions(parameter) ||
                unit == WaterParameterDefinitions.canonicalUnit(parameter)
        ) { "Method source unit does not describe its parameter." }
    }
}

data class WaterMethodOutput(
    val id: String,
    val semantic: WaterMethodSourceSemantic,
    val scale: WaterMethodResultScale
) {
    init {
        require(WaterMethodProfileRules.isStableId(id)) { "Invalid method result channel id." }
    }
}

/** Outputs in one mode are concurrent; only distinct modes are mutually exclusive. */
data class WaterMethodMode(
    val id: String,
    val matrices: Set<WaterSampleMatrix>,
    val outputs: List<WaterMethodOutput>
) {
    init {
        require(WaterMethodProfileRules.isStableId(id)) { "Invalid method mode id." }
        require(matrices.isNotEmpty()) { "Each method mode must declare its supported sample matrices." }
        require(outputs.isNotEmpty()) { "A method mode must produce at least one result." }
        require(outputs.map { it.id }.distinct().size == outputs.size) { "Duplicate result channel." }
        require(outputs.map { it.semantic.parameter }.distinct().size == outputs.size) {
            "Concurrent results must have distinct measured parameters."
        }
    }
}

/** Source metadata alone does not authorize a chemical conversion or a health rule. */
data class WaterMethodProfile(
    val key: WaterMethodProfileKey,
    val product: WaterMethodProduct,
    val matrices: Set<WaterSampleMatrix>,
    val modes: List<WaterMethodMode>,
    val evidence: List<WaterMethodEvidence>
) {
    init {
        require(matrices.isNotEmpty()) { "Supported sample matrices must be explicit." }
        require(modes.isNotEmpty()) { "A method must declare its result modes." }
        require(modes.map { it.id }.distinct().size == modes.size) { "Duplicate method mode id." }
        require(modes.flatMap { it.matrices }.toSet() == matrices) {
            "Product matrix coverage must match its supported method modes."
        }
        require(evidence.isNotEmpty()) { "A published method needs primary source evidence." }
    }

    val requiresModeSelection: Boolean get() = modes.size > 1
}

internal object WaterMethodProfileRules {
    private val stableId = Regex("[a-z][a-z0-9_]*")

    fun isStableId(value: String): Boolean = stableId.matches(value)

    fun isCanonicalText(value: String): Boolean = value.isNotBlank() && value == value.trim()

    fun isEvidenceUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
