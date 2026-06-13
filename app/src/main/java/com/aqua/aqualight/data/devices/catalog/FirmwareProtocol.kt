package com.aqua.aqualight.data.devices.catalog

enum class FirmwareProtocol {
    /**
     * Eski geliştirme protokolü. Yeni ticari cihazlarda kullanılmayacak.
     * Geçiş tamamlanana kadar compile köprüsü olarak durur.
     */
    LEGACY_GET_SET,

    /**
     * Aqua commercial device identity/discovery contract v1.
     */
    AQUA_V1,

    /**
     * Eski isimlendirme. Yeni kayıtlar AQUA_V1 kullanmalı.
     */
    NATIVE_V1
}
