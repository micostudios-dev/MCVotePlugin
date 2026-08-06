plugins {
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(libs.paper)
    implementation(project(":server"))
}

tasks {
    shadowJar {
        destinationDirectory.set(file("$rootDir/out"))
        archiveFileName.set("MCVote-Paper-${project.version}.jar")
        archiveClassifier.set("")
        listOf("com.zaxxer.hikari", "org.mariadb.jdbc").forEach { relocate(it, "org.mcvote.libs.$it") }
        mergeServiceFiles()
    }
    build { dependsOn(shadowJar) }
    processResources {
        val props = mapOf("version" to project.version)
        filesMatching("plugin.yml") { expand(props) }
    }
}
