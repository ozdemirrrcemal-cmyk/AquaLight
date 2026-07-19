package com.aqua.aqualight.i18n

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class DateOnlyArchitectureTest {

    private val repositoryRoot: File = locateRepositoryRoot()

    @Test
    fun calendarOnlyFieldsCannotReturnToTimestampStorage() {
        val protectedRoots = listOf(
            File(repositoryRoot, "app/src/main/java"),
            File(repositoryRoot, "app/src/main/proto"),
            File(repositoryRoot, "app/src/androidTest/java")
        )
        val forbiddenTokens = listOf(
            "setupDateMillis",
            "addedDateMillis",
            "setup_date_millis",
            "added_date_millis"
        )

        protectedRoots.forEach { root ->
            root.walkTopDown()
                .filter(File::isFile)
                .forEach { file ->
                    val source = file.readText()
                    forbiddenTokens.forEach { token ->
                        assertFalse(
                            "${file.relativeTo(repositoryRoot).invariantSeparatorsPath} " +
                                "must not store calendar-only values as timestamps: $token",
                            source.contains(token)
                        )
                    }
                }
        }
    }

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile

        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) {
                return candidate
            }
            candidate = candidate.parentFile
        }

        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
