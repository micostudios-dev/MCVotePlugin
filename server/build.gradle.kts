// Shared Bukkit-family logic (Spigot / Paper / Folia). Compiled against the
// Spigot API — the lowest common denominator — so it runs on all three. Anything
// platform-specific (scheduler, Adventure messaging) is behind an interface.
dependencies {
    api(project(":commons"))
    // Bundled into the Bukkit-family jars where SQLite is the default backend.
    api(libs.sqlite)
    compileOnly(libs.spigot)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.adventure.legacy)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
