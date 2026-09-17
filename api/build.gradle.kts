description = "Public, stable API for YouNeedMe: service interfaces, domain events and models. " +
    "Third-party plugins and expansions compileOnly against this module."

dependencies {
    compileOnly(libs.paper.api)
}

extensions.configure<JavaPluginExtension> {
    withJavadocJar()
}

tasks.named<Javadoc>("javadoc") {
    // The public API must build clean Javadoc; this is the one module where doclint stays on.
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:all,-missing", "-quiet")
        addBooleanOption("Xwerror", true)
    }
}

// TODO(coverage): once the test suite matures, wire jacocoTestCoverageVerification into
// `check` here with the >=70% (core) / higher (api) thresholds from the project brief.
// Left unenforced for now so `build` reflects real, current coverage instead of a fake gate.
