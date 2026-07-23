#!/usr/bin/env python3
"""One-time branch migration from the conflicting Detekt Gradle plugin to Detekt CLI."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP_BUILD = ROOT / "app" / "build.gradle"
SETTINGS = ROOT / "settings.gradle"

PLUGIN_LINE = "    id 'io.gitlab.arturbosch.detekt'\n"
SETTINGS_PLUGIN_LINE = "        id 'io.gitlab.arturbosch.detekt' version '1.23.8'\n"

OLD_DETEKT_BLOCK = '''detekt {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    ignoreFailures = true
    autoCorrect = false
}

tasks.withType(io.gitlab.arturbosch.detekt.Detekt).configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
        md.required.set(false)
    }
}

'''

NEW_DETEKT_BLOCK = '''configurations {
    aqlDetekt
}

def aqlDetektSourcePaths = [
        file("src/main/java"),
        file("src/main/kotlin")
].findAll { it.isDirectory() }

def aqlDetektReportDirectory = file("$buildDir/reports/detekt")
def aqlDetektReportFiles = [
        html: new File(aqlDetektReportDirectory, "detekt.html"),
        xml: new File(aqlDetektReportDirectory, "detekt.xml"),
        sarif: new File(aqlDetektReportDirectory, "detekt.sarif")
]

def aqlDetektTask = tasks.register("aqlDetekt", JavaExec) {
    group = "verification"
    description = "Runs Detekt in transitional reporting mode without the conflicting Gradle lifecycle plugin."
    classpath = configurations.aqlDetekt
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    ignoreExitValue = true

    inputs.files(aqlDetektSourcePaths)
            .withPropertyName("detektSources")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.files(aqlDetektReportFiles.values())
            .withPropertyName("detektReports")

    args(
            "--input", aqlDetektSourcePaths.collect { it.absolutePath }.join(","),
            "--build-upon-default-config",
            "--parallel",
            "--report", "html:${aqlDetektReportFiles.html.absolutePath}",
            "--report", "xml:${aqlDetektReportFiles.xml.absolutePath}",
            "--report", "sarif:${aqlDetektReportFiles.sarif.absolutePath}"
    )

    doFirst {
        aqlDetektReportDirectory.mkdirs()
    }
    doLast {
        def missingReports = aqlDetektReportFiles.values().findAll { !it.isFile() }
        if (!missingReports.isEmpty()) {
            throw new GradleException(
                    "Detekt did not produce the required report(s): " +
                            missingReports.collect { it.absolutePath }.join(", ")
            )
        }
    }
}

// Preserve the existing CI command contract while avoiding duplicate task registration.
def existingDetektTask = tasks.findByName("detekt")
if (existingDetektTask == null) {
    tasks.register("detekt") {
        group = "verification"
        description = "Compatibility lifecycle task for AquaLight Detekt analysis."
        dependsOn aqlDetektTask
    }
} else {
    existingDetektTask.dependsOn aqlDetektTask
}

'''

DEPENDENCY_MARKER = '''dependencies {
    // Firebase BoM
'''
DEPENDENCY_REPLACEMENT = '''dependencies {
    aqlDetekt "io.gitlab.arturbosch.detekt:detekt-cli:1.23.8"

    // Firebase BoM
'''


def replace_once(text: str, old: str, new: str, label: str) -> str:
    occurrences = text.count(old)
    if occurrences != 1:
        raise SystemExit(f"Expected exactly one {label}; found {occurrences}")
    return text.replace(old, new, 1)


def main() -> int:
    app = APP_BUILD.read_text(encoding="utf-8")
    app = replace_once(app, PLUGIN_LINE, "", "Detekt app plugin line")
    app = replace_once(app, OLD_DETEKT_BLOCK, NEW_DETEKT_BLOCK, "Detekt configuration block")
    app = replace_once(app, DEPENDENCY_MARKER, DEPENDENCY_REPLACEMENT, "dependencies marker")
    APP_BUILD.write_text(app, encoding="utf-8")

    settings = SETTINGS.read_text(encoding="utf-8")
    settings = replace_once(
        settings,
        SETTINGS_PLUGIN_LINE,
        "",
        "Detekt plugin-management line",
    )
    SETTINGS.write_text(settings, encoding="utf-8")

    print("Detekt CLI migration applied successfully.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
