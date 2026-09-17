description = "Official example expansion: exposes YouNeedMe service data as extra " +
    "PlaceholderAPI placeholders beyond the built-in expansion. Doubles as living " +
    "documentation for third-party expansion authors."

dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":expansions:expansion-api"))
    compileOnly(libs.paper.api)
    compileOnly(libs.placeholderapi)
}
