plugins {
    `java-library`
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = property("group") as String
version = property("version") as String
description = "Chat with AI providers in Minecraft"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.11.0")
}

tasks {
    compileJava {
        options.release = 21
        options.encoding = "UTF-8"
    }

    processResources {
        val props = mapOf(
            "version" to project.version,
            "description" to project.description
        )
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveBaseName = "AskAI"
    }

    runServer {
        minecraftVersion("1.21.11")
    }
}
