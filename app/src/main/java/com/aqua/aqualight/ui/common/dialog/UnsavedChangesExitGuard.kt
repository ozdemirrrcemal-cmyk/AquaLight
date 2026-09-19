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
    private val configuration: Configuration
) {
    private var pendingAction: (() -> Unit)? = null

    data class Configuration(
        val requestKey: String,
        val actionId: String,
        val hasUnsavedChanges: () -> Boolean,
        val isExitBlocked: () -> Boolean = { false },
        val beforeConfirmation: () -> Unit = {},
        val exit: () -> Unit
    )

    fun requestExit() = requestAction(configuration.exit)

    /** Uses the same central Dosing confirmation for any action that discards the editor draft. */
    fun requestAction(action: () -> Unit) {
        if (configuration.isExitBlocked()) {
            Unit
        } else if (!configuration.hasUnsavedChanges()) {
            action()
        } else if (pendingAction == null) {
            configuration.beforeConfirmation()
            pendingAction = action
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
                        requestKey = configuration.requestKey,
                        actionId = configuration.actionId
                    )
                )
            )
        }
    }

    private fun handleResult(result: String?, resultActionId: String?) {
        if (resultActionId != configuration.actionId) return
        val action = pendingAction
        pendingAction = null
        if (result == ConfirmDialogFragment.RESULT_CONFIRM && !configuration.isExitBlocked()) {
            action?.invoke()
        }
    }

    companion object {
        fun attach(
            fragment: Fragment,
            configuration: Configuration
        ): UnsavedChangesExitGuard {
            val guard = UnsavedChangesExitGuard(
                fragment = fragment,
                configuration = configuration
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
