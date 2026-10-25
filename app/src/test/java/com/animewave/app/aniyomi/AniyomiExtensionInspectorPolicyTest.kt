package com.animewave.app.aniyomi

import org.junit.Assert.assertEquals
import org.junit.Test

class AniyomiExtensionInspectorPolicyTest {
    @Test
    fun rejectsMissingVersionBeforeTrustCheck() {
        assertEquals(
            CompatibilityStatus.MISSING_VERSION,
            AniyomiExtensionInspector.classifyCompatibility(
                versionName = "",
                extensionLibVersion = 17.0,
                hasSigner = true,
                hasEntryPoint = true,
            ),
        )
    }

    @Test
    fun rejectsUnsignedExtension() {
        assertEquals(
            CompatibilityStatus.UNSIGNED,
            AniyomiExtensionInspector.classifyCompatibility(
                versionName = "1.0.0",
                extensionLibVersion = 17.0,
                hasSigner = false,
                hasEntryPoint = true,
            ),
        )
    }

    @Test
    fun rejectsUnsupportedExtensionApi() {
        assertEquals(
            CompatibilityStatus.UNSUPPORTED_API,
            AniyomiExtensionInspector.classifyCompatibility(
                versionName = "1.0.0",
                extensionLibVersion = 99.0,
                hasSigner = true,
                hasEntryPoint = true,
            ),
        )
    }

    @Test
    fun rejectsMissingSourceEntryPoint() {
        assertEquals(
            CompatibilityStatus.MISSING_ENTRY_POINT,
            AniyomiExtensionInspector.classifyCompatibility(
                versionName = "1.0.0",
                extensionLibVersion = 17.0,
                hasSigner = true,
                hasEntryPoint = false,
            ),
        )
    }

    @Test
    fun neverMarksMetadataOnlyCandidateAsLoadable() {
        assertEquals(
            CompatibilityStatus.REQUIRES_TRUST_AND_HOST,
            AniyomiExtensionInspector.classifyCompatibility(
                versionName = "1.0.0",
                extensionLibVersion = 17.0,
                hasSigner = true,
                hasEntryPoint = true,
            ),
        )
    }
}
