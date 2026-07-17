package com.aqua.aqualight.ui.common.permission

import com.aqua.aqualight.R
import com.aqua.aqualight.platform.permissions.AppCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityPermissionUiSpecResolverTest {

    @Test
    fun everyCapabilityHasOneExplicitDistinctCommercialIcon() {
        val icons = AppCapability.entries.map { capability ->
            CapabilityPermissionUiSpecResolver.resolve(
                capability = capability,
                mode = CapabilityPermissionBottomSheet.Mode.RATIONALE
            ).iconRes
        }

        assertEquals(AppCapability.entries.size, icons.toSet().size)
        assertTrue(icons.all { iconRes -> iconRes != 0 })
    }

    @Test
    fun rationaleUsesAllowActionWithoutBlockedBadge() {
        AppCapability.entries.forEach { capability ->
            val spec = CapabilityPermissionUiSpecResolver.resolve(
                capability = capability,
                mode = CapabilityPermissionBottomSheet.Mode.RATIONALE
            )

            assertEquals(R.string.permission_sheet_allow, spec.primaryActionRes)
            assertNull(spec.statusBadgeRes)
            assertTrue(spec.titleRes != 0)
            assertTrue(spec.messageRes != 0)
        }
    }

    @Test
    fun settingsModeUsesOneSharedBlockedBadgeAndOpenSettingsAction() {
        AppCapability.entries.forEach { capability ->
            val spec = CapabilityPermissionUiSpecResolver.resolve(
                capability = capability,
                mode = CapabilityPermissionBottomSheet.Mode.OPEN_SETTINGS
            )

            assertEquals(R.string.permission_sheet_open_settings, spec.primaryActionRes)
            assertEquals(R.drawable.ic_permission_blocked_badge, spec.statusBadgeRes)
            assertTrue(spec.titleRes != 0)
            assertTrue(spec.messageRes != 0)
        }
    }
}
