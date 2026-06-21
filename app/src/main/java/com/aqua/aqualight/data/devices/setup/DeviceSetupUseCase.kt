package com.aqua.aqualight.data.devices.setup

import android.content.Context
import com.aqua.aqualight.data.devices.DeviceIdentityMatcher
import com.aqua.aqualight.data.devices.DeviceStoreWriter
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.discovery.DeviceDiscoveryService
import com.aqua.aqualight.data.devices.discovery.DeviceScanReason
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class DeviceSetupUseCase(
    context: Context,
    private val wifiConnector: DeviceSetupWifiConnector = DeviceSetupWifiConnector(context),
    private val setupClient: AquaDeviceSetupClient = AquaDeviceSetupClient(),
    private val homeWifiWaiter: HomeWifiConnectionWaiter = HomeWifiConnectionWaiter(context),
    private val deviceStoreWriter: DeviceStoreWriter = DeviceStoreWriter(
        DevicesDataStoreManager.create(context.applicationContext)
    )
) {

    private val appContext = context.applicationContext

    private var setupConnection: DeviceSetupWifiConnector.SetupConnection? = null
    private var setupPairingToken: AquaDeviceSetupClient.DeviceApiToken? = null

    suspend fun scanHomeWifiNetworks(
        target: DeviceSetupTarget,
        onProgress: (DeviceSetupProgress) -> Unit
    ): List<AquaDeviceSetupClient.HomeWifiNetwork> {
        onProgress(DeviceSetupProgress.CONNECTING_TO_SETUP_NETWORK)

        val connection = getOrCreateSetupConnection(
            setupSsid = target.setupSsid
        )

        val apiToken = pairSetupDevice(
            connection = connection
        )

        onProgress(DeviceSetupProgress.SCANNING_HOME_NETWORKS)

        return setupClient.scanHomeWifiNetworks(
            network = connection.network,
            deviceApiToken = apiToken.token
        )
    }

    suspend fun runSetupFlow(
        target: DeviceSetupTarget,
        credentials: HomeWifiCredentials,
        onProgress: (DeviceSetupProgress) -> Unit
    ): Long {
        val connection = getOrCreateSetupConnection(
            setupSsid = target.setupSsid
        )

        val apiToken = pairSetupDevice(
            connection = connection
        )

        onProgress(DeviceSetupProgress.SENDING_HOME_WIFI_CREDENTIALS)

        val setupResult = setupClient.sendHomeWifiCredentials(
            network = connection.network,
            setupSsid = target.setupSsid,
            setupPassword = SETUP_AP_PASSWORD,
            homeSsid = credentials.ssid,
            homePassword = credentials.password,
            disableSetupAccessPoint = false,
            deviceApiToken = apiToken.token
        )

        if (!setupResult.success) {
            throw DeviceSetupFlowException(
                error = DeviceSetupFlowError.NOT_ACCEPTED,
                detailMessage = setupResult.errorMessage
            )
        }

        if (
            !waitForDeviceClientConnection(
                connection = connection,
                firstResponseBody = setupResult.responseBody,
                deviceApiToken = apiToken.token,
                onProgress = onProgress
            )
        ) {
            throw DeviceSetupFlowException(
                error = DeviceSetupFlowError.CONNECTION_FAILED
            )
        }

        onProgress(DeviceSetupProgress.CLOSING_SETUP_NETWORK)

        closeSetupAccessPoint(
            target = target,
            connection = connection,
            credentials = credentials,
            deviceApiToken = apiToken.token
        )

        closeSetupConnection()

        onProgress(DeviceSetupProgress.WAITING_PHONE_HOME_WIFI)

        val phoneReturnedToHomeWifi = homeWifiWaiter.waitUntilHomeWifiReady(
            expectedSsid = credentials.ssid,
            setupSsid = target.setupSsid,
            timeoutMs = 75_000L
        )

        if (!phoneReturnedToHomeWifi) {
            throw DeviceSetupFlowException(
                error = DeviceSetupFlowError.PHONE_NOT_HOME_WIFI,
                detailValue = credentials.ssid
            )
        }

        onProgress(DeviceSetupProgress.FINDING_DEVICE_ON_HOME_NETWORK)

        delay(7_000L)

        val discoveredDevice = waitForDeviceOnHomeNetwork(
            target = target
        ) ?: throw DeviceSetupFlowException(
            error = DeviceSetupFlowError.DEVICE_NOT_FOUND
        )

        val savedDeviceId = deviceStoreWriter.saveDiscoveredDevice(
            device = discoveredDevice,
            deviceApiToken = apiToken.token
        )

        onProgress(DeviceSetupProgress.SUCCESS)

        delay(700L)

        return savedDeviceId
    }

    fun close() {
        closeSetupConnection()
        setupPairingToken = null
    }

    private suspend fun pairSetupDevice(
        connection: DeviceSetupWifiConnector.SetupConnection
    ): AquaDeviceSetupClient.DeviceApiToken {
        setupPairingToken?.let { token ->
            return token
        }

        return try {
            setupClient.pairForSetup(
                network = connection.network
            ).also { token ->
                setupPairingToken = token
            }
        } catch (exception: AquaDeviceSetupClient.DeviceSecurityException) {
            throw DeviceSetupFlowException(
                error = DeviceSetupFlowError.NOT_ACCEPTED,
                detailMessage = exception.message
            )
        }
    }

    private suspend fun getOrCreateSetupConnection(
        setupSsid: String
    ): DeviceSetupWifiConnector.SetupConnection {
        setupConnection?.let { connection ->
            return connection
        }

        val connection = wifiConnector.connectToSetupNetwork(
            ssid = setupSsid,
            password = SETUP_AP_PASSWORD,
            timeoutMs = 30_000L
        )

        setupConnection = connection
        return connection
    }

    private suspend fun waitForDeviceClientConnection(
        connection: DeviceSetupWifiConnector.SetupConnection,
        firstResponseBody: String?,
        deviceApiToken: String,
        onProgress: (DeviceSetupProgress) -> Unit
    ): Boolean {
        val firstStatus = setupClient.parseDeviceWifiStatus(
            responseText = firstResponseBody
        )

        if (firstStatus.connected) {
            return true
        }

        onProgress(DeviceSetupProgress.CHECKING_DEVICE_CONNECTION)

        repeat(15) {
            val status = try {
                setupClient.readDeviceWifiStatus(
                    network = connection.network,
                    deviceApiToken = deviceApiToken
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                null
            }

            if (status?.connected == true) {
                return true
            }

            onProgress(DeviceSetupProgress.JOINING_HOME_WIFI)

            delay(3_000L)
        }

        return false
    }

    private suspend fun closeSetupAccessPoint(
        target: DeviceSetupTarget,
        connection: DeviceSetupWifiConnector.SetupConnection,
        credentials: HomeWifiCredentials,
        deviceApiToken: String
    ) {
        val closeApResult = setupClient.sendHomeWifiCredentials(
            network = connection.network,
            setupSsid = target.setupSsid,
            setupPassword = SETUP_AP_PASSWORD,
            homeSsid = credentials.ssid,
            homePassword = credentials.password,
            disableSetupAccessPoint = true,
            deviceApiToken = deviceApiToken
        )

        if (!closeApResult.success) {
            throw DeviceSetupFlowException(
                error = DeviceSetupFlowError.CLOSE_SETUP_AP_FAILED,
                detailMessage = closeApResult.errorMessage
            )
        }
    }

    private suspend fun waitForDeviceOnHomeNetwork(
        target: DeviceSetupTarget
    ): DiscoveredAquaDevice? {
        repeat(20) {
            val result = DeviceDiscoveryService.scan(
                context = appContext,
                timeoutMs = 3_000L,
                reason = DeviceScanReason.MANUAL_SCAN
            )

            val match = result.devices.firstOrNull { device ->
                isExpectedDevice(
                    target = target,
                    device = device
                )
            }

            if (match != null) {
                return match
            }

            delay(3_000L)
        }

        return null
    }

    private fun isExpectedDevice(
        target: DeviceSetupTarget,
        device: DiscoveredAquaDevice
    ): Boolean {
        val productMatches = target.expectedProductId.isBlank() ||
            device.productId.equals(
                other = target.expectedProductId,
                ignoreCase = true
            )

        if (!productMatches) {
            return false
        }

        val categoryMatches = target.expectedCategory == AquaDeviceCategory.UNKNOWN ||
            device.category == target.expectedCategory

        if (!categoryMatches) {
            return false
        }

        val setupCodeMatches = target.expectedSetupCode.isBlank() ||
            device.setupCode.equals(
                other = target.expectedSetupCode,
                ignoreCase = true
            )

        if (!setupCodeMatches) {
            return false
        }

        if (target.setupShortId.isBlank()) {
            return true
        }

        return DeviceIdentityMatcher.matchesSetupShortId(
            discoveredDevice = device,
            setupShortId = target.setupShortId
        )
    }

    private fun closeSetupConnection() {
        setupConnection?.close()
        setupConnection = null
    }

    private companion object {
        const val SETUP_AP_PASSWORD = "adminadmin"
    }
}

data class HomeWifiCredentials(
    val ssid: String,
    val password: String
)

enum class DeviceSetupProgress {
    CONNECTING_TO_SETUP_NETWORK,
    SCANNING_HOME_NETWORKS,
    SENDING_HOME_WIFI_CREDENTIALS,
    CHECKING_DEVICE_CONNECTION,
    JOINING_HOME_WIFI,
    CLOSING_SETUP_NETWORK,
    WAITING_PHONE_HOME_WIFI,
    FINDING_DEVICE_ON_HOME_NETWORK,
    SUCCESS
}

enum class DeviceSetupFlowError {
    NOT_ACCEPTED,
    CONNECTION_FAILED,
    CLOSE_SETUP_AP_FAILED,
    PHONE_NOT_HOME_WIFI,
    DEVICE_NOT_FOUND
}

class DeviceSetupFlowException(
    val error: DeviceSetupFlowError,
    val detailValue: String? = null,
    detailMessage: String? = null
) : Exception(detailMessage)
