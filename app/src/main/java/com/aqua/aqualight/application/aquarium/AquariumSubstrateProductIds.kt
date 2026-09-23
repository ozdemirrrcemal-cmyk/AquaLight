package com.aqua.aqualight.application.aquarium

object AquariumSubstrateProductIds {
    const val EXPECTED_CATALOG_PRODUCT_COUNT = 138

    private const val FIRST_PRODUCT_INDEX = 1
    private const val CHIHIROS_AQUASOIL_3L_INDEX = 1
    private const val CHIHIROS_AQUASOIL_9L_INDEX = 2
    private const val ADA_TOURMALINE_BC_INDEX = 3
    private const val DENNERLE_DEPONIT_MIX_INDEX = 4
    private const val ID_PADDING = 4
    private const val ID_PREFIX = "substrate_"

    fun productId(index: Int): String {
        require(index in FIRST_PRODUCT_INDEX..EXPECTED_CATALOG_PRODUCT_COUNT)
        return when (index) {
            CHIHIROS_AQUASOIL_3L_INDEX -> "substrate_chihiros_aquasoil_3l"
            CHIHIROS_AQUASOIL_9L_INDEX -> "substrate_chihiros_aquasoil_9l"
            ADA_TOURMALINE_BC_INDEX -> "substrate_ada_tourmaline_bc"
            DENNERLE_DEPONIT_MIX_INDEX -> "substrate_dennerle_deponitmix_4_8kg"
            else -> ID_PREFIX + index.toString().padStart(ID_PADDING, '0')
        }
    }

    fun productIds(
        firstProductId: String,
        lastProductId: String
    ): List<String> {
        val firstIndex = productIndex(firstProductId)
        val lastIndex = productIndex(lastProductId)
        require(firstIndex <= lastIndex)
        return (firstIndex..lastIndex).map(::productId)
    }

    private fun productIndex(productId: String): Int {
        require(productId.startsWith(ID_PREFIX))
        return productId.removePrefix(ID_PREFIX).toInt()
    }
}
