@file:Suppress("HardCodedStringLiteral")

import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String): String = providers.gradleProperty(key).get()
fun environment(key: String): Provider<String?> = providers.environmentVariable(key)

plugins {
  id("java")
  alias(libs.plugins.kotlin)
  alias(libs.plugins.gradleIntelliJPlugin)
  alias(libs.plugins.changelog)
  alias(libs.plugins.detekt)
  alias(libs.plugins.ktlint)
}

// Import variables from gradle.properties file
val pluginGroup: String by project
val pluginName: String by project
val pluginVersion: String by project
val pluginSinceBuild: String by project
val pluginUntilBuild: String by project

val platformVersion: String by project

group = properties("pluginGroup")
version = properties("pluginVersion")

val javaVersion: String by project

repositories {
  mavenCentral()
  mavenLocal()
  gradlePluginPortal()

  intellijPlatform {
    defaultRepositories()
    jetbrainsRuntime()
  }
}

dependencies {
  detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
  implementation("commons-io:commons-io:2.17.0")
  implementation("org.apache.commons:commons-lang3:3.12.0")

  intellijPlatform {
    intellijIdeaUltimate(platformVersion, useInstaller = false)
    pluginVerifier()
    zipSigner()
  }
}

kotlin {
  jvmToolchain(17)
}

detekt {
  config.setFrom("./detekt-config.yml")
  buildUponDefaultConfig = true
  autoCorrect = true
}

// Configure gradle-changelog-plugin plugin.
// Read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
  path.set("${project.projectDir}/docs/CHANGELOG.md")
  version.set(properties("pluginVersion"))
  // header.set(provider { version })
  itemPrefix.set("-")
  keepUnreleasedSection.set(true)
  unreleasedTerm.set("Changelog")
  groups.set(listOf("Features", "Fixes", "Removals", "Other"))
}

intellijPlatform {
  pluginConfiguration {
    id = pluginGroup
    name = pluginName
    version = pluginVersion

    // description = File("./README.md").readText().lines().run {
    //   val start = "<!-- Plugin description -->"
    //   val end = "<!-- Plugin description end -->"
    //
    //   if (!containsAll(listOf(start, end))) {
    //     throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
    //   }
    //   subList(indexOf(start) + 1, indexOf(end))
    // }.joinToString("\n").run { markdownToHTML(this) }

    ideaVersion {
      sinceBuild = pluginSinceBuild
      untilBuild = pluginUntilBuild
    }

    val changelog = project.changelog // local variable for configuration cache compatibility
    changeNotes = provider {
      with(changelog) {
        renderItem(
          (getOrNull(pluginVersion) ?: getUnreleased())
            .withHeader(false)
            .withEmptySections(false),
          Changelog.OutputType.HTML,
        )
      }
    }
  }

  publishing {
    token = environment("INTELLIJ_PUBLISH_TOKEN")
    channels = listOf(pluginVersion.split('-').getOrElse(1) { "default" }.split('.').first())
  }

  signing {
    certificateChain = environment("CERTIFICATE_CHAIN")
    privateKey = environment("PRIVATE_KEY")
    password = environment("PRIVATE_KEY_PASSWORD")
  }
}

tasks {
  wrapper {
    gradleVersion = "8.9"
  }

  buildSearchableOptions {
    enabled = false
  }

  withType<JavaCompile> {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    options.compilerArgs = listOf("-Xlint:deprecation", "-Xlint:unchecked")
  }

  withType<KotlinCompile> {
    kotlinOptions.jvmTarget = javaVersion
  }

  withType<Detekt> {
    jvmTarget = javaVersion
  }
}
