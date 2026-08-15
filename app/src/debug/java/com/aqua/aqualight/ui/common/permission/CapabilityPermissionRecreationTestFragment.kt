package com.aqua.aqualight.ui.common.permission

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.aqua.aqualight.platform.permissions.AppCapability

/** Debug-only host for instrumentation coverage of permission continuation recreation. */
class CapabilityPermissionRecreationTestFragment : Fragment() {
    private val coordinator = CapabilityPermissionCoordinator(
        fragment = this,
        instanceKey = INSTANCE_KEY,
        onGranted = ::recordGrantedAction
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FrameLayout(requireContext())

    fun showAppSettingsExplanation() {
        coordinator.openSettingsFor(
            capability = AppCapability.NOTIFICATIONS,
            actionToken = APP_SETTINGS_ACTION
        )
    }

    fun showChannelSettingsExplanation() {
        coordinator.openNotificationChannelSettingsFor(
            channelId = DEVICE_ALERTS_CHANNEL_ID,
            actionToken = CHANNEL_SETTINGS_ACTION
        )
    }

    private fun recordGrantedAction(actionToken: String) {
        grantedActions += actionToken
    }

    companion object {
        const val APP_SETTINGS_ACTION = "test-app-notification-settings"
        const val CHANNEL_SETTINGS_ACTION = "test-channel-notification-settings"
        const val DEVICE_ALERTS_CHANNEL_ID = "device_alerts"

        private const val INSTANCE_KEY = "permission-recreation-test"
        private val grantedActions = mutableListOf<String>()

        fun resetRecordedActions() {
            grantedActions.clear()
        }

        fun recordedActions(): List<String> = grantedActions.toList()
    }
}
