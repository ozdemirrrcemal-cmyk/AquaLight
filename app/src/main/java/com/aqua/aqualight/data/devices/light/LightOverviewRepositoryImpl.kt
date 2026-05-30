package com.aqua.aqualight.data.devices.light

import com.aqua.aqualight.data.devices.light.model.LightOverviewSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LightOverviewRepositoryImpl : LightOverviewRepository {

    private val lock = Any()

    private val deviceStates =
        mutableMapOf<Long, MutableStateFlow<LightOverviewSnapshot>>()

    override fun observeOverview(
        deviceId: Long
    ): StateFlow<LightOverviewSnapshot> {
        return getDeviceStateFlow(
            deviceId = deviceId
        )
    }

    override fun refresh(
        deviceId: Long
    ) {
        val stateFlow =
            getDeviceStateFlow(
                deviceId = deviceId
            )

        /*
         * Şu an ESP32 bağlantısı yok.
         * Bu yüzden sahte veri basmıyoruz.
         * Ekran loading / waiting state göstermeye devam eder.
         *
         * ESP32 entegrasyonunda burada:
         * 1. cache/DataStore okunacak
         * 2. ESP32 status/program/channel verisi çekilecek
         * 3. LightOverviewSnapshot güncellenecek
         */
        stateFlow.value =
            LightOverviewSnapshot.loading()
    }

    override fun setProgramEnabled(
        deviceId: Long,
        enabled: Boolean
    ) {
        val stateFlow =
            getDeviceStateFlow(
                deviceId = deviceId
            )

        val current =
            stateFlow.value

        /*
         * Şu an ESP32 yok.
         * Switch komutunu fake veriyle başarılı göstermiyoruz.
         * Sadece UI tarafında komut gönderildi mesajı gösteriliyor.
         */
        stateFlow.value =
            current.copy(
                isLoading = current.isLoading,
                isProgramEnabled = current.isProgramEnabled
            )
    }

    override fun applyTemporaryScene(
        deviceId: Long,
        sceneName: String,
        outputPercent: Int,
        durationLabel: String,
        resumeLabel: String
    ) {
        val stateFlow =
            getDeviceStateFlow(
                deviceId = deviceId
            )

        val current =
            stateFlow.value

        /*
         * Önemli:
         * Burada outputPercent'i ekrana basmıyoruz.
         * Çünkü %45 / %55 / %100 gibi değerler ESP32 onayı olmadan
         * production mantığında gerçek kabul edilmemeli.
         *
         * ESP32 entegrasyonunda temporary scene komutu gönderilecek,
         * cihazdan yeni snapshot gelince UI kendiliğinden güncellenecek.
         */
        stateFlow.value =
            current.copy(
                isLoading = current.isLoading,
                programTitle = current.programTitle,
                programSubtitle = current.programSubtitle,
                modeLabel = current.modeLabel
            )
    }

    override fun restoreAutoProgram(
        deviceId: Long
    ) {
        val stateFlow =
            getDeviceStateFlow(
                deviceId = deviceId
            )

        val current =
            stateFlow.value

        /*
         * ESP32 yokken fake auto state'e dönmüyoruz.
         * Gerçek cihazdan veri gelene kadar mevcut/loading state korunur.
         */
        stateFlow.value =
            current.copy(
                isLoading = current.isLoading
            )
    }

    private fun getDeviceStateFlow(
        deviceId: Long
    ): MutableStateFlow<LightOverviewSnapshot> {
        return synchronized(lock) {
            deviceStates.getOrPut(
                key = deviceId
            ) {
                MutableStateFlow(
                    LightOverviewSnapshot.loading()
                )
            }
        }
    }
}