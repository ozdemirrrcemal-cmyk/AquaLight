package com.aqua.aqualight.ui.common.dialog

import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.utils.DialogType

/**
 * Routes every user-initiated back action through the shared confirmation dialog when an editor
 * owns unsaved changes. The callbacks are bound to the Fragment view lifecycle so stale views
 * cannot receive confirmation results after navigation.
 */
class UnsavedChangesExitGuard private constructor(
    private val fragment: Fragment,
    private val requestKey: String,
    private val actionId: String,
    private val hasUnsavedChanges: () -> Boolean,
    private val isExitBlocked: () -> Boolean,
    private val exit: () -> Unit
) {

    data class Configuration(
        val requestKey: String,
        val actionId: String,
        val hasUnsavedChanges: () -> Boolean,
        val isExitBlocked: () -> Boolean = { false },
        val exit: () -> Unit
    )

    fun requestExit() {
        if (isExitBlocked()) return
        if (!hasUnsavedChanges()) {
            exit()
            return
        }

        ConfirmDialogFragment.show(
            fragmentManager = fragment.childFragmentManager,
            request = ConfirmDialogFragment.Request(
                title = fragment.getString(R.string.common_unsaved_changes_exit_title),
                message = fragment.getString(R.string.common_unsaved_changes_exit_message),
                confirmText = fragment.getString(R.string.common_unsaved_changes_exit_action),
                cancelText = fragment.getString(R.string.common_unsaved_changes_continue_action),
                presentation = ConfirmDialogFragment.Presentation(
                    type = DialogType.WARNING,
                    destructive = true
                ),
                resultTarget = ConfirmDialogFragment.ResultTarget(
                    requestKey = requestKey,
                    actionId = actionId
                )
            )
        )
    }

    private fun handleResult(result: String?, resultActionId: String?) {
        if (resultActionId != actionId) return
        if (result == ConfirmDialogFragment.RESULT_CONFIRM && !isExitBlocked()) {
            exit()
        }
    }

    companion object {
        fun attach(
            fragment: Fragment,
            configuration: Configuration
        ): UnsavedChangesExitGuard {
            val guard = UnsavedChangesExitGuard(
                fragment = fragment,
                requestKey = configuration.requestKey,
                actionId = configuration.actionId,
                hasUnsavedChanges = configuration.hasUnsavedChanges,
                isExitBlocked = configuration.isExitBlocked,
                exit = configuration.exit
            )
            fragment.childFragmentManager.setFragmentResultListener(
                configuration.requestKey,
                fragment.viewLifecycleOwner
            ) { _, result ->
                guard.handleResult(
                    result = result.getString(ConfirmDialogFragment.RESULT_KEY),
                    resultActionId = result.getString(ConfirmDialogFragment.RESULT_ACTION_ID)
                )
            }
            fragment.requireActivity().onBackPressedDispatcher.addCallback(
                fragment.viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() = guard.requestExit()
                }
            )
            return guard
        }
    }
}
