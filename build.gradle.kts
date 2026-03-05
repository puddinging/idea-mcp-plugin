plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.pudding"
version = "0.0.2"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.java")
        bundledPlugin("Git4Idea")
        bundledModule("intellij.platform.vcs.dvcs.impl")
        instrumentationTools()
    }
    implementation("com.google.code.gson:gson:2.10.1")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.pudding.mcp"
        name = "MCP Server"
        version = project.version.toString()
        description = "IntelliJ IDEA PSI capabilities exposed via MCP protocol"
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
}

tasks {
    runIde {
        jvmArgs("-Didea.trust.all.projects=true")
        // 自动打开指定项目，可通过 -PtestProject=xxx 覆盖
        val testProjectPath = providers.gradleProperty("testProject")
            .orElse("/Users/jiefeng/definesys/agent-run/agent-1.0")
        args(testProjectPath.get())
    }
}
