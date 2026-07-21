package com.aqua.aqualight.ui.auth.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReAuthenticateAccountDeletionNavigationTest {

    private val repositoryRoot = locateRepositoryRoot()
    private val fragmentSource = source(
        "app/src/main/java/com/aqua/aqualight/ui/auth/security/" +
            "ReAuthenticateFragment.kt"
    )

    @Test
    fun successfulDeletionDefersRootNavigationToSessionCoordinator() {
        val deleteFunction = fragmentSource.substringBetween(
            "private fun deleteAccount() {",
            "private fun renderAccountDeleteFailure("
        )
        val coordinatorTest = source(
            "app/src/test/java/com/aqua/aqualight/data/auth/" +
                "AppSessionCoordinatorTest.kt"
        )

        assertTrue(deleteFunction.contains("accountSecurityOperations.deleteCurrentAccount()"))
        assertTrue(deleteFunction.contains("AppSessionCoordinator"))
        assertTrue(
            coordinatorTest.contains(
                "logoutTransitionsExistingGraphAuthorityToUnauthenticated"
            )
        )

        listOf(
            "binding.",
            "RootNavigator",
            "navigateToLogin",
            "postDelayed",
            "findNavController"
        ).forEach { forbidden ->
            assertFalse(
                "Successful account deletion must not perform Fragment-owned root navigation: " +
                    forbidden,
                deleteFunction.contains(forbidden)
            )
        }
        assertFalse(fragmentSource.contains("ACTION_NAVIGATE_LOGIN"))
        assertFalse(fragmentSource.contains("private fun navigateToLogin()"))
    }

    @Test
    fun asynchronousDeleteFailureAndViewDestructionAreBindingSafe() {
        val failureRenderer = fragmentSource.substringBetween(
            "private fun renderAccountDeleteFailure(",
            "private fun getAccountDeleteFailureMessage("
        )
        val loadingRenderer = fragmentSource.substringBetween(
            "private fun setLoadingState(",
            "private fun shakeView("
        )
        val destroyView = fragmentSource.substringBetween(
            "override fun onDestroyView() {",
            "super.onDestroyView()"
        )

        assertTrue(failureRenderer.contains("_binding ?: return"))
        assertTrue(failureRenderer.contains("val currentContext = context ?: return"))
        assertTrue(loadingRenderer.contains("val currentBinding = _binding ?: return"))
        assertTrue(destroyView.contains("setFragmentGlobalLoading(false)"))
        assertTrue(destroyView.indexOf("setFragmentGlobalLoading(false)") < destroyView.indexOf("_binding = null"))
    }

    private fun source(relativePath: String): String =
        File(repositoryRoot, relativePath).readText()

    private fun String.substringBetween(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing source marker: $start" }
        val endIndex = indexOf(end, startIndex + start.length)
        require(endIndex > startIndex) { "Missing source marker: $end" }
        return substring(startIndex, endIndex)
    }

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
