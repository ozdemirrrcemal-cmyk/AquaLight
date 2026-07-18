package com.aqua.aqualight.ui.common.feedback

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.ui.common.bottomsheet.CareTaskTypeBottomSheetFragment
import com.aqua.aqualight.ui.common.bottomsheet.CountryPickerBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankSettingsEditorBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.ThemeBottomSheet
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionBottomSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessSafeFeedbackInstrumentedTest {

    @Test
    fun fragmentSheetsExposeNoArgumentConstructors() {
        val sheetClasses = listOf(
            ThemeBottomSheet::class.java,
            PhotoSourceBottomSheet::class.java,
            CountryPickerBottomSheet::class.java,
            CareTaskTypeBottomSheetFragment::class.java,
            CapabilityPermissionBottomSheet::class.java,
            TankSettingsEditorBottomSheet::class.java,
            FeedbackBottomSheet::class.java
        )

        sheetClasses.forEach { sheetClass ->
            assertNotNull(sheetClass.getDeclaredConstructor().newInstance())
        }
    }

    @Test
    fun feedbackSheetArgumentsCanRecreateTheSameRequest() {
        val original = FeedbackBottomSheet.newInstance(
            title = "Delete device",
            message = "Delete this device?",
            primaryText = "Delete",
            cancelText = "Cancel",
            tone = FeedbackBottomSheet.FeedbackTone.DANGER,
            requestKey = "test_feedback_result",
            actionId = "device-123"
        )
        val recreated = FeedbackBottomSheet::class.java
            .getDeclaredConstructor()
            .newInstance()
            .apply {
                arguments = Bundle(original.requireArguments())
            }

        assertEquals(original.requireArguments(), recreated.requireArguments())
    }

    @Test
    fun tankEditorArgumentsCanRecreateTheSameModeAndValues() {
        val original = TankSettingsEditorBottomSheet().apply {
            arguments = bundleOf(
                "arg_mode" to TankSettingsEditorBottomSheet.Mode.SIZE.name,
                "arg_title" to "Tank size",
                "arg_current_text" to "",
                "arg_validation_message" to "Invalid size",
                "arg_current_millis" to Long.MIN_VALUE,
                "arg_min_year" to 2000,
                "arg_max_year" to 2100,
                "arg_locale_tag" to "en-US",
                "arg_width_cm" to 60,
                "arg_length_cm" to 40,
                "arg_height_cm" to 40,
                "arg_current_unit" to "cm"
            )
        }
        val recreated = TankSettingsEditorBottomSheet::class.java
            .getDeclaredConstructor()
            .newInstance()
            .apply {
                arguments = Bundle(original.requireArguments())
            }

        assertEquals(original.requireArguments(), recreated.requireArguments())
    }

    @Test
    fun themeSheetContainsNoRuntimeCallbackFields() {
        val fieldNames = ThemeBottomSheet::class.java.declaredFields
            .map { field -> field.name }

        assertNull(fieldNames.firstOrNull { it == "onBeforeThemeApplied" })
        assertNull(fieldNames.firstOrNull { it == "onThemeChanged" })
    }
}
