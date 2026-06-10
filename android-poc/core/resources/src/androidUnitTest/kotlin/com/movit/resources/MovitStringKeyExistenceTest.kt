package com.movit.resources

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards dynamic [movitText]/[movitString] keys against typos that crash at runtime.
 */
class MovitStringKeyExistenceTest {

    private val keyPatterns = listOf(
        Regex("""movitText\s*\(\s*"([^"]+)""""),
        Regex("""movitString\s*\(\s*"([^"]+)""""),
    )

    private val literalKeyPattern = Regex("""^[a-z][a-z0-9_]*$""")

    @Test
    fun everyDynamicMovitKey_existsInStringsXml() {
        val androidPocRoot = locateAndroidPocRoot()
        val stringsXml = File(
            androidPocRoot,
            "core/resources/src/commonMain/composeResources/values/strings.xml",
        )
        val definedKeys = parseStringKeys(stringsXml.readText())
        val referencedKeys = mutableSetOf<String>()

        androidPocRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("${File.separator}build${File.separator}") }
            .filterNot { it.path.contains("${File.separator}androidUnitTest${File.separator}") }
            .filterNot { it.path.contains("${File.separator}commonTest${File.separator}") }
            .forEach { file ->
                val text = file.readText()
                keyPatterns.forEach { pattern ->
                    pattern.findAll(text).forEach { match ->
                        val key = match.groupValues[1]
                        if (literalKeyPattern.matches(key)) {
                            referencedKeys += key
                        }
                    }
                }
            }

        val missing = referencedKeys.filterNot(definedKeys::contains).sorted()
        assertTrue(
            missing.isEmpty(),
            "movitText/movitString keys missing from values/strings.xml:\n${missing.joinToString("\n")}",
        )
    }

    private fun parseStringKeys(xml: String): Set<String> {
        val regex = Regex("""<string name="([^"]+)">""")
        return regex.findAll(xml).map { it.groupValues[1] }.toSet()
    }

    private fun locateAndroidPocRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        error("android-poc root not found from ${System.getProperty("user.dir")}")
    }
}
