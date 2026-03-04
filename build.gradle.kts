plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.pudding"
version = "0.0.1"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2023.3")
        bundledPlugin("com.intellij.java")
        bundledPlugin("Git4Idea")
        instrumentationTools()
    }
    implementation("com.google.code.gson:gson:2.10.1")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.pudding.mcp"
        name = "MCP Server"
        version = "1.0.0"
        description = "IntelliJ IDEA PSI capabilities exposed via MCP protocol"
        ideaVersion {
            sinceBuild = "233"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
}
