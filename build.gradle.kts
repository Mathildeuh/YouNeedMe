import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    base
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
    version = "1.8.0" // x-release-please-version
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

    // CI builds a second time against the following Paper line (see ci.yml's matrix) to catch API
    // regressions before that build actually becomes stable, without the day-to-day build needing
    // to know or care - `./gradlew build -PpaperApiVersion=26.3.build.+`. Test configurations are
    // excluded: they're pinned to whatever exact build MockBukkit supports (see core's
    // testImplementation), which a pre-release Paper line generally isn't yet.
    val paperApiOverride = rootProject.findProperty("paperApiVersion") as String?
    if (paperApiOverride != null) {
        configurations.matching { !it.name.contains("test", ignoreCase = true) }.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "io.papermc.paper" && requested.name == "paper-api") {
                    useVersion(paperApiOverride)
                }
            }
        }
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
        // 0.8.12 cannot parse Java 25's class file format (major version 69) and crashes
        // instrumenting the JVM's own bootstrap classes - keep this at or above the first version
        // that added Java 25 support.
        toolVersion = "0.8.15"
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

// The single artifact a server operator needs: core's shaded plugin jar, copied to the root
// build/libs/ (not just core/build/libs/) so it's found where `./gradlew build` conventionally
// leaves a project's output, regardless of which module actually produces it.
val productionJar by tasks.registering(Copy::class) {
    dependsOn(":core:shadowJar")
    from(project(":core").layout.buildDirectory.dir("libs")) {
        include("YouNeedMe.jar")
    }
    into(layout.buildDirectory.dir("libs"))
}

tasks.named("assemble") {
    dependsOn(productionJar)
}
