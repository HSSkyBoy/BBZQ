package io.github.bbzq.feats.symbol

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JadxMangledNameTest {

    @Test
    fun testMangledNameKeepsRealCounterpart() {
        val sources = sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("未找到待检查的源码", sources.size >= MIN_SOURCE_FILES)

        val orphans = sources.flatMap { file ->
            val names = classNameLiterals(file.readText())
            names.mapNotNull { name ->
                val real = name.demangled()
                if (real == name || real in names) null else "${file.name}: $name -> $real"
            }
        }.sorted()

        assertTrue(
            "jadx 反混淆名缺少真实名兜底：\n" + orphans.joinToString("\n"),
            orphans.isEmpty(),
        )
    }

    private fun classNameLiterals(text: String): Set<String> =
        LITERAL.findAll(text)
            .map { it.groupValues[1].replace("\\", "") }
            .filter { it.contains('.') && CLASS_NAME.matches(it) }
            .toSet()

    private fun String.demangled(): String =
        replace(PACKAGE_MANGLE) { it.groupValues[1] }
            .replace(CLASS_MANGLE) { it.groupValues[1] }

    private fun sourceRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
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
        private val CLASS_MANGLE = Regex("(^|\\.)(?:Abstract)?C\\d{5,}(?=[A-Za-z])")
    }
}
