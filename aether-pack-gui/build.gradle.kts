import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "de.splatgames.aether.pack"
version = project.findProperty("aetherPackVersion")?.toString() ?: "0.2.0-SNAPSHOT"

val aetherPackCoreVersion = project.findProperty("aetherPackCoreVersion")?.toString() ?: "0.2.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
    mavenLocal()
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)

    // Fluent UI (Windows-native look)
    implementation("com.konyaco:fluent:0.0.1-dev.8")
    implementation("com.konyaco:fluent-icons-extended:0.0.1-dev.8")

    // Material3 (temporär während Migration)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Aether Pack modules (from Maven local repository)
    implementation("de.splatgames.aether.pack:aether-pack-core:$aetherPackCoreVersion")
    implementation("de.splatgames.aether.pack:aether-pack-compression:$aetherPackCoreVersion")
    implementation("de.splatgames.aether.pack:aether-pack-crypto:$aetherPackCoreVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    // JetBrains Annotations
    implementation("org.jetbrains:annotations:24.1.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

compose.desktop {
    application {
        mainClass = "de.splatgames.aether.pack.gui.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Aether Pack"
            packageVersion = "1.0.0"
            description = "APACK Archive Manager"
            vendor = "Splatgames.de Software"
            copyright = "Copyright (c) 2025 Splatgames.de Software and Contributors"

            windows {
                menuGroup = "Splatgames Software"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                // iconFile.set(project.file("src/main/resources/icons/app_icon.ico"))
            }

            linux {
                // iconFile.set(project.file("src/main/resources/icons/app_icon.png"))
            }

            macOS {
                bundleID = "de.splatgames.aether.pack.gui"
                dmgPackageVersion = "1.0.0"
                // iconFile.set(project.file("src/main/resources/icons/app_icon.icns"))
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
