import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy

plugins {
    alias(libs.plugins.shadow)
}

description = "YouNeedMe plugin implementation: commands, storage, scheduler, integrations."

val shadeRelocate = "fr.mathildeuh.youneedme.libs"

dependencies {
    api(project(":api"))
    implementation(project(":expansions:expansion-api"))

    compileOnly(libs.paper.api)

    // Soft-depended third-party integrations - never required at runtime.
    compileOnly(libs.vault.api)
    compileOnly(libs.luckperms.api)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.floodgate.api)
    compileOnly(libs.gson) // already on the server classpath (Bukkit ships Gson)
    // DiscordSRV is intentionally NOT a compile dependency: the integration talks to it purely
    // via reflection (see integrations/discord/DiscordSrvBridge), so no DiscordSRV/JDA jar (and
    // its heavy transitive graph) needs to resolve at build time.

    // Storage backends - bundled and relocated in the shaded jar.
    implementation(libs.hikaricp)
    implementation(libs.sqlite.jdbc)
    implementation(libs.mariadb.java.client)
    implementation(libs.postgresql)
    implementation(libs.mongodb.driver.sync)
    implementation(libs.jedis)

    testImplementation(project(":api"))
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
}

tasks.named<ShadowJar>("shadowJar") {
    // "YouNeedMe-<version>.jar" instead of the default "core-<version>.jar" - this is the file a
    // server operator drops into plugins/, so it needs to be identifiable at a glance.
    archiveBaseName.set("YouNeedMe")
    archiveClassifier.set("")
    // mergeServiceFiles() merges every jar's META-INF/services/* into one file per SPI (needed so
    // both the SQLite and MariaDB java.sql.Driver registrations survive in the shaded jar, along
    // with MariaDB's own plugin SPIs). Gradle's default DuplicatesStrategy (EXCLUDE) drops a
    // duplicate entry before the transformer ever sees it, silently keeping only the first jar's
    // registrations - INCLUDE hands every duplicate to the transformer so the merge is complete.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()

    listOf(
        "com.zaxxer.hikari",
        "org.sqlite",
        "org.mariadb.jdbc",
        "org.postgresql",
        "com.mongodb",
        "org.bson",
        "redis.clients.jedis",
        "org.apache.commons.pool2",
        "org.slf4j",
    ).forEach { relocate(it, "$shadeRelocate.$it") }

    // These two ship their own module-info / multi-release jars that upset the relocator;
    // exclude the noise, keep the classes.
    exclude("module-info.class")
    exclude("META-INF/versions/**/module-info.class")
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
