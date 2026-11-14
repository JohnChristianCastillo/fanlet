package dev.johncastles.fanlet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.johncastles.fanlet.ui.theme.FanletTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FanletTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PlaceholderScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.placeholder_message))
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    FanletTheme {
        PlaceholderScreen()
    }
}
