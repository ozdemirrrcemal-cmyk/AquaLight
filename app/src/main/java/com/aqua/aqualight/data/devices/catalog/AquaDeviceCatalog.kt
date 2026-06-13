package com.aqua.aqualight.data.devices.catalog

import com.aqua.aqualight.data.devices.catalog.cooling.CoolingDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.cooling.CoolingProductCatalog
import com.aqua.aqualight.data.devices.catalog.dosing.DosingDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.dosing.DosingProductCatalog
import com.aqua.aqualight.data.devices.catalog.light.LightDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.light.LightProductCatalog
import com.aqua.aqualight.data.devices.catalog.timer.TimerDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.timer.TimerProductCatalog
import java.util.Locale

object AquaDeviceCatalog {

    val allDefinitions: List<AquaDeviceDefinition>
        get() = buildList {
            addAll(
                LightProductCatalog.all.map { definition ->
                    definition.base
                }
            )

            addAll(
                TimerProductCatalog.all.map { definition ->
                    definition.base
                }
            )

            addAll(
                CoolingProductCatalog.all.map { definition ->
                    definition.base
                }
            )

            addAll(
                DosingProductCatalog.all.map { definition ->
                    definition.base
                }
            )
        }

    fun findByProductKey(
        productKey: AquaProductKey
    ): AquaDeviceDefinition? {
        if (productKey == AquaProductKey.UNKNOWN) {
            return null
        }

        return allDefinitions.firstOrNull { definition ->
            definition.productKey == productKey
        }
    }

    fun findByProductId(
        productId: String?
    ): AquaDeviceDefinition? {
        val normalizedProductId = productId.normalizedProductId()

        if (normalizedProductId.isBlank()) {
            return null
        }

        return allDefinitions.firstOrNull { definition ->
            definition.productId.normalizedProductId() == normalizedProductId
        }
    }

    fun findBySetupCode(
        setupCode: String?
    ): AquaDeviceDefinition? {
        val normalizedSetupCode = setupCode.normalizedSetupCode()

        if (normalizedSetupCode.isBlank()) {
            return null
        }

        return allDefinitions.firstOrNull { definition ->
            definition.setupCode.normalizedSetupCode() == normalizedSetupCode
        }
    }

    fun findByCategory(
        category: AquaDeviceCategory
    ): List<AquaDeviceDefinition> {
        if (category == AquaDeviceCategory.UNKNOWN) {
            return emptyList()
        }

        return allDefinitions.filter { definition ->
            definition.category == category
        }
    }

    /**
     * Geçiş köprüsü. Yeni route kararları category/productKey ile alınmalı.
     */
    fun findByType(
        type: AquaDeviceType
    ): AquaDeviceDefinition? {
        if (type == AquaDeviceType.UNKNOWN) {
            return null
        }

        return allDefinitions.firstOrNull { definition ->
            definition.type == type
        }
    }

    fun resolveCategoryByProductId(
        productId: String?
    ): AquaDeviceCategory {
        return findByProductId(
            productId = productId
        )?.category ?: AquaDeviceCategory.UNKNOWN
    }

    fun resolveTypeByProductId(
        productId: String?
    ): AquaDeviceType {
        return findByProductId(
            productId = productId
        )?.type ?: AquaDeviceType.UNKNOWN
    }

    @Deprecated(
        message = "Commercial identity uses ProductId, DeviceUid and setupCode."
    )
    fun findByLegacyIdentity(
        aquaName: String?,
        name: String?
    ): AquaDeviceDefinition? {
        val normalizedAquaName = aquaName.normalizedIdentity()
        val normalizedName = name.normalizedIdentity()

        if (
            normalizedAquaName.isBlank() ||
            normalizedName.isBlank()
        ) {
            return null
        }

        return allDefinitions.firstOrNull { definition ->
            definition.productFamily.normalizedIdentity() == normalizedAquaName &&
                definition.productModel.normalizedIdentity() == normalizedName
        }
    }

    @Deprecated(
        message = "Commercial identity uses ProductId, DeviceUid and setupCode."
    )
    fun resolveTypeByLegacyIdentity(
        aquaName: String?,
        name: String?
    ): AquaDeviceType {
        return findByLegacyIdentity(
            aquaName = aquaName,
            name = name
        )?.type ?: AquaDeviceType.UNKNOWN
    }

    fun lightDefinitionOf(
        productKey: AquaProductKey
    ): LightDeviceDefinition? {
        return LightProductCatalog.findByProductKey(
            productKey = productKey
        )
    }

    fun lightDefinitionOf(
        type: AquaDeviceType
    ): LightDeviceDefinition? {
        return LightProductCatalog.findByType(
            type = type
        )
    }

    fun timerDefinitionOf(
        productKey: AquaProductKey
    ): TimerDeviceDefinition? {
        return TimerProductCatalog.findByProductKey(
            productKey = productKey
        )
    }

    fun timerDefinitionOf(
        type: AquaDeviceType
    ): TimerDeviceDefinition? {
        return TimerProductCatalog.findByType(
            type = type
        )
    }

    fun coolingDefinitionOf(
        productKey: AquaProductKey
    ): CoolingDeviceDefinition? {
        return CoolingProductCatalog.findByProductKey(
            productKey = productKey
        )
    }

    fun coolingDefinitionOf(
        type: AquaDeviceType
    ): CoolingDeviceDefinition? {
        return CoolingProductCatalog.findByType(
            type = type
        )
    }

    fun dosingDefinitionOf(
        productKey: AquaProductKey
    ): DosingDeviceDefinition? {
        return DosingProductCatalog.findByProductKey(
            productKey = productKey
        )
    }

    fun dosingDefinitionOf(
        type: AquaDeviceType
    ): DosingDeviceDefinition? {
        return DosingProductCatalog.findByType(
            type = type
        )
    }

    fun isSupported(
        type: AquaDeviceType
    ): Boolean {
        return type != AquaDeviceType.UNKNOWN &&
            findByType(
                type = type
            ) != null
    }

    fun isSupported(
        productId: String?
    ): Boolean {
        return findByProductId(
            productId = productId
        ) != null
    }

    private fun String?.normalizedIdentity(): String {
        return this
            ?.trim()
            ?.replace(
                Regex("\\s+"),
                " "
            )
            ?.lowercase(
                Locale.US
            )
            .orEmpty()
    }

    private fun String?.normalizedProductId(): String {
        return this
            ?.trim()
            ?.lowercase(
                Locale.US
            )
            .orEmpty()
    }

    private fun String?.normalizedSetupCode(): String {
        return this
            ?.trim()
            ?.uppercase(
                Locale.US
            )
            .orEmpty()
    }
}
