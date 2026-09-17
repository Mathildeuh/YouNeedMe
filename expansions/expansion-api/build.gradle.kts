description = "Dedicated, separately-versioned API third-party developers implement against " +
    "to ship a YouNeedMe expansion jar (plugins/YouNeedMe/expansions/)."

dependencies {
    api(project(":api"))
    compileOnly(libs.paper.api)
}

extensions.configure<JavaPluginExtension> {
    withJavadocJar()
}
