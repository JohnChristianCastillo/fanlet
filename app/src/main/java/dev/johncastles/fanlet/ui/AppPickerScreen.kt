package dev.johncastles.fanlet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.apps.LaunchableApp
import dev.johncastles.fanlet.apps.LaunchableApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every app with a launcher icon (except Fanlet), with a search field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(title: String, onPicked: (packageName: String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<LaunchableApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            LaunchableApps.query(context.packageManager).filter { it.packageName != context.packageName }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.picker_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val allApps = apps
            if (allApps == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val matching = remember(allApps, query) {
                val search = query.trim()
                if (search.isEmpty()) {
                    allApps
                } else {
                    allApps.filter { it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true) }
                }
            }
            if (matching.isEmpty()) {
                Text(
                    text = stringResource(R.string.picker_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(matching, key = { it.packageName }) { app ->
                    AppPickerRow(app = app, onClick = { onPicked(app.packageName) })
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(app: LaunchableApp, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(lookup = rememberAppLookup(app.packageName), size = 40.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
    }
}
