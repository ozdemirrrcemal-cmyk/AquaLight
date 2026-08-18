package com.aqua.aqualight.data.devices.dosing.v1

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDosingV1ProgramDayDateMappingTest {

    @Test
    fun `canonical firmware program day maps to local date`() {
        assertEquals(
            LocalDate.of(2026, 8, 18),
            parseFirmwareProgramDayDate("2026-08-18")
        )
    }

    @Test
    fun `missing firmware program day stays unavailable`() {
        assertNull(parseFirmwareProgramDayDate(null))
    }

    @Test
    fun `malformed firmware program day fails closed without handset fallback`() {
        assertNull(parseFirmwareProgramDayDate("2026-02-30"))
        assertNull(parseFirmwareProgramDayDate("18-08-2026"))
        assertNull(parseFirmwareProgramDayDate("2026-8-18"))
    }
}
