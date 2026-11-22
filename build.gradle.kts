plugins {
    kotlin("jvm") version "2.4.10"
    `java-library`
    `maven-publish`
}

group = "net.azisaba"
version = "2.0.0+1.21.11"
description = "Villager-style infinite shop plugin with search"

val kotlinVersion = "2.4.10"
val hikariVersion = "6.2.1"
val sqliteVersion = "3.49.1.0"
val mariadbVersion = "3.5.4"

repositories {
    // StorageBox comes only from Azisaba's repository (never from a local build in ~/.m2)
    exclusiveContent {
        forRepository { maven("https://maven.azisaba.net/releases") }
        filter { includeModule("xyz.acrylicstyle", "StorageBox") }
    }
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://maven.citizensnpcs.co/repo")
    maven("https://jitpack.io")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.lumine:Mythic-Dist:5.12.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    compileOnly("net.citizensnpcs:citizens-main:2.0.30-SNAPSHOT") { exclude("*", "*") }
    compileOnly("xyz.acrylicstyle:StorageBox:1.8.0") { isTransitive = false }

    // Loaded by Paper from Maven Central through plugin.yml `libraries` (not shaded).
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    compileOnly("com.zaxxer:HikariCP:$hikariVersion")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.110.0") {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
        exclude(group = "org.opentest4j")
    }
    testImplementation("com.zaxxer:HikariCP:$hikariVersion")
    testImplementation("org.xerial:sqlite-jdbc:$sqliteVersion")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

tasks {
    withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

    processResources {
        val props = mapOf(
            "version" to project.version,
            "kotlinVersion" to kotlinVersion,
            "hikariVersion" to hikariVersion,
            "sqliteVersion" to sqliteVersion,
            "mariadbVersion" to mariadbVersion,
        )
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") { expand(props) }
    }

    test {
        useJUnitPlatform()
    }
}

publishing {
    repositories {
        maven {
            name = "repo"
            credentials(PasswordCredentials::class)
            url = uri(
                if (project.version.toString().endsWith("-SNAPSHOT")) "https://maven.azisaba.net/snapshots"
                else "https://maven.azisaba.net/releases"
            )
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
