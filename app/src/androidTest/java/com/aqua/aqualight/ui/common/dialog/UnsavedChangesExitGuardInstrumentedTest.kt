package com.aqua.aqualight.ui.common.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.feedback.Stage8DialogTestActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnsavedChangesExitGuardInstrumentedTest {

    @Test
    fun cleanEditorExitsWithoutShowingConfirmation() {
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.installGuardHost()

                host.guard.requestExit()

                assertEquals(1, host.exitCount)
                assertNull(host.confirmationDialog())
            }
        }
    }

    @Test
    fun dirtyEditorStaysOnCancelAndExitsOnlyOnConfirm() {
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.installGuardHost().apply { dirty = true }

                host.guard.requestExit()
                host.childFragmentManager.executePendingTransactions()
                requireNotNull(host.confirmationDialog()).requireDialog().cancel()
                host.childFragmentManager.executePendingTransactions()
                assertEquals(0, host.exitCount)

                host.guard.requestExit()
                host.childFragmentManager.executePendingTransactions()
                val confirmButton = requireNotNull(
                    requireNotNull(host.confirmationDialog())
                        .requireDialog()
                        .findViewById<View>(R.id.btnConfirmPrimary)
                )
                confirmButton.performClick()
                host.childFragmentManager.executePendingTransactions()

                assertEquals(1, host.exitCount)
            }
        }
    }

    @Test
    fun systemBackUsesTheSameDirtyEditorConfirmation() {
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.installGuardHost().apply { dirty = true }

                activity.onBackPressedDispatcher.onBackPressed()
                host.childFragmentManager.executePendingTransactions()

                requireNotNull(host.confirmationDialog())
                assertEquals(0, host.exitCount)
            }
        }
    }

    @Test
    fun operationInProgressCannotExitOrOpenConfirmation() {
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.installGuardHost().apply {
                    dirty = true
                    blocked = true
                }

                host.guard.requestExit()

                assertEquals(0, host.exitCount)
                assertNull(host.confirmationDialog())
            }
        }
    }
}

class UnsavedChangesExitGuardHostFragment : Fragment() {
    var dirty = false
    var blocked = false
    var exitCount = 0
    lateinit var guard: UnsavedChangesExitGuard

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FrameLayout(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        guard = UnsavedChangesExitGuard.attach(
            fragment = this,
            requestKey = REQUEST_KEY,
            actionId = ACTION_ID,
            hasUnsavedChanges = { dirty },
            isExitBlocked = { blocked },
            exit = { exitCount += 1 }
        )
    }

    fun confirmationDialog(): ConfirmDialogFragment? = childFragmentManager.fragments
        .filterIsInstance<ConfirmDialogFragment>()
        .singleOrNull()

    private companion object {
        const val REQUEST_KEY = "unsaved_changes_exit_guard_test"
        const val ACTION_ID = "exit_test_editor"
    }
}

private fun Stage8DialogTestActivity.installGuardHost(): UnsavedChangesExitGuardHostFragment {
    return UnsavedChangesExitGuardHostFragment().also { fragment ->
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
    }
}
