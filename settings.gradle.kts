rootProject.name = "MCVotePlugin"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

listOf(
    "api", "commons", "server", "spigot", "paper", "folia", "bungee", "velocity"
).forEach { module -> include(module) }
