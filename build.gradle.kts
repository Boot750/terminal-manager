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
        // Only fail on compatibility errors, not warnings
        failureLevel.set(listOf(
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.INVALID_PLUGIN
        ))

        // IDE is specified via command line property for matrix CI builds
        // Usage: ./gradlew verifyPlugin -PverifyIdeType=IC -PverifyIdeVersion=2024.3.2
        ides {
            val ideType = providers.gradleProperty("verifyIdeType").orNull
            val ideVersion = providers.gradleProperty("verifyIdeVersion").orNull ?: "2024.3.2"

            if (ideType != null) {
                // Single IDE from CI matrix
                val platformType = when (ideType) {
                    "IC" -> IntelliJPlatformType.IntellijIdeaCommunity
                    "IU" -> IntelliJPlatformType.IntellijIdeaUltimate
                    "WS" -> IntelliJPlatformType.WebStorm
                    "PS" -> IntelliJPlatformType.PhpStorm
                    "PY" -> IntelliJPlatformType.PyCharmProfessional
                    "PC" -> IntelliJPlatformType.PyCharmCommunity
                    "GO" -> IntelliJPlatformType.GoLand
                    "RM" -> IntelliJPlatformType.RubyMine
                    "CL" -> IntelliJPlatformType.CLion
                    "RD" -> IntelliJPlatformType.Rider
                    "DG" -> IntelliJPlatformType.DataGrip
                    "RR" -> IntelliJPlatformType.RustRover
                    else -> IntelliJPlatformType.IntellijIdeaCommunity
                }
                ide(platformType, ideVersion)
            } else {
                // Default: verify against primary IDE locally
                ide(IntelliJPlatformType.IntellijIdeaCommunity, ideVersion)
            }
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