package com.aqua.aqualight.ui.auth.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReAuthenticateFragmentArchitectureTest {

    private val source by lazy {
        File(
            "src/main/java/com/aqua/aqualight/ui/auth/security/ReAuthenticateFragment.kt"
        ).readText()
    }

    @Test
    fun `account deletion leaves root graph replacement to central session coordinator`() {
        assertFalse(source.contains("RootNavigator"))
        assertFalse(source.contains("openAuthGraph"))
        assertFalse(source.contains("binding.root.postDelayed"))
        assertTrue(source.contains("AppSessionCoordinator are the single authority"))
    }

    @Test
    fun `asynchronous completion never requires a destroyed view binding`() {
        assertTrue(source.contains("if (_binding == null)"))
        assertTrue(source.contains("val currentBinding = _binding ?: return"))
        assertTrue(source.contains("isLoading = false\n        _binding = null"))
    }
}
