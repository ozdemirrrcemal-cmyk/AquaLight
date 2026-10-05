package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import org.junit.Assert.assertEquals
import org.junit.Test

class LivestockHealthUiTextTest {

    @Test
    fun otherObservationUsesUserDescriptionAsDisplayLabel() {
        assertEquals(
            "Camın arkasında uzun süre hareketsiz kalıyor",
            LivestockHealthUiText.resolveObservationLabel(
                symptomKey = LivestockHealthUiText.SYMPTOM_OTHER,
                otherObservation = "  Camın arkasında uzun süre hareketsiz kalıyor  ",
                fallbackLabel = "Diğer"
            )
        )
    }

    @Test
    fun blankOtherObservationFallsBackToCategoryLabel() {
        assertEquals(
            "Diğer",
            LivestockHealthUiText.resolveObservationLabel(
                symptomKey = LivestockHealthUiText.SYMPTOM_OTHER,
                otherObservation = "   ",
                fallbackLabel = "Diğer"
            )
        )
    }

    @Test
    fun knownSymptomKeepsCatalogLabel() {
        assertEquals(
            "Hızlı soluma / yüzeye çıkma",
            LivestockHealthUiText.resolveObservationLabel(
                symptomKey = LivestockHealthUiText.SYMPTOM_SURFACE,
                otherObservation = "Kullanıcı açıklaması",
                fallbackLabel = "Hızlı soluma / yüzeye çıkma"
            )
        )
    }
}
