import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "org.nanoya"
version = "1.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.2")
        bundledPlugin("org.jetbrains.plugins.terminal")

        // Plugin Verifier - test against multiple IDE versions
        pluginVerifier()
    }
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))
}

intellijPlatform {
    pluginVerification {
        // IDE versions to verify against (matching sinceBuild 243 to untilBuild 253.*)
        ides {
            // IntelliJ IDEA
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3.2")
            ide(IntelliJPlatformType.IntellijIdeaUltimate, "2024.3.2")

            // WebStorm
            ide(IntelliJPlatformType.WebStorm, "2024.3.2")

            // PyCharm
            ide(IntelliJPlatformType.PyCharmCommunity, "2024.3.2")
            ide(IntelliJPlatformType.PyCharmProfessional, "2024.3.2")

            // PhpStorm
            ide(IntelliJPlatformType.PhpStorm, "2024.3.2")

            // Rider
            ide(IntelliJPlatformType.Rider, "2024.3.2")

            // GoLand
            ide(IntelliJPlatformType.GoLand, "2024.3.2")

            // RubyMine
            ide(IntelliJPlatformType.RubyMine, "2024.3.2")

            // CLion
            ide(IntelliJPlatformType.CLion, "2024.3.2")

            // DataGrip
            ide(IntelliJPlatformType.DataGrip, "2024.3.2")

            // RustRover
            ide(IntelliJPlatformType.RustRover, "2024.3.2")
        }
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("243")
        untilBuild.set("253.*")
    }
    // Skip searchable options to avoid Gradle plugin Java 25 bug
    buildSearchableOptions {
        enabled = false
    }
    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }
    publishPlugin {
        token.set(System.getenv("JETBRAINS_TOKEN"))
    }
}