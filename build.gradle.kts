plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.5.1" apply false
}

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.extendedclip.com/releases/")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        // Avoid invokedynamic string concatenation so shadow's relocation
        // remapper (ASM) can process the bytecode reliably.
        options.compilerArgs.add("-XDstringConcat=inline")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
