plugins {
    id("movit.kmp.feature")
}

movitKmp {
    namespace = "com.movit.resources"
    unitTestsReturnDefaultValues = true
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            movitComposeResourcesStack()
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.compose.ui)
        }
        sourceSets.named("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.movit.resources"
}

val generateMovitEnglishStrings by tasks.registering {
    val stringsXml = layout.projectDirectory.file("src/commonMain/composeResources/values/strings.xml")
    val outputKt = layout.projectDirectory.file("src/commonMain/kotlin/com/movit/resources/MovitEnglishStrings.kt")
    inputs.file(stringsXml)
    outputs.file(outputKt)
    doLast {
        val xml = stringsXml.asFile.readText()
        val entryRegex = Regex("""<string name="([^"]+)">([^<]*)</string>""")
        val lines = buildList {
            add("package com.movit.resources")
            add("")
            add("// Generated from composeResources/values/strings.xml — do not edit manually.")
            add("internal val movitEnglishStrings: Map<String, String> = mapOf(")
            entryRegex.findAll(xml).forEach { match ->
                val name = match.groupValues[1]
                val unescaped = match.groupValues[2]
                    .replace("\\'", "'")
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                val raw = unescaped
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("$", "\\$")
                add("    \"$name\" to \"$raw\",")
            }
            add(")")
        }
        outputKt.asFile.writeText(lines.joinToString("\n") + "\n")
    }
}

tasks.matching { it.name.startsWith("compile") }.configureEach {
    dependsOn(generateMovitEnglishStrings)
}
