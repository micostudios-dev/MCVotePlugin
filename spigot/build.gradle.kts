plugins {
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(libs.spigot)
    implementation(project(":server"))

    // Spigot has no Adventure, so MiniMessage is bundled here to get the same
    // <click>/<hover> support Paper and Folia have natively.
    implementation(libs.adventure.platform.bukkit)
    implementation(libs.adventure.minimessage)
}

tasks {
    shadowJar {
        destinationDirectory.set(file("$rootDir/out"))
        archiveFileName.set("MCVote-Spigot-${project.version}.jar")
        archiveClassifier.set("")
        listOf("com.zaxxer.hikari", "org.mariadb.jdbc", "net.kyori").forEach {
            relocate(it, "org.mcvote.libs.$it")
        }
        mergeServiceFiles()
    }
    build { dependsOn(shadowJar) }
    processResources {
        val props = mapOf("version" to project.version)
        filesMatching("plugin.yml") { expand(props) }
    }
}
