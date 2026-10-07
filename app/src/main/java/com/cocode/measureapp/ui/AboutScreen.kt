package com.cocode.measureapp.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.cocode.measureapp.R
import kotlinx.coroutines.launch

/**
 * The About screen, in the cocode-apps order: name and version, what the app does, privacy,
 * links, credits and licenses, made by Cocode. Each section title is a TalkBack heading.
 * A link opens in the phone's browser; when no app can open it, a message says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val version = remember(context) { appVersion(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noBrowser = stringResource(R.string.about_no_browser)
    val open = { link: AboutLink ->
        if (!openLink(context, aboutUrl(link, context.packageName, language))) {
            scope.launch { snackbar.showSnackbar(noBrowser) }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section(R.string.about_name_title)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyLarge)
            LinkButton(R.string.about_check_updates) { open(AboutLink.LatestVersion) }

            Section(R.string.about_what_title)
            Body(R.string.about_what)

            Section(R.string.about_privacy_title)
            Body(R.string.about_privacy_data)
            Body(R.string.about_privacy_photos)
            Body(R.string.about_privacy_permissions)
            Body(R.string.about_privacy_export)
            LinkButton(R.string.about_privacy_link) { open(AboutLink.Privacy) }

            Section(R.string.about_links_title)
            LinkButton(R.string.about_website) { open(AboutLink.Website) }
            LinkButton(R.string.about_source) { open(AboutLink.Source) }
            LinkButton(R.string.about_report) { open(AboutLink.Issues) }

            Section(R.string.about_credits)
            Body(R.string.about_credits_licence)
            Body(R.string.about_credits_libraries)

            Section(R.string.about_made_by)
            Body(R.string.about_made_by_body)

            // Support slot (cocode-apps standard, section 7): empty until the Support phase. Nothing is shown.
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp).semantics { heading() },
    )
}

@Composable
private fun Body(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun LinkButton(@StringRes label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
}

/** The version name from the installed package, e.g. `0.0.3`; empty if the system cannot say. */
@Suppress("DEPRECATION")
private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

/** Opens [url] in the phone's browser; false when no app can open it or the system refuses. */
private fun openLink(context: Context, url: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
