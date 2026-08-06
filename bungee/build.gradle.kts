plugins {
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(libs.bungeecord)
    implementation(project(":commons"))
    // The proxy can own the state in a local file, so it ships the driver too.
    implementation(libs.sqlite)

    // BungeeCord has no Adventure, so MiniMessage is bundled here.
    implementation(libs.adventure.platform.bungeecord)
    implementation(libs.adventure.minimessage)
}

tasks {
    shadowJar {
        destinationDirectory.set(file("$rootDir/out"))
        archiveFileName.set("MCVote-Bungee-${project.version}.jar")
        archiveClassifier.set("")

        listOf("com.zaxxer.hikari", "org.mariadb.jdbc", "net.kyori").forEach {
            relocate(it, "org.mcvote.libs.$it")
        }
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to project.version)
        filesMatching("bungee.yml") { expand(props) }
    }
}
