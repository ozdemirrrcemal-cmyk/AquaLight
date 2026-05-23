package com.aqua.aqualight.data.devices.catalog

data class AquaDeviceDefinition(
    val type: AquaDeviceType,
    val family: AquaDeviceFamily,

    /**
     * Mevcut ESP32 firmware kimliği.
     *
     * Şimdiki firmware:
     * AquaName = legacyAquaName
     * Name     = legacyName
     */
    val legacyAquaName: String,
    val legacyName: String,

    /**
     * İleride ESP32 firmware tarafına eklenecek profesyonel ürün kimliği.
     *
     * Örnek:
     * ProductId = "aqualight.001"
     */
    val productId: String,
    val productFamily: String,
    val productModel: String,

    /**
     * Kullanıcıya gösterilecek model adı.
     */
    val displayName: String,

    val mainModule: AquaDeviceModule,
    val uiController: AquaDeviceUiController,
    val firmwareProtocol: FirmwareProtocol,

    val moduleVisibility: Map<AquaDeviceModule, ModuleVisibility>,
    val screens: Set<AquaDeviceScreen>,
    val features: Set<AquaDeviceFeature> = emptySet(),

    val minSupportedApiVersion: Int = 1,
    val maxSupportedApiVersion: Int? = null
) {

    init {
        require(legacyAquaName.isNotBlank()) {
            "legacyAquaName cannot be blank."
        }

        require(legacyName.isNotBlank()) {
            "legacyName cannot be blank."
        }

        require(productId.isNotBlank()) {
            "productId cannot be blank."
        }

        require(productFamily.isNotBlank()) {
            "productFamily cannot be blank."
        }

        require(productModel.isNotBlank()) {
            "productModel cannot be blank."
        }

        require(displayName.isNotBlank()) {
            "displayName cannot be blank."
        }

        require(screens.isNotEmpty()) {
            "Device must define at least one screen."
        }

        require(minSupportedApiVersion > 0) {
            "minSupportedApiVersion must be greater than 0."
        }

        require(
            maxSupportedApiVersion == null ||
                maxSupportedApiVersion >= minSupportedApiVersion
        ) {
            "maxSupportedApiVersion cannot be lower than minSupportedApiVersion."
        }
    }

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

    fun isApiVersionSupported(
        apiVersion: Int?
    ): Boolean {
        if (apiVersion == null || apiVersion <= 0) {
            return firmwareProtocol == FirmwareProtocol.LEGACY_GET_SET
        }

        if (apiVersion < minSupportedApiVersion) {
            return false
        }

        val maxVersion = maxSupportedApiVersion

        return maxVersion == null || apiVersion <= maxVersion
    }
}