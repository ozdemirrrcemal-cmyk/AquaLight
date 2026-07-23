package com.aqua.aqualight.ui.auth

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthPrivacyNoticeArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun privacyNoticeIsSingleLineAndDoesNotMoveLoginButtons() {
        val layout = source("app/src/main/res/layout/fragment_login.xml")
        val styles = source("app/src/main/res/values/styles.xml")

        assertTrue(tag(layout, "tvPrivacyNotice").contains(
            """app:layout_constraintBottom_toTopOf="@id/btnGoogleLogin""""
        ))
        assertTrue(styles.contains("""name="Widget.Aqua.Auth.LegalLink""""))
        assertTrue(styles.contains("""<item name="android:maxLines">1</item>"""))

        assertTrue(tag(layout, "btnGoogleLogin").contains(
            """app:layout_constraintBottom_toTopOf="@+id/btnSignIn""""
        ))
        assertTrue(tag(layout, "btnSignIn").contains(
            """app:layout_constraintBottom_toTopOf="@+id/btnRegister""""
        ))
        assertTrue(tag(layout, "btnRegister").contains(
            """app:layout_constraintBottom_toBottomOf="parent""""
        ))
    }

    @Test
    fun privacyNoticeFollowsRepeatedPasswordWithoutMovingRegisterButtons() {
        val layout = source("app/src/main/res/layout/fragment_register.xml")

        assertTrue(tag(layout, "passwordRepeatContainer").contains(
            """app:layout_constraintBottom_toTopOf="@id/tvPrivacyNotice""""
        ))
        assertTrue(tag(layout, "tvPrivacyNotice").contains(
            """app:layout_constraintTop_toBottomOf="@id/passwordRepeatContainer""""
        ))
        assertTrue(tag(layout, "tvPrivacyNotice").contains(
            """app:layout_constraintBottom_toTopOf="@id/btnRegister""""
        ))

        assertTrue(tag(layout, "btnRegister").contains(
            """app:layout_constraintBottom_toTopOf="@id/btnReturnToSignIn""""
        ))
        assertTrue(tag(layout, "btnReturnToSignIn").contains(
            """app:layout_constraintBottom_toBottomOf="parent""""
        ))
    }

    @Test
    fun bothPreAuthLinksOpenThePackagedPrivacyNotice() {
        val navigation = source("app/src/main/res/navigation/nav_graph_auth.xml")
        val login = source(
            "app/src/main/java/com/aqua/aqualight/ui/auth/LoginFragment.kt"
        )
        val register = source(
            "app/src/main/java/com/aqua/aqualight/ui/auth/RegisterFragment.kt"
        )

        assertTrue(navigation.contains("action_loginFragment_to_privacyFragment"))
        assertTrue(navigation.contains("action_registerFragment_to_privacyFragment"))
        assertTrue(navigation.contains(
            """android:name="com.aqua.aqualight.ui.tabs.settings.legal.PrivacyFragment""""
        ))
        assertTrue(login.contains("actionLoginFragmentToPrivacyFragment()"))
        assertTrue(register.contains("actionRegisterFragmentToPrivacyFragment()"))
    }

    private fun tag(
        xml: String,
        id: String
    ): String =
        Regex(
            """<[^>]+android:id="@\+id/$id"[^>]*>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(xml)?.value ?: error("Missing view: $id")

    private fun source(relativePath: String): String =
        File(repositoryRoot, relativePath).readText()

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
