package com.aqua.aqualight.data.devices.light

import com.aqua.aqualight.data.devices.light.model.LightOverviewSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LightOverviewRepositoryImpl : LightOverviewRepository {

    private val deviceFlows =
        mutableMapOf<Long, MutableStateFlow<LightOverviewSnapshot>>()

    private val lastAutoSnapshots =
        mutableMapOf<Long, LightOverviewSnapshot>()

    override fun observeOverview(
        deviceId: Long
    ): Flow<LightOverviewSnapshot> {
        return flowFor(deviceId).asStateFlow()
    }

    override suspend fun refresh(
        deviceId: Long
    ) {
        val flow = flowFor(deviceId)

        flow.value = flow.value.copy(
            isRefreshing = true,
            connectionLabel = if (flow.value.isOnline) {
                flow.value.connectionLabel.ifBlank {
                    "Online · 4-channel WRGB"
                }
            } else {
                "Syncing · WRGB"
            },
            healthLabel = if (flow.value.isOnline) {
                flow.value.healthLabel.ifBlank {
                    "Syncing device data"
                }
            } else {
                "Connecting"
            }
        )

        /*
         * TODO:
         * Burada gerçek ESP32 entegrasyonu bağlanacak.
         *
         * Doğru akış:
         * 1. Önce DataStore/cache son bilinen cihaz verisini verir.
         * 2. Sonra ESP32 HTTP/UDP üzerinden taze veri çekilir.
         * 3. Gelen veri LightOverviewSnapshot'a map edilir.
         * 4. flow.value = freshSnapshot yapılır.
         *
         * Şimdilik fake cihaz datası basmıyoruz.
         * Bu ticari uygulama için doğru: kullanıcıya sahte değer göstermiyoruz.
         */

        flow.value = flow.value.copy(
            isRefreshing = false,
            connectionLabel = if (flow.value.isOnline) {
                flow.value.connectionLabel.ifBlank {
                    "Online · 4-channel WRGB"
                }
            } else {
                "Connecting · WRGB"
            },
            healthLabel = if (flow.value.isOnline) {
                flow.value.healthLabel.ifBlank {
                    "Online"
                }
            } else {
                "Waiting for device data"
            }
        )
    }

    override suspend fun setProgramEnabled(
        deviceId: Long,
        enabled: Boolean
    ) {
        val flow = flowFor(deviceId)

        /*
         * TODO:
         * ESP32'ye program enable/disable komutu gönderilecek.
         * Komut başarılı olunca ESP32’den dönen gerçek state ile güncellenecek.
         */

        flow.value = flow.value.copy(
            isProgramEnabled = enabled
        )

        if (enabled) {
            lastAutoSnapshots[deviceId] = flow.value
        }
    }

    override suspend fun applyTemporaryScene(
        deviceId: Long,
        sceneName: String,
        outputPercent: Int,
        durationLabel: String,
        resumeLabel: String
    ) {
        val flow = flowFor(deviceId)

        lastAutoSnapshots[deviceId] = flow.value

        /*
         * TODO:
         * ESP32’ye temporary scene komutu gönderilecek.
         * Komut sonrası gerçek cihaz cevabı gelince flow.value tekrar gerçek veriyle güncellenecek.
         */

        flow.value = flow.value.copy(
            programTitle = "$sceneName Active",
            programSubtitle = "Temporary scene is running",
            modeLabel = "OVERRIDE",
            currentOutputPercent = outputPercent.coerceIn(0, 100),
            nowLabel = sceneName,
            nextLabel = resumeLabel,
            curveNowLabel = "Override · ${outputPercent.coerceIn(0, 100)}%"
        )
    }

    override suspend fun restoreAutoProgram(
        deviceId: Long
    ) {
        val flow = flowFor(deviceId)

        /*
         * TODO:
         * ESP32’ye restore auto komutu gönderilecek.
         * Cihazdan gerçek program state dönünce flow.value onunla güncellenecek.
         */

        val lastAutoSnapshot = lastAutoSnapshots[deviceId]

        flow.value = if (lastAutoSnapshot != null) {
            lastAutoSnapshot
        } else {
            flow.value.copy(
                programTitle = "Current Program",
                programSubtitle = "Waiting for device data",
                modeLabel = "SYNCING",
                currentOutputPercent = null,
                nowLabel = "",
                nextLabel = "",
                curveNowLabel = ""
            )
        }
    }

    private fun flowFor(
        deviceId: Long
    ): MutableStateFlow<LightOverviewSnapshot> {
        return deviceFlows.getOrPut(deviceId) {
            MutableStateFlow(
                LightOverviewSnapshot.loading(
                    deviceId = deviceId
                )
            )
        }
    }
}