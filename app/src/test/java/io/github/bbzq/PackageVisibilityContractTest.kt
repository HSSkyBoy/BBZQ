package io.github.bbzq

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageVisibilityContractTest {
    private fun root(): File = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    @Test fun scopedQueriesCoverEverySupportedHookTarget() {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(File(root(), "AndroidManifest.xml"))
        val packages = document.getElementsByTagName("package")
        val visible = (0 until packages.length).map {
            packages.item(it).attributes.getNamedItemNS("http://schemas.android.com/apk/res/android", "name").nodeValue
        }.toSet()
        val source = File(root(), "java/io/github/bbzq/BbzqModule.kt").readText()
        val targets = source.substringAfter("private val TARGET_PACKAGES = setOf(").substringBefore(")")
        val names = Regex("\"([^\"]+)\"").findAll(targets).map { it.groupValues[1] }.toSet()
        assertTrue(names.isNotEmpty())
        assertTrue("Missing queries: ${names - visible}", visible.containsAll(names))
    }

    @Test fun networkAndBroadPackagePermissionsAreExplicit() {
        val manifest = File(root(), "AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(manifest.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse(manifest.contains("tools:overrideLibrary"))
    }
}
