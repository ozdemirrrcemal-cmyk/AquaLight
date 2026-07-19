package com.aqua.aqualight.ui.tabs.settings

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceStatusOperations
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.OwnerDeviceStatusSnapshot
import com.aqua.aqualight.application.user.UserAddressInput
import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.application.user.UserProfileSnapshot
import com.aqua.aqualight.ui.common.text.AquaUiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelBoundaryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `settings combines profile with application device overview`() {
        val deviceOperations = FakeDeviceStatusOperations(
            listOf(
                status("online", OwnerDeviceAvailability.REACHABLE),
                status("offline", OwnerDeviceAvailability.UNREACHABLE)
            )
        )
        val viewModel = SettingsViewModel(
            userProfileOperations = FakeUserProfileOperations(),
            deviceStatusOperations = deviceOperations
        )

        val state = viewModel.uiState.value

        assertEquals(1, deviceOperations.startCalls)
        assertEquals("Commercial User", state.username)
        assertEquals("commercial@aqualight.invalid", state.email)
        assertEquals(
            AquaUiText.Plural(R.plurals.settings_online_devices_count, 1),
            state.deviceOverview.activeDeviceCountText
        )
        assertTrue(state.deviceOverview.hasOnlineDevices)
    }

    private fun status(
        uid: String,
        availability: OwnerDeviceAvailability
    ) = OwnerDeviceStatusSnapshot(
        deviceUid = uid,
        displayName = uid,
        serialText = uid,
        family = OwnerDeviceFamily.UNKNOWN,
        availability = availability
    )

    private class FakeDeviceStatusOperations(
        initialStatuses: List<OwnerDeviceStatusSnapshot>
    ) : DeviceStatusOperations {
        override val statuses = MutableStateFlow(initialStatuses)
        var startCalls = 0

        override fun start(scope: CoroutineScope): Job {
            startCalls += 1
            return Job().apply { complete() }
        }
    }

    private class FakeUserProfileOperations : UserProfileOperations {
        override val profile: Flow<UserProfileSnapshot> = MutableStateFlow(
            UserProfileSnapshot(
                username = "Commercial User",
                email = "commercial@aqualight.invalid"
            )
        )

        override suspend fun updateProfilePhoto(photoUri: String) = Unit
        override suspend fun updateUsername(username: String) = Unit
        override suspend fun saveAddress(address: UserAddressInput) = Unit
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
