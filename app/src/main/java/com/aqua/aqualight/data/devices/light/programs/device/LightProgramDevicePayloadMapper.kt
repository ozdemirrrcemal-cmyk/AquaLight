package com.aqua.aqualight.data.devices.light.programs.device

import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.light.LightProgram
import com.aqua.aqualight.data.devices.api.light.LightProgramPoint
import com.aqua.aqualight.data.devices.light.programs.compiler.CompiledLightProgramSchedule
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram

/**
 * Single mapping boundary between stored/compiled light programs and the
 * firmware-facing LightApi contract.
 *
 * UI must never build firmware payloads. When firmware gains native repeat or
 * transition support, update this boundary and the concrete LightApi writer;
 * screens and ViewModels should stay unchanged.
 */
object LightProgramDevicePayloadMapper {

    fun toApiProgram(
        savedProgram: SavedLightProgram,
        schedule: CompiledLightProgramSchedule
    ): LightProgram {
        return LightProgram(
            id = savedProgram.id,
            name = savedProgram.name,
            isActive = true,
            startMinute = savedProgram.startMinute,
            peakStartMinute = savedProgram.peakStartMinute,
            peakEndMinute = savedProgram.peakEndMinute,
            endMinute = savedProgram.endMinute,
            channelValues = LightChannelValues(
                red = savedProgram.red,
                green = savedProgram.green,
                blue = savedProgram.blue,
                white = savedProgram.white
            ).normalized(),
            repeatDays = SavedLightProgram.ALL_DAYS,
            points = schedule.points.map { point ->
                LightProgramPoint(
                    minuteOfDay = point.minuteOfDay,
                    red = point.red,
                    green = point.green,
                    blue = point.blue,
                    white = point.white
                )
            }
        )
    }
}
