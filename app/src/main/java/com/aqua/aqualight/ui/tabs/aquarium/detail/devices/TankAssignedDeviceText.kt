package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R

object TankAssignedDeviceText {

    @StringRes val UNKNOWN_AQUARIUM = R.string.aquarium_device_unknown_aquarium
    @StringRes val OFFLINE_LABEL = R.string.aquarium_device_label_offline
    @StringRes val MANUAL_LABEL = R.string.aquarium_device_label_manual_mode
    @StringRes val SCENE_LABEL = R.string.aquarium_device_label_scene_active
    @StringRes val MOONLIGHT_LABEL = R.string.aquarium_device_label_moonlight
    @StringRes val SYNCING_LABEL = R.string.aquarium_device_label_syncing
    @StringRes val NO_ACTIVE_PROGRAM_LABEL = R.string.aquarium_device_label_no_active_program
    @StringRes val ACTIVE_PROGRAM_LABEL = R.string.aquarium_device_label_active_program
    @StringRes val SCHEDULED_LABEL = R.string.aquarium_device_label_scheduled

    @StringRes val NO_LIVE_DATA_TITLE = R.string.aquarium_device_no_live_data_title
    @StringRes val MANUAL_CONTROL_TITLE = R.string.aquarium_device_manual_control_title
    @StringRes val SCENE_MODE_TITLE = R.string.aquarium_device_scene_mode_title
    @StringRes val MOONLIGHT_MODE_TITLE = R.string.aquarium_device_moonlight_mode_title
    @StringRes val WAITING_FOR_TIME_TITLE = R.string.aquarium_device_waiting_for_time_title
    @StringRes val PROGRAM_NOT_SET_TITLE = R.string.aquarium_device_program_not_set_title

    @StringRes val MANUAL_LEFT_TEXT = R.string.aquarium_device_manual_left_text
    @StringRes val SCENE_LEFT_TEXT = R.string.aquarium_device_scene_left_text
    @StringRes val RESUME_RIGHT_TEXT = R.string.aquarium_device_resume_right_text
    @StringRes val EMPTY_TIME_TEXT = R.string.aquarium_device_empty_time_text

    fun resolve(
        context: Context,
        @StringRes resId: Int
    ): String {
        return context.getString(
            resId
        )
    }
}
