package com.aqua.aqualight.base

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessUiStateRestorePolicyTest {

    @Test
    fun sameProcessConfigurationRecreationRestoresUiState() {
        assertTrue(
            ProcessUiStateRestorePolicy.canRestore(
                savedProcessToken = "process-a",
                currentProcessToken = "process-a"
            )
        )
    }

    @Test
    fun processDeathDropsStaleOwnerUiState() {
        assertFalse(
            ProcessUiStateRestorePolicy.canRestore(
                savedProcessToken = "old-process",
                currentProcessToken = "new-process"
            )
        )
    }

    @Test
    fun stateSavedByOlderBuildWithoutTokenIsNotRestored() {
        assertFalse(
            ProcessUiStateRestorePolicy.canRestore(
                savedProcessToken = null,
                currentProcessToken = "new-process"
            )
        )
    }

    @Test
    fun blankTokensNeverAuthorizeRestoration() {
        assertFalse(
            ProcessUiStateRestorePolicy.canRestore(
                savedProcessToken = "",
                currentProcessToken = ""
            )
        )
    }
}
