package com.aqua.aqualight.i18n

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class UserVisibleTextResourceGuardTest {

    @Test
    fun duplicateTankNameCopyIsOwnedByMatchedTankResourceFiles() {
        val root = projectRoot()
        val englishFile = root.resolve(
            "app/src/main/res/values/tank_duplicate_name_strings.xml"
        )
        val turkishFile = root.resolve(
            "app/src/main/res/values-tr/tank_duplicate_name_strings.xml"
        )
        val expectedValues = linkedMapOf(
            "aquarium_duplicate_name_suffix" to linkedMapOf(
                englishFile to "Copy",
                turkishFile to "Kopya"
            ),
            "aquarium_duplicate_name_numbered_suffix" to linkedMapOf(
                englishFile to "Copy %1\$d",
                turkishFile to "Kopya %1\$d"
            )
        )

        expectedValues.forEach { (resourceName, expectedByFile) ->
            expectedByFile.forEach { (expectedFile, expectedValue) ->
                assertTrue(
                    "Missing ${relative(root, expectedFile)}",
                    Files.isRegularFile(expectedFile)
                )

                val definitions = resourceDefinitions(
                    directory = expectedFile.parent,
                    resourceName = resourceName
                )
                assertEquals(
                    "string/$resourceName must be owned only by ${relative(root, expectedFile)}",
                    listOf(expectedFile),
                    definitions
                )
                assertEquals(
                    "Unexpected value for string/$resourceName in ${relative(root, expectedFile)}",
                    expectedValue,
                    resourceValue(expectedFile, resourceName)
                )
            }
        }
    }

    @Test
    fun duplicateTankNamePlaceholdersMatchBetweenEnglishAndTurkish() {
        val root = projectRoot()
        val englishFile = root.resolve(
            "app/src/main/res/values/tank_duplicate_name_strings.xml"
        )
        val turkishFile = root.resolve(
            "app/src/main/res/values-tr/tank_duplicate_name_strings.xml"
        )
        val resourceName = "aquarium_duplicate_name_numbered_suffix"

        assertEquals(
            placeholderSignature(requireNotNull(resourceValue(englishFile, resourceName))),
            placeholderSignature(requireNotNull(resourceValue(turkishFile, resourceName)))
        )
        assertEquals(
            listOf("1:d"),
            placeholderSignature(requireNotNull(resourceValue(englishFile, resourceName)))
        )
    }

    @Test
    fun duplicateTankNameGenerationUsesLocaleAwareStringResourcesInsteadOfLanguageLiterals() {
        val root = projectRoot()
        val sourceFile = root.resolve(
            "app/src/main/java/com/aqua/aqualight/data/aquarium/store/" +
                "AquariumTankDataStoreManager.kt"
        )
        val source = String(
            Files.readAllBytes(sourceFile),
            StandardCharsets.UTF_8
        )

        assertTrue(source.contains("import com.aqua.aqualight.R"))
        assertTrue(source.contains("import androidx.core.content.ContextCompat"))
        assertTrue(source.contains("ContextCompat.getContextForLanguage(context)"))
        assertTrue(source.contains("R.string.aquarium_duplicate_name_suffix"))
        assertTrue(source.contains("R.string.aquarium_duplicate_name_numbered_suffix"))
        assertTrue(source.contains("localizedContext.getString"))

        val forbiddenLiteral = Regex("\"[^\"\\n]*(?:Copy|Kopya)[^\"\\n]*\"")
            .find(source)
            ?.value
        assertFalse(
            "Duplicate-name product copy must come from matched tank resources, found " +
                forbiddenLiteral,
            forbiddenLiteral != null
        )
    }

    @Test
    fun productionUserVisibleLiteralsAtDefiniteUiBoundariesMustUseResources() {
        val root = projectRoot()
        val sourceRoot = root.resolve("app/src/main/java")
        val violations = sourceRoot.toFile()
            .walkTopDown()
            .filter { file ->
                file.isFile && (file.extension == "kt" || file.extension == "java")
            }
            .flatMap { file ->
                findUserVisibleLiteralViolations(
                    root = root,
                    path = file.toPath()
                ).asSequence()
            }
            .toList()

        assertTrue(
            buildString {
                append("Static product copy at a user-visible API must use string resources.")
                if (violations.isNotEmpty()) {
                    append('\n')
                    append(violations.joinToString(separator = "\n"))
                }
            },
            violations.isEmpty()
        )
    }

    @Test
    fun scannerIgnoresTechnicalAndDynamicValuesButRejectsStaticUiCopy() {
        assertTrue(findViolationsInLine("throw IllegalStateException(\"Storage failed\")", false).isEmpty())
        assertTrue(findViolationsInLine("val title = deviceName", true).isEmpty())
        assertTrue(findViolationsInLine("view.setText(\"\")", true).isEmpty())
        assertTrue(findViolationsInLine("val suffix = \" else \"", false).isEmpty())
        assertEquals(
            1,
            findViolationsInLine("view.setContentDescription(\"Open details\")", true).size
        )
        assertEquals(
            1,
            findViolationsInLine("val title = \"Aquarium details\"", true).size
        )
    }

    private fun findUserVisibleLiteralViolations(
        root: Path,
        path: Path
    ): List<String> {
        val relativePath = relative(root, path)
        val isUiSource = "/ui/" in "/$relativePath"
        val violations = mutableListOf<String>()
        var inBlockComment = false

        Files.readAllLines(path, StandardCharsets.UTF_8).forEachIndexed { index, rawLine ->
            val result = codeWithoutComments(rawLine, inBlockComment)
            val line = result.first
            inBlockComment = result.second
            findViolationsInLine(line, isUiSource).forEach { violation ->
                violations += "$relativePath:${index + 1}: $violation"
            }
        }
        return violations.distinct()
    }

    private fun findViolationsInLine(
        line: String,
        isUiSource: Boolean
    ): List<String> {
        if (line.isBlank()) return emptyList()
        val patterns = buildList {
            if (isUiSource) add(KOTLIN_UI_ASSIGNMENT)
            add(USER_VISIBLE_SETTER)
            add(USER_VISIBLE_FEEDBACK_CALL)
            add(STATIC_AQUA_UI_TEXT)
        }
        return patterns.flatMap { pattern ->
            pattern.findAll(line)
                .mapNotNull { match ->
                    val literal = match.groups[1]?.value ?: return@mapNotNull null
                    match.value.trim().takeIf { hasHumanLanguageCopy(literal) }
                }
                .toList()
        }.distinct()
    }

    private fun codeWithoutComments(
        rawLine: String,
        startedInsideBlock: Boolean
    ): Pair<String, Boolean> {
        val result = StringBuilder()
        var inBlock = startedInsideBlock
        var inString = false
        var escaped = false
        var index = 0

        while (index < rawLine.length) {
            val character = rawLine[index]
            val next = rawLine.getOrNull(index + 1)

            if (inBlock) {
                if (character == '*' && next == '/') {
                    inBlock = false
                    index += 2
                } else {
                    index += 1
                }
                continue
            }

            if (inString) {
                result.append(character)
                if (escaped) {
                    escaped = false
                } else if (character == '\\') {
                    escaped = true
                } else if (character == '"') {
                    inString = false
                }
                index += 1
                continue
            }

            when {
                character == '"' -> {
                    inString = true
                    result.append(character)
                    index += 1
                }

                character == '/' && next == '/' -> return result.toString() to false
                character == '/' && next == '*' -> {
                    inBlock = true
                    index += 2
                }

                else -> {
                    result.append(character)
                    index += 1
                }
            }
        }

        return result.toString() to inBlock
    }

    private fun hasHumanLanguageCopy(quotedLiteral: String): Boolean {
        val literal = quotedLiteral
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace(INTERPOLATION, "")
        return HUMAN_LETTER.containsMatchIn(literal)
    }

    private fun resourceDefinitions(
        directory: Path,
        resourceName: String
    ): List<Path> {
        val stream = Files.list(directory)
        return try {
            stream
                .filter { path ->
                    Files.isRegularFile(path) && path.fileName.toString().endsWith(".xml")
                }
                .filter { path -> resourceValue(path, resourceName) != null }
                .sorted()
                .collect(Collectors.toList())
        } finally {
            stream.close()
        }
    }

    private fun resourceValue(path: Path, resourceName: String): String? {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(path.toFile())
        val nodes = document.getElementsByTagName("string")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            if (element.getAttribute("name") == resourceName) {
                return element.textContent.trim()
            }
        }
        return null
    }

    private fun placeholderSignature(value: String): List<String> {
        var implicitPosition = 1
        return PLACEHOLDER.findAll(value.replace("%%", ""))
            .map { match ->
                val position = match.groupValues[1].ifBlank {
                    (implicitPosition++).toString()
                }
                "$position:${match.groupValues[2].lowercase()}"
            }
            .toList()
    }

    private fun projectRoot(): Path {
        var current: Path? = Path.of(System.getProperty("user.dir"))
            .toAbsolutePath()
            .normalize()
        while (current != null) {
            if (Files.isDirectory(current.resolve("app/src/main"))) {
                return current
            }
            current = current.parent
        }
        error("Could not locate the AquaLight project root from ${System.getProperty("user.dir")}")
    }

    private fun relative(root: Path, path: Path): String =
        root.relativize(path).toString().replace('\\', '/')

    private companion object {
        val PLACEHOLDER = Regex("%(?:(\\d+)\\$)?([A-Za-z])")
        val INTERPOLATION = Regex("\\$\\{[^}]+}|\\$[A-Za-z_][A-Za-z0-9_]*")
        val HUMAN_LETTER = Regex("[A-Za-zÇĞİÖŞÜçğıöşü]")
        val KOTLIN_UI_ASSIGNMENT = Regex(
            "\\b(?:text|title|subtitle|message|contentDescription|hint|helperText|" +
                "placeholderText|prefixText|suffixText)\\s*=\\s*(\"(?:\\\\.|[^\"\\\\])*\")"
        )
        val USER_VISIBLE_SETTER = Regex(
            "\\.(?:setText|setTitle|setSubtitle|setMessage|setContentDescription|setHint|" +
                "setHelperText|setPlaceholderText|setPrefixText|setSuffixText)" +
                "\\s*\\(\\s*(\"(?:\\\\.|[^\"\\\\])*\")"
        )
        val USER_VISIBLE_FEEDBACK_CALL = Regex(
            "(?:Toast\\.makeText|Snackbar\\.make|setPositiveButton|setNegativeButton|" +
                "setNeutralButton)\\s*\\([^\\n]*?(\"(?:\\\\.|[^\"\\\\])*\")"
        )
        val STATIC_AQUA_UI_TEXT = Regex(
            "AquaUiText\\.Dynamic\\s*\\(\\s*(\"(?:\\\\.|[^\"\\\\])*\")"
        )
    }
}
