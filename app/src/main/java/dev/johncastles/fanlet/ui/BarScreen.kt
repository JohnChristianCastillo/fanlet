package dev.johncastles.fanlet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.config.BarConfig
import dev.johncastles.fanlet.config.Edge
import dev.johncastles.fanlet.ui.theme.FanletTheme

/** One bar's position, size and feel. "Show bars" is repeated here to see the bar while tuning it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarScreen(
    bar: BarConfig,
    showBars: Boolean,
    onShowBarsChanged: (Boolean) -> Unit,
    onBarChanged: (BarConfig) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(barTitle(bar)) }, navigationIcon = { BackButton(onBack) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(title = null) {
                SwitchItem(
                    title = stringResource(R.string.show_bars_title),
                    summary = stringResource(R.string.show_bars_summary),
                    checked = showBars,
                    onCheckedChange = onShowBarsChanged,
                )
            }
            BarSettingsCard(bar = bar, onBarChanged = onBarChanged)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BarScreenPreview() {
    FanletTheme {
        BarScreen(
            bar = BarConfig(id = "right", edge = Edge.RIGHT),
            showBars = true,
            onShowBarsChanged = {},
            onBarChanged = {},
            onBack = {},
        )
    }
}
