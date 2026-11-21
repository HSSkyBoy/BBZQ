package io.github.bbzq.feats.symbol

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JadxMangledNameTest {

    @Test
    fun testNoJadxMangledNamesRemain() {
        val sources = sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("未找到待检查的源码", sources.size >= MIN_SOURCE_FILES)

        val violations = sources.flatMap { file ->
            val text = file.readText()
            val classViolations = classNameLiterals(text)
                .filter { it.hasJadxMangle() }
                .map { name -> "${file.name}: $name" }
            val memberViolations = stringLiterals(text)
                .filter { MEMBER_MANGLE.matches(it) }
                .map { name -> "${file.name}: $name" }
            classViolations + memberViolations
        }.sorted()

        assertTrue(
            "源码中禁止使用 JADX 反混淆伪类名/伪成员名：\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private fun stringLiterals(text: String): Set<String> =
        LITERAL.findAll(text)
            .map { it.groupValues[1].replace("\\", "") }
            .toSet()

    private fun classNameLiterals(text: String): Set<String> =
        stringLiterals(text)
            .filter { it.contains('.') && CLASS_NAME.matches(it) }
            .toSet()

    private fun String.hasJadxMangle(): Boolean =
        PACKAGE_MANGLE.containsMatchIn(this) || CLASS_MANGLE.containsMatchIn(this)

    private fun sourceRoot(): File {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            SOURCE_ROOT_CANDIDATES
                .map { File(dir, it) }
                .firstOrNull { it.isDirectory }
                ?.let { return it }
            dir = dir.parentFile
        }
        throw IllegalStateException("source root not found")
    }

    private companion object {
        private const val MIN_SOURCE_FILES = 50
        private val SOURCE_ROOT_CANDIDATES = listOf("src/main/java", "app/src/main/java")
        private val LITERAL = Regex("\"([^\"\\n]*)\"")
        private val CLASS_NAME = Regex("[A-Za-z0-9_.\$]+")
        private val PACKAGE_MANGLE = Regex("(^|\\.)p\\d{3,}(?=[A-Za-z])")
        private val CLASS_MANGLE = Regex("(^|[.\$])(?:Abstract|Interface)?C\\d{3,}(?=[A-Za-z0-9]|$)")
        private val MEMBER_MANGLE = Regex("^(?:f|mo|m)\\d{4,}[A-Za-z0-9_]+$")
    }
}
