package com.aqua.aqualight.ui.tabs.devices.detail

import android.graphics.Color

data class DeviceVisualSpec(
    val accentColor: Int,
    val accentDarkColor: Int,
    val cardBackgroundColor: Int,
    val cardStrokeColor: Int,
    val buttonColor: Int,
    val buttonTextColor: Int,
    val chartGridColor: Int,
    val chartTextColor: Int
)

object DeviceVisualSpecs {

    val Cooling = DeviceVisualSpec(
        accentColor = Color.parseColor("#5BA7B8"),
        accentDarkColor = Color.parseColor("#183544"),
        cardBackgroundColor = Color.parseColor("#0F1B2D"),
        cardStrokeColor = Color.parseColor("#2A5366"),
        buttonColor = Color.parseColor("#1F6E7A"),
        buttonTextColor = Color.WHITE,
        chartGridColor = Color.parseColor("#22364D"),
        chartTextColor = Color.parseColor("#AFC0D3")
    )

    val Light = DeviceVisualSpec(
        accentColor = Color.parseColor("#FFC857"),
        accentDarkColor = Color.parseColor("#3D3217"),
        cardBackgroundColor = Color.parseColor("#101D31"),
        cardStrokeColor = Color.parseColor("#7A6125"),
        buttonColor = Color.parseColor("#E0A928"),
        buttonTextColor = Color.WHITE,
        chartGridColor = Color.parseColor("#2F3A4A"),
        chartTextColor = Color.parseColor("#D7E1EF")
    )

    val Timer = DeviceVisualSpec(
        accentColor = Color.parseColor("#8B7CFF"),
        accentDarkColor = Color.parseColor("#29264A"),
        cardBackgroundColor = Color.parseColor("#101D31"),
        cardStrokeColor = Color.parseColor("#514AA0"),
        buttonColor = Color.parseColor("#6E63E8"),
        buttonTextColor = Color.WHITE,
        chartGridColor = Color.parseColor("#2A3158"),
        chartTextColor = Color.parseColor("#D7E1EF")
    )
}