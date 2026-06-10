plugins {
    id("movit.kmp.feature")
}

movitKmp {
    unitTestsReturnDefaultValues = true
}

android {
    namespace = "com.movit.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(compose.ui)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
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

tasks.named("preBuild") {
    dependsOn(generateMovitEnglishStrings)
}

tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
    dependsOn(generateMovitEnglishStrings)
}
