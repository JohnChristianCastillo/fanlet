package dev.johncastles.fanlet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.apps.LaunchableApp
import dev.johncastles.fanlet.apps.LaunchableApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Section key of an app: its first letter, upper case, or '#' for anything else. */
private fun sectionOf(app: LaunchableApp): Char {
    val first = app.label.firstOrNull()?.uppercaseChar() ?: return '#'
    return if (first in 'A'..'Z') first else '#'
}

/** A row of the picker list: a letter header or an app. */
private sealed interface PickerRow {
    data class Header(val letter: Char) : PickerRow
    data class App(val app: LaunchableApp) : PickerRow
}

/**
 * Every app with a launcher icon, except Fanlet and the apps in [excludedPackages] (those
 * already in the pie). Grouped by first letter, with an A-Z index along the side and a
 * search field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    title: String,
    excludedPackages: Set<String>,
    onPicked: (packageName: String) -> Unit,
    onBack: () -> Unit,
) {
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

            val matching = remember(allApps, query, excludedPackages) {
                val search = query.trim()
                allApps.filter { app ->
                    app.packageName !in excludedPackages &&
                        (search.isEmpty() ||
                            app.label.contains(search, ignoreCase = true) ||
                            app.packageName.contains(search, ignoreCase = true))
                }
            }
            if (matching.isEmpty()) {
                Text(
                    text = stringResource(R.string.picker_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }
            GroupedAppList(apps = matching, onPicked = onPicked)
        }
    }
}

/** The apps with a header per letter and the A-Z index on the right. */
@Composable
private fun GroupedAppList(apps: List<LaunchableApp>, onPicked: (String) -> Unit) {
    // Rows and, per letter, the row index of its header (for the index to jump to).
    val (rows, headerIndex) = remember(apps) {
        val rows = mutableListOf<PickerRow>()
        val headerIndex = linkedMapOf<Char, Int>()
        apps.groupBy(::sectionOf)
            .toSortedMap(compareBy { if (it == '#') '{' else it }) // '#' after Z
            .forEach { (letter, group) ->
                headerIndex[letter] = rows.size
                rows += PickerRow.Header(letter)
                group.forEach { rows += PickerRow.App(it) }
            }
        rows to headerIndex
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggedLetter by remember { mutableStateOf<Char?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 28.dp)) {
            items(rows, key = { row ->
                when (row) {
                    is PickerRow.Header -> "header-${row.letter}"
                    is PickerRow.App -> row.app.packageName
                }
            }) { row ->
                when (row) {
                    is PickerRow.Header -> Text(
                        text = row.letter.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                    is PickerRow.App -> AppPickerRow(app = row.app, onClick = { onPicked(row.app.packageName) })
                }
            }
        }

        LetterIndex(
            letters = headerIndex.keys.toList(),
            onLetter = { letter ->
                draggedLetter = letter
                headerIndex[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } }
            },
            onRelease = { draggedLetter = null },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(28.dp)
                .padding(vertical = 8.dp),
        )

        draggedLetter?.let { letter ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** A column of letters; tapping or dragging over it reports the letter under the finger. */
@Composable
private fun LetterIndex(
    letters: List<Char>,
    onLetter: (Char) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return
    var heightPx by remember { mutableIntStateOf(1) }
    fun letterAt(y: Float): Char {
        val index = (y / heightPx * letters.size).toInt().coerceIn(0, letters.lastIndex)
        return letters[index]
    }
    Column(
        modifier = modifier
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                detectTapGestures(
                    onPress = { offset ->
                        onLetter(letterAt(offset.y))
                        tryAwaitRelease()
                        onRelease()
                    },
                )
            }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> onLetter(letterAt(offset.y)) },
                    onDragEnd = onRelease,
                    onDragCancel = onRelease,
                ) { change, _ -> onLetter(letterAt(change.position.y)) }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
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
