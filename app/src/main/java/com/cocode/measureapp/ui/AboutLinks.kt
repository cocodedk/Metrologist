package com.cocode.measureapp.ui

/** Every address the About screen can open. */
enum class AboutLink { LatestVersion, Website, Privacy, Source, Issues }

/**
 * True once the app is live on F-Droid (`fdroid` in the cocode-apps registry); until then the
 * "See the latest version" button opens the GitHub release page. Flip it with the first release
 * that F-Droid has accepted.
 */
const val ON_FDROID = false

private const val REPO = "https://github.com/cocodedk/Metrologist"
private const val SITE = "https://measure.cocode.dk/"

/**
 * Languages the site has both a home page and a privacy page for, at `<site>/<code>/` and
 * `<site>/<code>/privacy/`. Persian has a home page but no privacy page of its own, so Persian
 * stays on the English pages.
 */
private val SITE_LANGUAGES = setOf("da")

private fun sitePage(language: String, path: String = ""): String =
    if (language in SITE_LANGUAGES) "$SITE$language/$path" else "$SITE$path"

/**
 * The address behind each About link. The website and privacy links follow [language] (a code such
 * as "da" from the app's current locale) and open the English pages when the site has none in that
 * language. The app never asks these servers anything itself: the phone's browser opens the page,
 * and the app does not check for updates over the network.
 */
fun aboutUrl(
    link: AboutLink,
    applicationId: String,
    language: String,
    onFdroid: Boolean = ON_FDROID,
): String = when (link) {
    AboutLink.LatestVersion ->
        if (onFdroid) "https://f-droid.org/packages/$applicationId/" else "$REPO/releases/latest"
    AboutLink.Website -> sitePage(language)
    AboutLink.Privacy -> sitePage(language, "privacy/")
    AboutLink.Source -> REPO
    AboutLink.Issues -> "$REPO/issues"
}
