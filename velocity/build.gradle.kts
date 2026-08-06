plugins {
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(libs.velocity)
    annotationProcessor(libs.velocity)
    implementation(project(":commons"))
    // The proxy can own the state in a local file, so it ships the driver too.
    implementation(libs.sqlite)
    implementation(libs.snakeyaml)
}

tasks {
    shadowJar {
        destinationDirectory.set(file("$rootDir/out"))
        archiveFileName.set("MCVote-Velocity-${project.version}.jar")
        archiveClassifier.set("")
        listOf("com.zaxxer.hikari", "org.mariadb.jdbc", "org.yaml.snakeyaml").forEach {
            relocate(it, "org.mcvote.libs.$it")
        }
        mergeServiceFiles()
    }
    build { dependsOn(shadowJar) }
}
