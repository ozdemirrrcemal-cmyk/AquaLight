package com.aqua.aqualight.application.aquarium.health

import java.util.Collections

/** Immutable exact-revision lookup; retiring a product never deletes its historical definition. */
class WaterMethodCatalogSnapshot(
    val revision: Int,
    profiles: List<WaterMethodProfile>,
    selectableKeys: Set<WaterMethodProfileKey>
) {
    private val profilesByKey = profiles.map(::freezeProfile).associateBy { it.key }
    val selectableKeys: Set<WaterMethodProfileKey> = immutableSet(selectableKeys)

    init {
        require(revision > 0) { "Catalog revision must be positive." }
        require(profilesByKey.size == profiles.size) { "Duplicate published product revision." }
        require(selectableKeys.all(profilesByKey::containsKey)) { "Selectable profile revision is missing." }
        require(selectableKeys.map { it.productId }.distinct().size == selectableKeys.size) {
            "Only one revision of each product can be selected for new measurements."
        }
        require(profiles.groupBy { it.key.productId }.values.all { versions ->
            versions.map { it.product }.distinct().size == 1
        }) { "A stable product id cannot be reused for another model or variant." }
    }

    fun resolve(key: WaterMethodProfileKey): WaterMethodProfile? = profilesByKey[key]

    fun selectableFor(parameter: WaterParameter, matrix: WaterSampleMatrix): List<WaterMethodProfile> =
        selectableKeys.mapNotNull(profilesByKey::get)
            .filter { profile ->
                matrix in profile.matrices && profile.modes.any { mode ->
                    matrix in mode.matrices && mode.outputs.any { it.semantic.parameter == parameter }
                }
            }
            .sortedBy { it.key.productId }

    fun successor(
        revision: Int,
        profiles: List<WaterMethodProfile>,
        selectableKeys: Set<WaterMethodProfileKey>
    ): WaterMethodCatalogSnapshot {
        require(revision > this.revision) { "Catalog revision must advance." }
        val next = WaterMethodCatalogSnapshot(revision, profiles, selectableKeys)
        require(profilesByKey.all { (key, profile) -> next.resolve(key) == profile }) {
            "Published profile revisions must be retained unchanged."
        }
        return next
    }
}

private fun freezeProfile(profile: WaterMethodProfile): WaterMethodProfile = profile.copy(
    matrices = immutableSet(profile.matrices),
    evidence = immutableList(profile.evidence),
    modes = immutableList(profile.modes.map { mode ->
        mode.copy(matrices = immutableSet(mode.matrices), outputs = immutableList(mode.outputs.map { output ->
            val precision = output.scale.precision
            output.copy(scale = output.scale.copy(
                qualifiers = immutableSet(output.scale.qualifiers),
                precision = if (precision is WaterMethodPrecision.ComparatorScale) {
                    precision.copy(values = immutableList(precision.values))
                } else {
                    precision
                }
            ))
        }))
    })
)

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())

private fun <T> immutableSet(values: Collection<T>): Set<T> =
    Collections.unmodifiableSet(values.toSet())
