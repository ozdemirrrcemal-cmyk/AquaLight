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

    suspend fun scanHomeWifiNetworks(
        target: DeviceSetupTarget,
        onProgress: (DeviceSetupProgress) -> Unit
    ): List<AquaDeviceSetupClient.HomeWifiNetwork> {
        onProgress(DeviceSetupProgress.CONNECTING_TO_SETUP_NETWORK)

        val connection = getOrCreateSetupConnection(
            setupSsid = target.setupSsid
        )

        onProgress(DeviceSetupProgress.SCANNING_HOME_NETWORKS)

        return setupClient.scanHomeWifiNetworks(
            network = connection.network
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

        // Important onboarding order:
        // Do NOT pair before the home Wi-Fi credentials are accepted.
        // If pairing succeeds and any later Wi-Fi step fails, the device becomes
        // paired while the app has not saved the token yet. Every retry is then
        // blocked by the token gate and the UI can misleadingly show scan/password
        // errors. The firmware intentionally allows /api/v1/network/wifi while the
        // device is in setup/onboarding mode, so credentials go first, pairing goes
        // after the device has proven it can join the home network.
        onProgress(DeviceSetupProgress.SENDING_HOME_WIFI_CREDENTIALS)

        val setupResult = setupClient.sendHomeWifiCredentials(
            network = connection.network,
            setupSsid = target.setupSsid,
            setupPassword = SETUP_AP_PASSWORD,
            homeSsid = credentials.ssid,
            homePassword = credentials.password,
            disableSetupAccessPoint = false,
            apiToken = ""
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
                expectedSsid = credentials.ssid,
                firstResponseBody = setupResult.responseBody,
                onProgress = onProgress
            )
        ) {
            throw DeviceSetupFlowException(
                error = DeviceSetupFlowError.CONNECTION_FAILED
            )
        }

        val pairingResult = setupClient.pairDevice(
            network = connection.network,
            deviceUid = target.setupSsid,
            serialNumber = target.setupSsid,
            shortId = target.setupShortId
        )

        if (!pairingResult.success) {
            throw DeviceSetupFlowException(
                error = DeviceSetupFlowError.PAIRING_FAILED,
                detailMessage = pairingResult.errorMessage
            )
        }

        val apiToken = pairingResult.token

        onProgress(DeviceSetupProgress.CLOSING_SETUP_NETWORK)

        closeSetupAccessPoint(
            target = target,
            connection = connection,
            credentials = credentials,
            apiToken = apiToken
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
            apiToken = apiToken
        )

        onProgress(DeviceSetupProgress.SUCCESS)

        delay(700L)

        return savedDeviceId
    }

    fun close() {
        closeSetupConnection()
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
        expectedSsid: String,
        firstResponseBody: String?,
        onProgress: (DeviceSetupProgress) -> Unit
    ): Boolean {
        var credentialsAccepted = false

        val firstStatus = setupClient.parseDeviceWifiStatus(
            responseText = firstResponseBody
        )

        if (firstStatus.hasAcceptedCredentials(expectedSsid)) {
            credentialsAccepted = true
        }

        if (firstStatus.connected) {
            return true
        }

        onProgress(DeviceSetupProgress.CHECKING_DEVICE_CONNECTION)

        repeat(35) {
            val status = try {
                setupClient.readDeviceWifiStatus(
                    network = connection.network
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                null
            }

            if (status?.hasAcceptedCredentials(expectedSsid) == true) {
                credentialsAccepted = true
            }

            if (status?.connected == true) {
                return true
            }

            onProgress(DeviceSetupProgress.JOINING_HOME_WIFI)

            delay(3_000L)
        }

        if (!credentialsAccepted) {
            throw DeviceSetupFlowException(
                error = DeviceSetupFlowError.NOT_ACCEPTED,
                detailMessage = "Device did not persist the home Wi-Fi SSID/password. The setup request did not reach firmware or was rejected."
            )
        }

        return false
    }

    private suspend fun closeSetupAccessPoint(
        target: DeviceSetupTarget,
        connection: DeviceSetupWifiConnector.SetupConnection,
        credentials: HomeWifiCredentials,
        apiToken: String
    ) {
        val closeApResult = setupClient.sendHomeWifiCredentials(
            network = connection.network,
            setupSsid = target.setupSsid,
            setupPassword = SETUP_AP_PASSWORD,
            homeSsid = credentials.ssid,
            homePassword = credentials.password,
            disableSetupAccessPoint = true,
            apiToken = apiToken
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
    PAIRING_FAILED,
    PHONE_NOT_HOME_WIFI,
    DEVICE_NOT_FOUND
}

class DeviceSetupFlowException(
    val error: DeviceSetupFlowError,
    val detailValue: String? = null,
    detailMessage: String? = null
) : Exception(detailMessage)
