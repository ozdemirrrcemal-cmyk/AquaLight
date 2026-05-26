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

    fun findByType(
        type: AquaDeviceType
    ): AquaDeviceDefinition? {
        return allDefinitions.firstOrNull { definition ->
            definition.type == type
        }
    }

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
            definition.legacyAquaName.normalizedIdentity() == normalizedAquaName &&
                definition.legacyName.normalizedIdentity() == normalizedName
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

    fun resolveTypeByLegacyIdentity(
        aquaName: String?,
        name: String?
    ): AquaDeviceType {
        return findByLegacyIdentity(
            aquaName = aquaName,
            name = name
        )?.type ?: AquaDeviceType.UNKNOWN
    }

    fun resolveTypeByProductId(
        productId: String?
    ): AquaDeviceType {
        return findByProductId(
            productId = productId
        )?.type ?: AquaDeviceType.UNKNOWN
    }

    fun lightDefinitionOf(
        type: AquaDeviceType
    ): LightDeviceDefinition? {
        return LightProductCatalog.findByType(
            type = type
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
        type: AquaDeviceType
    ): CoolingDeviceDefinition? {
        return CoolingProductCatalog.findByType(
            type = type
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
}