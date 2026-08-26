package me.rerere.readstack

import me.rerere.readstack.domain.model.DocFormat
import me.rerere.readstack.domain.model.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests so they can be run on a machine without the Android SDK
 * (./gradlew :app:testDebugUnitTest --tests "me.rerere.readstack.*Test").
 */
class DataSourceSmokeTest {

    @Test
    fun sourceKind_enumValues_areStable() {
        // If this changes, the Room migration story needs attention.
        val all = SourceKind.values().map { it.name }
        assertTrue(all.containsAll(listOf("GITHUB", "GUTENBERG", "WEB_SEARCH", "READ_THE_DOCS", "GITBOOK", "DOCUSAURUS")))
    }

    @Test
    fun docFormat_extensions_matchMimeSuffix() {
        DocFormat.values().forEach { fmt ->
            assertEquals(fmt.name.lowercase(), fmt.extension)
            assertTrue("mime for $fmt", fmt.mime.startsWith("application/") || fmt.mime.startsWith("text/"))
        }
    }
}
