package com.aqua.aqualight.ui.tabs.devices.detail.light.common

import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.LightDashboardMode

fun TextView.renderLightModeChip(
    mode: LightDashboardMode
) {
    text = mode.label

    setBackgroundResource(
        when (mode) {
            LightDashboardMode.AUTO -> {
                R.drawable.bg_light_mode_chip_auto
            }

            LightDashboardMode.MANUAL -> {
                R.drawable.bg_light_mode_chip_manual
            }

            LightDashboardMode.SCENE -> {
                R.drawable.bg_light_mode_chip_scene
            }

            LightDashboardMode.MOON -> {
                R.drawable.bg_light_mode_chip_moon
            }

            LightDashboardMode.WAIT -> {
                R.drawable.bg_light_mode_chip_wait
            }

            LightDashboardMode.IDLE -> {
                R.drawable.bg_light_mode_chip_idle
            }

            LightDashboardMode.SYNC -> {
                R.drawable.bg_light_mode_chip_sync
            }
        }
    )

    setTextColor(
        ContextCompat.getColor(
            context,
            when (mode) {
                LightDashboardMode.AUTO -> {
                    R.color.light_accent
                }

                LightDashboardMode.MANUAL -> {
                    R.color.light_gold
                }

                LightDashboardMode.SCENE -> {
                    R.color.light_gold
                }

                LightDashboardMode.MOON -> {
                    R.color.light_accent
                }

                LightDashboardMode.WAIT -> {
                    R.color.light_text_secondary
                }

                LightDashboardMode.IDLE -> {
                    R.color.light_text_tertiary
                }

                LightDashboardMode.SYNC -> {
                    R.color.light_accent_muted
                }
            }
        )
    )
}