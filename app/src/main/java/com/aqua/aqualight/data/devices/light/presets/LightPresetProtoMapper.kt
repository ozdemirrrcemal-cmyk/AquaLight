package com.aqua.aqualight.data.devices.light.presets

import com.aqua.aqualight.data.devices.light.presets.model.SavedLightPreset

object LightPresetProtoMapper {

    fun toProto(
        preset: SavedLightPreset
    ): LightPresetProto {
        return LightPresetProto
            .newBuilder()
            .setId(preset.id)
            .setName(preset.name)
            .setRed(preset.red)
            .setGreen(preset.green)
            .setBlue(preset.blue)
            .setWhite(preset.white)
            .setCreatedAt(preset.createdAt)
            .setUpdatedAt(preset.updatedAt)
            .build()
    }

    fun fromProto(
        proto: LightPresetProto
    ): SavedLightPreset {
        return SavedLightPreset(
            id = proto.id,
            name = proto.name,
            red = proto.red,
            green = proto.green,
            blue = proto.blue,
            white = proto.white,
            createdAt = proto.createdAt,
            updatedAt = proto.updatedAt
        )
    }
}