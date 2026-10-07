package com.cocode.measureapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutLinksTest {
    private val id = "com.cocode.measureapp"

    @Test fun theLatestVersionIsTheGitHubReleaseUntilTheAppIsOnFdroid() {
        assertEquals(
            "https://github.com/cocodedk/Metrologist/releases/latest",
            aboutUrl(AboutLink.LatestVersion, id, "en", onFdroid = false),
        )
    }

    @Test fun theLatestVersionIsTheFdroidPageOnceTheAppIsThere() {
        assertEquals(
            "https://f-droid.org/packages/com.cocode.measureapp/",
            aboutUrl(AboutLink.LatestVersion, id, "en", onFdroid = true),
        )
    }

    @Test fun theDefaultFollowsTheOnFdroidSwitch() {
        assertEquals(
            aboutUrl(AboutLink.LatestVersion, id, "en", ON_FDROID),
            aboutUrl(AboutLink.LatestVersion, id, "en"),
        )
    }

    @Test fun thePrivacyLinkIsTheEnglishPrivacyPageInEnglish() {
        assertEquals("https://measure.cocode.dk/privacy/", aboutUrl(AboutLink.Privacy, id, "en"))
    }

    @Test fun thePrivacyLinkIsTheDanishPrivacyPageInDanish() {
        assertEquals("https://measure.cocode.dk/da/privacy/", aboutUrl(AboutLink.Privacy, id, "da"))
    }

    @Test fun thePrivacyLinkFallsBackToEnglishInALanguageTheSiteLacks() {
        // Persian has a home page but no privacy page of its own.
        for (language in listOf("fa", "de")) {
            assertEquals("https://measure.cocode.dk/privacy/", aboutUrl(AboutLink.Privacy, id, language))
        }
    }

    @Test fun theWebsiteLinkIsTheEnglishSiteInEnglish() {
        assertEquals("https://measure.cocode.dk/", aboutUrl(AboutLink.Website, id, "en"))
    }

    @Test fun theWebsiteLinkIsTheDanishSiteInDanish() {
        assertEquals("https://measure.cocode.dk/da/", aboutUrl(AboutLink.Website, id, "da"))
    }

    @Test fun theWebsiteLinkFallsBackToEnglishInALanguageTheSiteLacks() {
        for (language in listOf("fa", "de")) {
            assertEquals("https://measure.cocode.dk/", aboutUrl(AboutLink.Website, id, language))
        }
    }

    @Test fun sourceAndIssuesDoNotDependOnTheFdroidStateOrTheLanguage() {
        for (onFdroid in listOf(false, true)) {
            for (language in listOf("en", "da", "fa")) {
                assertEquals("https://github.com/cocodedk/Metrologist", aboutUrl(AboutLink.Source, id, language, onFdroid))
                assertEquals(
                    "https://github.com/cocodedk/Metrologist/issues",
                    aboutUrl(AboutLink.Issues, id, language, onFdroid),
                )
            }
        }
    }

    @Test fun everyLinkIsAnHttpsAddress() {
        for (language in listOf("en", "da")) {
            for (link in AboutLink.entries) {
                assertTrue(link.name, aboutUrl(link, id, language).startsWith("https://"))
            }
        }
    }
}
