package com.animewave.app.aniyomi

import org.junit.Assert.assertEquals
import org.junit.Test

class AniyomiExtensionInspectorTest {
    @Test
    fun missingVersionIsRejectedFirst() {
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
    fun unsignedPackageIsRejected() {
        assertEquals(
            CompatibilityStatus.UNSIGNED,
            AniyomiExtensionInspector.classifyCompatibility(
                versionName = "1.2.3",
                extensionLibVersion = 17.0,
                hasSigner = false,
                hasEntryPoint = true,
            ),
        )
    }

    @Test
    fun unsupportedApiIsRejected() {
        assertEquals(
            CompatibilityStatus.UNSUPPORTED_API,
            AniyomiExtensionInspector.classifyCompatibility(
                versionName = "1.2.3",
                extensionLibVersion = 99.0,
                hasSigner = true,
                hasEntryPoint = true,
            ),
        )
    }

    @Test
    fun missingSourceFactoryIsRejected() {
        assertEquals(
            CompatibilityStatus.MISSING_ENTRY_POINT,
            AniyomiExtensionInspector.classifyCompatibility(
                versionName = "1.2.3",
                extensionLibVersion = 17.0,
                hasSigner = true,
                hasEntryPoint = false,
            ),
        )
    }

    @Test
    fun compatibleMetadataStillRequiresHostAndExplicitTrust() {
        assertEquals(
            CompatibilityStatus.REQUIRES_TRUST_AND_HOST,
            AniyomiExtensionInspector.classifyCompatibility(
                versionName = "1.2.3",
                extensionLibVersion = 17.0,
                hasSigner = true,
                hasEntryPoint = true,
            ),
        )
    }
}
