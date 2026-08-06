dependencies {
    api(project(":api"))

    // Bundled + relocated into each platform jar (see shadowJar relocations).
    api(libs.hikari)
    api(libs.mariadb)

    // Provided at runtime by every supported platform.
    compileOnly(libs.gson)

    // Adventure/MiniMessage: native on Paper, Folia and Velocity; bundled and
    // relocated by the Spigot and BungeeCord jars.
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.adventure.legacy)

    // SQLite: compiled here, bundled only by the Bukkit-family jars (see :server).
    compileOnly(libs.sqlite)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    // Provided by the platforms at runtime, needed on the test classpath.
    testImplementation(libs.gson)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.adventure.legacy)
    testImplementation(libs.adventure.plain)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
