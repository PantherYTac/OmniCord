plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.omnicord.plugin"
version = "0.0.1-alpha"
description = "OmniCord — The Next-Generation Minecraft to Discord Bridge & Suite"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://papermc.io/repo/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    // Minecraft Server API (Spigot API 1.20.4 ensures 1.16+ backwards and future forwards compatibility)
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")

    // Kyori Adventure & MiniMessage (Rich formatting, RGB colors, legacy & modern converter)
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.3")
    implementation("net.kyori:adventure-text-serializer-legacy:4.17.0")

    // JDA (Discord Bot API 5.x)
    implementation("net.dv8tion:JDA:5.0.0-beta.24") {
        exclude(module = "opus-java")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    // Database & Connection Pooling
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.45.2.0")
    implementation("com.mysql:mysql-connector-j:8.3.0")

    // Optional Plugin Hooks (CompileOnly)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.5")
    compileOnly("net.luckperms:api:5.4")

    // Annotations
    compileOnly("org.jetbrains:annotations:24.1.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("OmniCord-${project.version}.jar")

        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    build {
        dependsOn(shadowJar)
    }
}
