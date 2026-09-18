package com.aqua.aqualight.data.devices.light.library

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlSnapshot
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannelDescriptor
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryTarget
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus

internal fun DeviceLightStatus.toLibraryTarget(
    deviceUid: DeviceUid
): DeviceLightLibraryTarget = DeviceLightLibraryTarget(
    deviceUid = deviceUid.value,
    productKey = product.wireValue,
    channelDescriptors = channels.sortedBy { descriptor -> descriptor.order }.map { descriptor ->
        DeviceLightLibraryChannelDescriptor(
            channel = requireNotNull(
                DeviceLightLibraryChannel.fromSceneKey(descriptor.percentField)
            ),
            key = descriptor.key,
            displayName = descriptor.displayName,
            displayColorRgb = descriptor.displayColorRgb,
            order = descriptor.order
        )
    },
    estimatedPowerWatts = power.estimatedFixturePowerW
        ?.takeIf { product == DeviceLightProduct.WRGB_PRO_ELITE }
        ?.toInt()
)

internal fun DeviceLightControlSnapshot.toLibraryTarget(
    product: DeviceLightProduct
): DeviceLightLibraryTarget = DeviceLightLibraryTarget(
    deviceUid = deviceUid,
    productKey = productKey,
    channelDescriptors = product.sceneFields.zip(channels).mapIndexed { index, pair ->
        val (percentField, output) = pair
        DeviceLightLibraryChannelDescriptor(
            channel = requireNotNull(DeviceLightLibraryChannel.fromSceneKey(percentField)),
            key = output.key,
            displayName = output.displayName,
            displayColorRgb = output.displayColorRgb,
            order = index
        )
    },
    estimatedPowerWatts = hero.estimatedPowerWatts
        ?.takeIf { product == DeviceLightProduct.WRGB_PRO_ELITE }
        ?.toInt()
)

internal fun StoredDeviceLightLibraryEntry.toApplicationEntry(): DeviceLightLibraryEntry {
    val channels = channelKeysList.map { key ->
        requireNotNull(DeviceLightLibraryChannel.fromSceneKey(key))
    }
    val payload = when (kind) {
        StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_MANUAL ->
            DeviceLightLibraryPayload.Manual(
                DeviceLightLibraryScene(manual.channelsList.toApplicationChannels())
            )
        StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_CUSTOM ->
            DeviceLightLibraryPayload.Custom(
                weekdaysMask = custom.weekdaysMask,
                points = custom.pointsList.map { point ->
                    DeviceLightLibraryCustomPoint(
                        timeMs = point.timeMs,
                        scene = DeviceLightLibraryScene(
                            point.channelsList.toApplicationChannels()
                        )
                    )
                }
            )
        StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_UNSPECIFIED,
        StoredDeviceLightLibraryKind.UNRECOGNIZED -> error("Unsupported stored library kind.")
    }
    return DeviceLightLibraryEntry(
        id = id,
        name = displayName,
        productKey = productKey,
        channels = channels,
        payload = payload,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )
}

internal fun DeviceLightLibraryScene.toStoredChannelValues(
    channelOrder: List<DeviceLightLibraryChannel>
): List<StoredDeviceLightChannelValue> = channelOrder.map { channel ->
    StoredDeviceLightChannelValue.newBuilder()
        .setChannelKey(channel.sceneKey)
        .setPercent(channels.getValue(channel))
        .build()
}

private fun List<StoredDeviceLightChannelValue>.toApplicationChannels() = associate { value ->
    requireNotNull(DeviceLightLibraryChannel.fromSceneKey(value.channelKey)) to value.percent
}

internal fun requireExactScene(
    target: DeviceLightLibraryTarget,
    scene: DeviceLightLibraryScene
) {
    require(scene.channels.keys == target.channels.toSet()) {
        "Library scene channels must exactly match the target product."
    }
}
