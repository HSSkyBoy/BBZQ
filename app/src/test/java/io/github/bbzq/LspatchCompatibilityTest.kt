package io.github.bbzq

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class LspatchCompatibilityTest {
    private fun metadata(config: String): String =
        Base64.getEncoder().encodeToString(config.toByteArray(Charsets.UTF_8))

    @Test
    fun managerModeWithRemotePreferencesIsSupported() {
        val config = metadata("""{"useManager":true}""")

        assertTrue(RuntimeEnvironmentInfo.isLspatchManagerSupported("102", "2", config))
        assertTrue(RuntimeEnvironmentInfo.isLspatchManagerSupported("103", "6", config))
    }

    @Test
    fun integratedModeAndOldOrIncompleteFrameworksAreBlocked() {
        val manager = metadata("""{"useManager":true}""")
        val integrated = metadata("""{"useManager":false}""")

        assertFalse(RuntimeEnvironmentInfo.isLspatchManagerSupported("102", "2", integrated))
        assertFalse(RuntimeEnvironmentInfo.isLspatchManagerSupported("101", "2", manager))
        assertFalse(RuntimeEnvironmentInfo.isLspatchManagerSupported("102", "1", manager))
        assertFalse(RuntimeEnvironmentInfo.isLspatchManagerSupported("unknown", "2", manager))
        assertFalse(RuntimeEnvironmentInfo.isLspatchManagerSupported("102", "unknown", manager))
    }

    @Test
    fun missingOrInvalidPatchMetadataIsBlocked() {
        assertFalse(RuntimeEnvironmentInfo.isLspatchManagerSupported("102", "2", null))
        assertFalse(RuntimeEnvironmentInfo.isLspatchManagerSupported("102", "2", "invalid"))
        assertFalse(RuntimeEnvironmentInfo.isLspatchManagerSupported("102", "2", metadata("{}")))
    }
}
