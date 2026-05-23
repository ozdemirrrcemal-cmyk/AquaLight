package com.aqua.aqualight.data.devices.catalog.light

data class LightChannelDefinition(
    val id: String,
    val displayName: String,
    val color: LightChannelColor,
    val order: Int,

    /**
     * ESP32 LPWMChanelLED Data index.
     *
     * Kullanıcı bunu değiştirmez.
     * Ürün modeline göre uygulama sabit bilir.
     */
    val firmwareChannelIndex: Int,

    val minPercent: Int = 0,
    val maxPercent: Int = 100,

    /**
     * Ticari üründe kanal ekleme/silme/GPIO değiştirme yok.
     */
    val isUserEditable: Boolean = false
) {
    init {
        require(id.isNotBlank()) {
            "Light channel id cannot be blank."
        }

        require(displayName.isNotBlank()) {
            "Light channel displayName cannot be blank."
        }

        require(order >= 0) {
            "Light channel order must be >= 0."
        }

        require(firmwareChannelIndex >= 0) {
            "Firmware channel index must be >= 0."
        }

        require(minPercent in 0..100) {
            "minPercent must be between 0 and 100."
        }

        require(maxPercent in 0..100) {
            "maxPercent must be between 0 and 100."
        }

        require(minPercent <= maxPercent) {
            "minPercent cannot be greater than maxPercent."
        }
    }
}