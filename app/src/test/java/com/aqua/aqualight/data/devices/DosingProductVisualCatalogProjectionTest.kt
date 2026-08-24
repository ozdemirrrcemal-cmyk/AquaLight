package com.aqua.aqualight.data.devices

import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DosingProductVisualCatalogProjectionTest {

    @Test
    fun `dose pro catalog identity projects exact physical pump count everywhere`() {
        listOf(
            "DOSING_DOSE_PRO_2" to 2,
            "DOSING_DOSE_PRO_4" to 4
        ).forEach { (productKey, expectedCount) ->
            val snapshot = product(productKey).toSnapshot()

            assertEquals(expectedCount, snapshot.toOwnerDeviceListItem().dosingChannelCount)
            assertEquals(expectedCount, snapshot.toTankDeviceListItem().dosingChannelCount)
            assertEquals(expectedCount, snapshot.toOwnerDeviceStatusSnapshot().dosingChannelCount)
        }
    }

    @Test
    fun `unresolved dosing catalog identity does not fall back to four pumps`() {
        val exact = product("DOSING_DOSE_PRO_2").toSnapshot()
        val unresolved = exact.copy(
            product = exact.product.copy(productKey = "DOSING_UNKNOWN_PRODUCT")
        )

        assertNull(unresolved.toOwnerDeviceListItem().dosingChannelCount)
        assertNull(unresolved.toTankDeviceListItem().dosingChannelCount)
        assertNull(unresolved.toOwnerDeviceStatusSnapshot().dosingChannelCount)
    }

    private fun product(productKey: String): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { product ->
            product.productKey.value == productKey
        }

    private fun AqlCommercialCatalogProduct.toSnapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = DeviceUid("visual-${model.value}")
        ),
        product = DeviceProduct(
            brand = "AquaLight",
            productId = productId.value,
            productKey = productKey.value,
            family = family,
            familyRaw = family.wireValue,
            line = line.value,
            model = model.value,
            displayName = displayName,
            skuId = skuId.value,
            skuCode = skuCode.value,
            hardwareRevision = hardwareRevision.value
        )
    )
}
