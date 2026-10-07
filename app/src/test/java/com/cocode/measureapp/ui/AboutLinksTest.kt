package com.cocode.measureapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutLinksTest {
    private val id = "com.cocode.measureapp"

    @Test fun theLatestVersionIsTheGitHubReleaseUntilTheAppIsOnFdroid() {
        assertEquals(
            "https://github.com/cocodedk/Metrologist/releases/latest",
            aboutUrl(AboutLink.LatestVersion, id, onFdroid = false),
        )
    }

    @Test fun theLatestVersionIsTheFdroidPageOnceTheAppIsThere() {
        assertEquals(
            "https://f-droid.org/packages/com.cocode.measureapp/",
            aboutUrl(AboutLink.LatestVersion, id, onFdroid = true),
        )
    }

    @Test fun theAppIsNotOnFdroidYet() {
        assertFalse(ON_FDROID)
        assertEquals(aboutUrl(AboutLink.LatestVersion, id, onFdroid = false), aboutUrl(AboutLink.LatestVersion, id))
    }

    @Test fun thePrivacyLinkIsTheSitesPrivacyPage() {
        assertEquals("https://measure.cocode.dk/privacy/", aboutUrl(AboutLink.Privacy, id))
    }

    @Test fun websiteSourceAndIssuesDoNotDependOnTheFdroidState() {
        for (onFdroid in listOf(false, true)) {
            assertEquals("https://measure.cocode.dk/", aboutUrl(AboutLink.Website, id, onFdroid))
            assertEquals("https://github.com/cocodedk/Metrologist", aboutUrl(AboutLink.Source, id, onFdroid))
            assertEquals("https://github.com/cocodedk/Metrologist/issues", aboutUrl(AboutLink.Issues, id, onFdroid))
        }
    }

    @Test fun everyLinkIsAnHttpsAddress() {
        for (link in AboutLink.entries) {
            assertTrue(link.name, aboutUrl(link, id).startsWith("https://"))
        }
    }
}
