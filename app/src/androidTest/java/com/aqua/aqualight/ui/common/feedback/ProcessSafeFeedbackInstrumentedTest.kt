package com.aqua.aqualight.ui.common.feedback

import android.os.Bundle
import android.os.Parcel
import androidx.core.os.bundleOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.base.loading.LoadingOverlayDialogFragment
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetAction
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetActionStyle
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetDetailRow
import com.aqua.aqualight.ui.common.bottomsheet.CareProfileBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.CareTaskTypeBottomSheetFragment
import com.aqua.aqualight.ui.common.bottomsheet.CountryPickerBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.GlobalActionBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.SingleChoiceBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankSettingsEditorBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.ThemeBottomSheet
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionBottomSheet
import com.aqua.aqualight.utils.DialogType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessSafeFeedbackInstrumentedTest {

    @Test
    fun fragmentDialogsExposeNoArgumentConstructors() {
        val sheetClasses = listOf(
            ThemeBottomSheet::class.java,
            PhotoSourceBottomSheet::class.java,
            CountryPickerBottomSheet::class.java,
            CareTaskTypeBottomSheetFragment::class.java,
            CapabilityPermissionBottomSheet::class.java,
            TankSettingsEditorBottomSheet::class.java,
            GlobalActionBottomSheet::class.java,
            SingleChoiceBottomSheet::class.java,
            TextInputBottomSheet::class.java,
            CareProfileBottomSheet::class.java,
            AppDatePickerDialogFragment::class.java,
            AppTimePickerDialogFragment::class.java,
            ConfirmDialogFragment::class.java,
            LoadingOverlayDialogFragment::class.java,
            FeedbackBottomSheet::class.java
        )

        sheetClasses.forEach { sheetClass ->
            assertNotNull(sheetClass.getDeclaredConstructor().newInstance())
        }
    }

    @Test
    fun feedbackSheetArgumentsCanRecreateTheSameRequest() {
        val original = feedbackSheet()
        val recreated = FeedbackBottomSheet::class.java
            .getDeclaredConstructor()
            .newInstance()
            .apply { arguments = Bundle(original.requireArguments()) }

        assertBundlesEquivalent(
            expected = original.requireArguments(),
            actual = recreated.requireArguments()
        )
    }

    @Test
    fun confirmDialogArgumentsCanRecreateTheSameRequest() {
        val original = confirmDialog()
        val recreated = ConfirmDialogFragment::class.java
            .getDeclaredConstructor()
            .newInstance()
            .apply { arguments = Bundle(original.requireArguments()) }

        assertBundlesEquivalent(
            expected = original.requireArguments(),
            actual = recreated.requireArguments()
        )
    }

    @Test
    fun globalActionArgumentsCanRecreateDetailsAndActions() {
        val original = GlobalActionBottomSheet.newInstance(
            title = "Completed task",
            message = "Choose an action",
            details = listOf(BottomSheetDetailRow("Aquarium", "Tank 1")),
            actions = listOf(
                BottomSheetAction(
                    id = "delete",
                    text = "Delete",
                    style = BottomSheetActionStyle.DANGER
                )
            ),
            requestKey = "test_global_action_result",
            payloadId = "task-42"
        )
        val recreated = GlobalActionBottomSheet::class.java
            .getDeclaredConstructor()
            .newInstance()
            .apply { arguments = Bundle(original.requireArguments()) }

        assertBundlesEquivalent(
            expected = original.requireArguments(),
            actual = recreated.requireArguments()
        )
    }

    @Test
    fun parcelRoundTripPreservesArgumentsAcrossProcessBoundary() {
        val original = GlobalActionBottomSheet.newInstance(
            title = "Completed task",
            message = "Choose an action",
            details = listOf(BottomSheetDetailRow("Aquarium", "Tank 1")),
            actions = listOf(
                BottomSheetAction("delete", "Delete", BottomSheetActionStyle.DANGER)
            ),
            requestKey = "process_recreation_result",
            payloadId = "task-42"
        ).requireArguments()

        val parcel = Parcel.obtain()
        val restored = try {
            parcel.writeBundle(original)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally {
            parcel.recycle()
        }

        val recreated = GlobalActionBottomSheet().apply { arguments = restored }
        assertBundlesEquivalent(
            expected = original,
            actual = recreated.requireArguments()
        )
    }

    @Test
    fun confirmDialogSurvivesActivityRecreationWithArgumentsIntact() {
        var expectedArguments: Bundle? = null
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = confirmDialog()
                expectedArguments = Bundle(dialog.requireArguments())
                dialog.show(activity.supportFragmentManager, TEST_CONFIRM_TAG)
                activity.supportFragmentManager.executePendingTransactions()
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val restored = activity.supportFragmentManager
                    .findFragmentByTag(TEST_CONFIRM_TAG) as? ConfirmDialogFragment
                assertNotNull(restored)
                assertBundlesEquivalent(
                    expected = requireNotNull(expectedArguments),
                    actual = requireNotNull(restored).requireArguments()
                )
            }
        }
    }

    @Test
    fun feedbackAndCareSheetsSurviveActivityRecreationWithArgumentsIntact() {
        var expectedArguments: Bundle? = null
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val sheet = feedbackSheet()
                expectedArguments = Bundle(sheet.requireArguments())
                sheet.show(activity.supportFragmentManager, TEST_FEEDBACK_TAG)
                activity.supportFragmentManager.executePendingTransactions()
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val restored = activity.supportFragmentManager
                    .findFragmentByTag(TEST_FEEDBACK_TAG) as? FeedbackBottomSheet
                assertNotNull(restored)
                assertBundlesEquivalent(
                    expected = requireNotNull(expectedArguments),
                    actual = requireNotNull(restored).requireArguments()
                )
            }
        }

        var expectedCareArguments: Bundle? = null
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                CareTaskTypeBottomSheetFragment.show(
                    fragmentManager = activity.supportFragmentManager,
                    title = "Select care task",
                    resultRequestKey = "stage14_care_rotation_result",
                    selectedType = CareTaskType.WATER_CHANGE
                )
                activity.supportFragmentManager.executePendingTransactions()
                val sheet = activity.supportFragmentManager.fragments
                    .filterIsInstance<CareTaskTypeBottomSheetFragment>()
                    .single()
                expectedCareArguments = Bundle(sheet.requireArguments())
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val restored = activity.supportFragmentManager.fragments
                    .filterIsInstance<CareTaskTypeBottomSheetFragment>()
                    .singleOrNull()
                assertNotNull(restored)
                assertBundlesEquivalent(
                    expected = requireNotNull(expectedCareArguments),
                    actual = requireNotNull(restored).requireArguments()
                )
            }
        }
    }

    @Test
    fun tankEditorSurvivesActivityRecreationWithArgumentsIntact() {
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
        val expected = Bundle(original.requireArguments())
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                original.show(activity.supportFragmentManager, TEST_TANK_EDITOR_TAG)
                activity.supportFragmentManager.executePendingTransactions()
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val restored = activity.supportFragmentManager
                    .findFragmentByTag(TEST_TANK_EDITOR_TAG) as? TankSettingsEditorBottomSheet
                assertNotNull(restored)
                assertBundlesEquivalent(
                    expected = expected,
                    actual = requireNotNull(restored).requireArguments()
                )
            }
        }
    }

    @Test
    fun themeSheetContainsNoRuntimeCallbackFields() {
        val fieldNames = ThemeBottomSheet::class.java.declaredFields
            .map { field -> field.name }

        assertNull(fieldNames.firstOrNull { it == "onBeforeThemeApplied" })
        assertNull(fieldNames.firstOrNull { it == "onThemeChanged" })
    }

    private fun assertBundlesEquivalent(
        expected: Bundle,
        actual: Bundle
    ) {
        expected.classLoader = javaClass.classLoader
        actual.classLoader = javaClass.classLoader

        assertEquals(expected.keySet(), actual.keySet())
        expected.keySet().forEach { key ->
            val expectedValue = expected.get(key)
            val actualValue = actual.get(key)
            when {
                expectedValue is BooleanArray && actualValue is BooleanArray -> {
                    assertArrayEquals("Bundle key: $key", expectedValue, actualValue)
                }
                expectedValue is IntArray && actualValue is IntArray -> {
                    assertArrayEquals("Bundle key: $key", expectedValue, actualValue)
                }
                expectedValue is LongArray && actualValue is LongArray -> {
                    assertArrayEquals("Bundle key: $key", expectedValue, actualValue)
                }
                expectedValue is Array<*> && actualValue is Array<*> -> {
                    assertArrayEquals("Bundle key: $key", expectedValue, actualValue)
                }
                expectedValue is ArrayList<*> && actualValue is ArrayList<*> -> {
                    assertEquals("Bundle key: $key", expectedValue.toList(), actualValue.toList())
                }
                else -> assertEquals("Bundle key: $key", expectedValue, actualValue)
            }
        }
    }

    private fun feedbackSheet(): FeedbackBottomSheet {
        return FeedbackBottomSheet.newInstance(
            title = "Delete device",
            message = "Delete this device?",
            primaryText = "Delete",
            cancelText = "Cancel",
            tone = FeedbackBottomSheet.FeedbackTone.DANGER,
            requestKey = "test_feedback_result",
            actionId = "device-123"
        )
    }

    private fun confirmDialog(): ConfirmDialogFragment {
        return ConfirmDialogFragment.newInstance(
            ConfirmDialogFragment.Request(
                title = "Reset channel",
                message = "Reset all dosing configuration?",
                confirmText = "Reset",
                cancelText = "Cancel",
                presentation = ConfirmDialogFragment.Presentation(
                    type = DialogType.ERROR
                ),
                resultTarget = ConfirmDialogFragment.ResultTarget(
                    requestKey = "test_confirm_dialog_result",
                    actionId = "channel-2"
                )
            )
        )
    }

    private companion object {
        const val TEST_CONFIRM_TAG = "stage8_confirm_dialog_rotation_test"
        const val TEST_FEEDBACK_TAG = "stage8_feedback_rotation_test"
        const val TEST_TANK_EDITOR_TAG = "stage14_tank_editor_rotation_test"
    }
}
