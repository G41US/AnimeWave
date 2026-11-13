package com.animewave.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetArchiveProviderTest {
    @Test fun acceptsOnlyExplicitAllowListedLicenses() {
        assertTrue(InternetArchiveProvider.isAllowedLicense("https://creativecommons.org/publicdomain/zero/1.0/"))
        assertTrue(InternetArchiveProvider.isAllowedLicense("https://creativecommons.org/licenses/by/4.0/"))
        assertTrue(InternetArchiveProvider.isAllowedLicense("https://creativecommons.org/licenses/by-sa/4.0"))
        assertFalse(InternetArchiveProvider.isAllowedLicense(""))
        assertFalse(InternetArchiveProvider.isAllowedLicense("https://creativecommons.org/licenses/by-nc/4.0/"))
        assertFalse(InternetArchiveProvider.isAllowedLicense("https://example.com/license"))
    }
}
