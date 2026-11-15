package dev.johncastles.fanlet.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.ui.theme.FanletTheme

/**
 * Version, licence notice, where the source lives and what the app can and cannot do.
 * The licence asks interactive programs to show the notice. The source address is plain
 * text: Fanlet has no internet access and opens nothing for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember { installedVersionName(context.packageManager, context.packageName) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.about_title)) }, navigationIcon = { BackButton(onBack) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.titleMedium)

            AboutSection(R.string.about_license_title, R.string.about_license_text)
            AboutSection(R.string.about_source_title, R.string.about_source_text)
            AboutSection(R.string.about_privacy_title, R.string.about_privacy_text)
        }
    }
}

@Composable
private fun AboutSection(titleRes: Int, textRes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = stringResource(textRes), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The version name of the installed app, or "?" if it cannot be read. */
private fun installedVersionName(packageManager: PackageManager, packageName: String): String =
    try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        info.versionName ?: "?"
    } catch (e: PackageManager.NameNotFoundException) {
        "?"
    }

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    FanletTheme { AboutScreen(onBack = {}) }
}
