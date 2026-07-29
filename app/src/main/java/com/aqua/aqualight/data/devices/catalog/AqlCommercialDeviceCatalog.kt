package com.aqua.aqualight.data.devices.catalog

import com.aqua.aqualight.data.devices.contract.AqlCatalogKeySet
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.contract.parseAqlDeviceFeatureKeysExact
import com.aqua.aqualight.data.devices.contract.parseAqlDeviceScreenKeysExact
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet
import com.aqua.aqualight.data.devices.model.DeviceCompatibilityIdentity
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceHardwareRevision
import com.aqua.aqualight.data.devices.model.DeviceLimitSet
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceProductId
import com.aqua.aqualight.data.devices.model.DeviceProductKey
import com.aqua.aqualight.data.devices.model.DeviceProductLine
import com.aqua.aqualight.data.devices.model.DeviceProductModel
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceSkuCode
import com.aqua.aqualight.data.devices.model.DeviceSkuId
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

internal enum class AqlCommercialCatalogFailureCode {
    MALFORMED_REPORTED_PRODUCT,
    UNKNOWN_COMPATIBILITY_IDENTITY,
    FAMILY_MISMATCH,
    LINE_MISMATCH,
    DISPLAY_NAME_MISMATCH,
    SKU_ID_MISMATCH,
    SKU_CODE_MISMATCH,
    CAPABILITIES_MISMATCH,
    LIMITS_MISMATCH,
    FEATURES_MISMATCH,
    SCREENS_MISMATCH
}

internal data class AqlCommercialCatalogFailure(
    val code: AqlCommercialCatalogFailureCode,
    val field: String
)

internal sealed interface AqlCommercialCatalogValidation {
    data class Valid(
        val product: AqlCommercialCatalogProduct
    ) : AqlCommercialCatalogValidation

    data class Invalid(
        val failure: AqlCommercialCatalogFailure
    ) : AqlCommercialCatalogValidation
}

internal object AqlCommercialDeviceCatalog {
    val products: List<AqlCommercialCatalogProduct> = AQL_GENERATED_COMMERCIAL_PRODUCTS

    private val productsByIdentity = products.associateBy(
        AqlCommercialCatalogProduct::compatibilityIdentity
    )

    init {
        require(products.size == EXPECTED_PRODUCT_COUNT) {
            "Commercial catalog must contain exactly $EXPECTED_PRODUCT_COUNT products."
        }
        require(productsByIdentity.size == products.size) {
            "Commercial catalog compatibility identities must be unique."
        }
    }

    fun validate(metadata: DeviceRuntimeMetadata): AqlCommercialCatalogValidation {
        return validateReported(metadata.toReportedCatalogProduct())
    }

    fun validateSnapshot(snapshot: DeviceSnapshot): AqlCommercialCatalogValidation {
        val reported = snapshot.toReportedCatalogProduct().getOrElse {
            return invalid(
                code = AqlCommercialCatalogFailureCode.MALFORMED_REPORTED_PRODUCT,
                field = "snapshot"
            )
        }
        return validateReported(reported)
    }

    private fun validateReported(
        reported: ReportedCatalogProduct
    ): AqlCommercialCatalogValidation {
        val product = productsByIdentity[reported.compatibilityIdentity]
            ?: return invalid(
                code = AqlCommercialCatalogFailureCode.UNKNOWN_COMPATIBILITY_IDENTITY,
                field = "compatibilityIdentity"
            )
        val failure = compareIdentity(product = product, reported = reported)
            ?: compareProfile(product = product, reported = reported)
        return failure?.let(AqlCommercialCatalogValidation::Invalid)
            ?: AqlCommercialCatalogValidation.Valid(product)
    }

    private fun compareIdentity(
        product: AqlCommercialCatalogProduct,
        reported: ReportedCatalogProduct
    ): AqlCommercialCatalogFailure? {
        return when {
            reported.family != product.family -> mismatch(
                AqlCommercialCatalogFailureCode.FAMILY_MISMATCH,
                "family"
            )
            reported.line != product.line -> mismatch(
                AqlCommercialCatalogFailureCode.LINE_MISMATCH,
                "line"
            )
            reported.displayName != product.displayName -> mismatch(
                AqlCommercialCatalogFailureCode.DISPLAY_NAME_MISMATCH,
                "displayName"
            )
            reported.skuId != product.skuId -> mismatch(
                AqlCommercialCatalogFailureCode.SKU_ID_MISMATCH,
                "skuId"
            )
            reported.skuCode != product.skuCode -> mismatch(
                AqlCommercialCatalogFailureCode.SKU_CODE_MISMATCH,
                "skuCode"
            )
            else -> null
        }
    }

    private fun compareProfile(
        product: AqlCommercialCatalogProduct,
        reported: ReportedCatalogProduct
    ): AqlCommercialCatalogFailure? {
        return when {
            reported.capabilities != product.profile.capabilities -> mismatch(
                AqlCommercialCatalogFailureCode.CAPABILITIES_MISMATCH,
                "capabilities"
            )
            reported.limits != product.limits -> mismatch(
                AqlCommercialCatalogFailureCode.LIMITS_MISMATCH,
                "limits"
            )
            reported.supportedFeatures != product.profile.supportedFeatures -> mismatch(
                AqlCommercialCatalogFailureCode.FEATURES_MISMATCH,
                "supportedFeatures"
            )
            reported.supportedScreens != product.profile.supportedScreens -> mismatch(
                AqlCommercialCatalogFailureCode.SCREENS_MISMATCH,
                "supportedScreens"
            )
            else -> null
        }
    }

    private const val EXPECTED_PRODUCT_COUNT = 9
}

private data class ReportedCatalogProduct(
    val compatibilityIdentity: DeviceCompatibilityIdentity,
    val family: DeviceFamily,
    val line: DeviceProductLine,
    val displayName: String,
    val skuId: DeviceSkuId,
    val skuCode: DeviceSkuCode,
    val capabilities: DeviceCapabilitySet,
    val limits: DeviceLimitSet,
    val supportedFeatures: Set<AqlDeviceFeatureKey>,
    val supportedScreens: Set<AqlDeviceScreenKey>
)

private fun DeviceRuntimeMetadata.toReportedCatalogProduct(): ReportedCatalogProduct {
    return ReportedCatalogProduct(
        compatibilityIdentity = identity.compatibilityIdentity,
        family = identity.family,
        line = identity.line,
        displayName = identity.displayName,
        skuId = identity.skuId,
        skuCode = identity.skuCode,
        capabilities = capabilities.capabilities,
        limits = capabilities.limits,
        supportedFeatures = capabilities.supportedFeatures,
        supportedScreens = capabilities.supportedScreens
    )
}

private fun DeviceSnapshot.toReportedCatalogProduct(): Result<ReportedCatalogProduct> = runCatching {
    ReportedCatalogProduct(
        compatibilityIdentity = product.toCompatibilityIdentity(),
        family = product.family,
        line = DeviceProductLine(product.line),
        displayName = requireExactText(product.displayName, "displayName"),
        skuId = DeviceSkuId(product.skuId),
        skuCode = DeviceSkuCode(product.skuCode),
        capabilities = capabilities.toExactCapabilitySet(),
        limits = limits.toExactLimitSet(),
        supportedFeatures = supportedFeatures.toExactFeatureSet(),
        supportedScreens = supportedScreens.toExactScreenSet()
    )
}

private fun DeviceProduct.toCompatibilityIdentity(): DeviceCompatibilityIdentity {
    return DeviceCompatibilityIdentity(
        productKey = DeviceProductKey(productKey),
        productId = DeviceProductId(productId),
        model = DeviceProductModel(model),
        hardwareRevision = DeviceHardwareRevision(hardwareRevision)
    )
}

private fun DeviceCapabilities.toExactCapabilitySet(): DeviceCapabilitySet {
    return DeviceCapabilitySet(
        light = light,
        manualLight = manualLight,
        lightProgram = lightProgram,
        lightPresets = lightPresets,
        lightSimulation = lightSimulation,
        fan = fan,
        cooling = cooling,
        temperature = temperature,
        standaloneTimer = standaloneTimer,
        dosing = dosing,
        timeSync = timeSync,
        ota = ota
    )
}

private fun DeviceLimits.toExactLimitSet(): DeviceLimitSet {
    return DeviceLimitSet(
        lightChannelCount = lightChannelCount,
        fanOutputCount = fanOutputCount,
        temperatureSensorCount = temperatureSensorCount,
        timerChannelCount = timerChannelCount,
        dosingChannelCount = dosingChannelCount
    )
}

private fun List<String>.toExactFeatureSet(): Set<AqlDeviceFeatureKey> {
    require(size == toSet().size) { "supportedFeatures must not contain duplicates." }
    return when (val parsed = parseAqlDeviceFeatureKeysExact()) {
        is AqlCatalogKeySet.Valid -> parsed.values
        is AqlCatalogKeySet.Invalid -> error("supportedFeatures contains unknown exact keys.")
    }
}

private fun List<String>.toExactScreenSet(): Set<AqlDeviceScreenKey> {
    require(size == toSet().size) { "supportedScreens must not contain duplicates." }
    return when (val parsed = parseAqlDeviceScreenKeysExact()) {
        is AqlCatalogKeySet.Valid -> parsed.values
        is AqlCatalogKeySet.Invalid -> error("supportedScreens contains unknown exact keys.")
    }
}

private fun requireExactText(value: String, field: String): String {
    require(value.isNotEmpty()) { "$field must not be empty." }
    require(!value.first().isWhitespace() && !value.last().isWhitespace()) {
        "$field must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) { "$field must not contain control characters." }
    return value
}

private fun mismatch(
    code: AqlCommercialCatalogFailureCode,
    field: String
): AqlCommercialCatalogFailure = AqlCommercialCatalogFailure(code = code, field = field)

private fun invalid(
    code: AqlCommercialCatalogFailureCode,
    field: String
): AqlCommercialCatalogValidation.Invalid = AqlCommercialCatalogValidation.Invalid(
    AqlCommercialCatalogFailure(code = code, field = field)
)
