package com.user.fbprivacymanager

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PrivacyManagerScreen(
                        onOpenAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenFacebook = {
                            FacebookNavigator.openFacebook(this)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyManagerScreen(
    onOpenAccessibility: () -> Unit,
    onOpenFacebook: () -> Unit
) {
    var dryRun by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("FB Privacy Manager", style = MaterialTheme.typography.headlineMedium)

        Text(
            "Changes your own Facebook post audience through the visible Facebook UI. " +
            "No Facebook password or API token is collected."
        )

        HorizontalDivider()

        Text("Before starting:")
        Text("1. Enable the Accessibility Service below.")
        Text("2. Open Facebook and sign in normally.")
        Text("3. Open Activity Log → Posts / Your posts.")
        Text("4. Return here and start the automation.")

        Row {
            Checkbox(checked = dryRun, onCheckedChange = { dryRun = it })
            Text("Dry Run (recommended first)")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenAccessibility) {
                Text("Accessibility Settings")
            }
            Button(onClick = onOpenFacebook) {
                Text("Open Facebook")
            }
        }

        HorizontalDivider()

        Text(
            if (running) "Status: Running" else "Status: Stopped",
            style = MaterialTheme.typography.titleMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    PrivacyAutomationController.start(dryRun)
                }
            ) {
                Text("Start")
            }

            OutlinedButton(
                enabled = running,
                onClick = {
                    running = false
                    PrivacyAutomationController.stop()
                }
            ) {
                Text("Stop")
            }
        }

        Text(
            "Safety: the service intentionally stops when it cannot positively identify " +
            "the expected Facebook control. It does not use coordinate-based blind tapping."
        )
    }
}
