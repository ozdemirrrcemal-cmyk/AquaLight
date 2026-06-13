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

    fun findDefinition(
        productId: String?,
        productKey: AquaProductKey = AquaProductKey.UNKNOWN,
        category: AquaDeviceCategory = AquaDeviceCategory.UNKNOWN
    ): AquaDeviceDefinition? {
        findByProductId(
            productId = productId
        )?.let { definition ->
            return definition
        }

        findByProductKey(
            productKey = productKey
        )?.let { definition ->
            return definition
        }

        return findByCategory(
            category = category
        ).firstOrNull()
    }


    fun resolveCategoryByProductId(
        productId: String?
    ): AquaDeviceCategory {
        return findByProductId(
            productId = productId
        )?.category ?: AquaDeviceCategory.UNKNOWN
    }


    fun lightDefinitionOf(
        productKey: AquaProductKey
    ): LightDeviceDefinition? {
        return LightProductCatalog.findByProductKey(
            productKey = productKey
        )
    }


    fun timerDefinitionOf(
        productKey: AquaProductKey
    ): TimerDeviceDefinition? {
        return TimerProductCatalog.findByProductKey(
            productKey = productKey
        )
    }


    fun coolingDefinitionOf(
        productKey: AquaProductKey
    ): CoolingDeviceDefinition? {
        return CoolingProductCatalog.findByProductKey(
            productKey = productKey
        )
    }


    fun dosingDefinitionOf(
        productKey: AquaProductKey
    ): DosingDeviceDefinition? {
        return DosingProductCatalog.findByProductKey(
            productKey = productKey
        )
    }



    fun isSupported(
        productId: String?
    ): Boolean {
        return findByProductId(
            productId = productId
        ) != null
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
