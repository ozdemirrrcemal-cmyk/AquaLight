package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaterSourceChangeCandidateCodecTest {

    @Test
    fun confirmationSurvivesDialogRecreationWithItsExactSelection() {
        val parameterId = WaterTestParameterId.NITRATE
        val selection = WaterMeasurementUiCatalog.defaultSelection(parameterId)
            .copy(method = WaterMeasurementMethodUi.TEST_KIT, testKitId = "other")
        val encoded = WaterSourceChangeCandidateCodec.encode(parameterId, selection)

        assertEquals(parameterId to selection, WaterSourceChangeCandidateCodec.decode(encoded))
        assertNull(WaterSourceChangeCandidateCodec.decode("invalid"))
    }
}
