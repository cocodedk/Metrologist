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
 * The address behind each About link. The app never asks these servers anything itself: the
 * phone's browser opens the page, and the app does not check for updates over the network.
 */
fun aboutUrl(link: AboutLink, applicationId: String, onFdroid: Boolean = ON_FDROID): String = when (link) {
    AboutLink.LatestVersion ->
        if (onFdroid) "https://f-droid.org/packages/$applicationId/" else "$REPO/releases/latest"
    AboutLink.Website -> SITE
    AboutLink.Privacy -> SITE + "privacy/"
    AboutLink.Source -> REPO
    AboutLink.Issues -> "$REPO/issues"
}
