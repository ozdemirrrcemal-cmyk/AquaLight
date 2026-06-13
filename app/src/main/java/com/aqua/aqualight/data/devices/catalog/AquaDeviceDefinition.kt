package com.aqua.aqualight.data.devices.catalog

/**
 * Ticari ürün katalog tanımı.
 *
 * Bu model ürün kimliğini, route kararını, setup Wi-Fi kodunu, ürün varyantlarını
 * ve desteklenen ekran/özellikleri tek yerde toplar. Firmware tarafı da aynı
 * ProductId + setupCode + category sözleşmesine uymalıdır.
 */
data class AquaDeviceDefinition(
    val productKey: AquaProductKey,
    val productId: String,
    val category: AquaDeviceCategory,

    val productFamily: String,
    val productLine: String,
    val productModel: String,
    val displayName: String,

    /**
     * Setup Wi-Fi adında kullanılan resmi ürün kodu.
     * Format: AQL-<setupCode>-<shortId>
     */
    val setupCode: String,

    val variants: List<AquaProductVariant> = emptyList(),

    val mainModule: AquaDeviceModule,
    val controllerType: AquaDeviceControllerType,
    val firmwareProtocol: FirmwareProtocol,

    val moduleVisibility: Map<AquaDeviceModule, ModuleVisibility>,
    val screens: Set<AquaDeviceScreen>,
    val features: Set<AquaDeviceFeature> = emptySet(),

    val minProtocolVersion: Int = 1,
    val maxProtocolVersion: Int? = null,

    val family: AquaDeviceFamily = category.defaultFamily
) {

    init {
        require(productKey != AquaProductKey.UNKNOWN) {
            "productKey cannot be UNKNOWN."
        }

        require(category != AquaDeviceCategory.UNKNOWN) {
            "category cannot be UNKNOWN."
        }

        require(productId.isNotBlank()) {
            "productId cannot be blank."
        }

        require(productId == productKey.productId) {
            "productId must match productKey.productId."
        }

        require(category == productKey.category) {
            "category must match productKey.category."
        }

        require(productFamily.isNotBlank()) {
            "productFamily cannot be blank."
        }

        require(productLine.isNotBlank()) {
            "productLine cannot be blank."
        }

        require(productModel.isNotBlank()) {
            "productModel cannot be blank."
        }

        require(displayName.isNotBlank()) {
            "displayName cannot be blank."
        }

        require(setupCode.isNotBlank()) {
            "setupCode cannot be blank."
        }

        require(setupCode == productKey.setupCode) {
            "setupCode must match productKey.setupCode."
        }

        require(screens.isNotEmpty()) {
            "Device must define at least one screen."
        }

        require(minProtocolVersion > 0) {
            "minProtocolVersion must be greater than 0."
        }

        require(
            maxProtocolVersion == null ||
                maxProtocolVersion >= minProtocolVersion
        ) {
            "maxProtocolVersion cannot be lower than minProtocolVersion."
        }
    }

    val routeKey: String
        get() = category.routeKey

    val supportsCommercialIdentity: Boolean
        get() = firmwareProtocol == FirmwareProtocol.AQUA_V1 ||
            firmwareProtocol == FirmwareProtocol.NATIVE_V1

    fun visibilityOf(
        module: AquaDeviceModule
    ): ModuleVisibility {
        return moduleVisibility[module] ?: ModuleVisibility.HIDDEN
    }

    fun supportsModule(
        module: AquaDeviceModule
    ): Boolean {
        return visibilityOf(module) != ModuleVisibility.HIDDEN
    }

    fun supportsScreen(
        screen: AquaDeviceScreen
    ): Boolean {
        return screens.contains(screen)
    }

    fun supportsFeature(
        feature: AquaDeviceFeature
    ): Boolean {
        return features.contains(feature)
    }

    fun isProtocolVersionSupported(
        protocolVersion: Int?
    ): Boolean {
        if (protocolVersion == null || protocolVersion <= 0) {
            return false
        }

        if (protocolVersion < minProtocolVersion) {
            return false
        }

        val maxVersion = maxProtocolVersion

        return maxVersion == null || protocolVersion <= maxVersion
    }

    @Deprecated(
        message = "Use isProtocolVersionSupported()."
    )
    fun isApiVersionSupported(
        apiVersion: Int?
    ): Boolean {
        return isProtocolVersionSupported(
            protocolVersion = apiVersion
        ) || firmwareProtocol == FirmwareProtocol.LEGACY_GET_SET
    }
}
