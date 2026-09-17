import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.spotless) apply false
}

// Captured here, at the script's top level, where the generated type-safe catalog accessor
// resolves - referencing `libs` directly from inside the `subprojects {}` closure below does not
// see the same generated accessor and fails at configuration time with "Extension 'libs' does
// not exist", so every subproject-scoped usage goes through this captured reference instead.
val catalog = libs

allprojects {
    group = "fr.mathildeuh"
    version = "1.0.0"
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "checkstyle")
    apply(plugin = "pmd")
    apply(plugin = "jacoco")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
        maven("https://oss.sonatype.org/content/repositories/snapshots/") { name = "sonatype-oss-snapshots" }
        maven("https://repo.opencollab.dev/main/") { name = "opencollab" }
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") { name = "placeholderapi" }
        maven("https://jitpack.io") { name = "jitpack" }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(catalog.versions.java.get().toInt()))
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addStringOption("Xdoclint:none", "-quiet")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }

    tasks.named("test") {
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    extensions.configure<CheckstyleExtension> {
        toolVersion = catalog.versions.checkstyle.tool.get()
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isIgnoreFailures = true
        maxWarnings = Int.MAX_VALUE
    }

    extensions.configure<PmdExtension> {
        toolVersion = catalog.versions.pmd.tool.get()
        ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
        ruleSets = emptyList()
        isIgnoreFailures = true
        isConsoleOutput = true
    }

    // Formatting is NOT optional: `check`/`build` fails on unformatted code.
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/*/java/**/*.java")
            googleJavaFormat(catalog.versions.google.java.format.get()).aosp().reflowLongStrings()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
        }
    }

    dependencies {
        "testImplementation"(catalog.junit.jupiter)
        "testImplementation"(catalog.mockito.core)
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}
