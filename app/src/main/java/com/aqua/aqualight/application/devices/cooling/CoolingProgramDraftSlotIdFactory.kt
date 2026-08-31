package com.aqua.aqualight.application.devices.cooling

import java.util.UUID

fun interface CoolingProgramDraftSlotIdFactory {
    fun create(): String
}

class UuidCoolingProgramDraftSlotIdFactory : CoolingProgramDraftSlotIdFactory {
    override fun create(): String = UUID.randomUUID().toString()
}
