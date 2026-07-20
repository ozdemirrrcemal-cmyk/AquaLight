package com.aqua.aqualight.i18n

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectDateFormatUsageArchitectureTest {

    private val repositoryRoot: File = locateRepositoryRoot()
    private val productionJava = File(repositoryRoot, "app/src/main/java")

    @Test
    fun simpleDateFormatIsCentralizedInsideLocaleFormatter() {
        val owners = productionJava.walkTopDown()
            .filter(File::isFile)
            .filter { file -> file.extension == "kt" }
            .filter { file -> file.readText().contains("SimpleDateFormat(") }
            .mapTo(linkedSetOf()) { file ->
                file.relativeTo(repositoryRoot).invariantSeparatorsPath
            }

        assertEquals(
            setOf(
                "app/src/main/java/com/aqua/aqualight/i18n/LocaleFormatter.kt"
            ),
            owners
        )
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
