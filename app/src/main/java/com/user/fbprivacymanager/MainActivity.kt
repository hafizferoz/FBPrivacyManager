package com.user.fbprivacymanager

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrivacyAutomationController.initialize(this)
        PrivacyAutomationController.log("FB Privacy Manager opened.")

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PrivacyManagerScreen(
                        onOpenAccessibility = {
                            PrivacyAutomationController.log("Opened Android Accessibility settings. Enable FB Privacy Manager there, then return here.")
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenFacebook = {
                            FacebookNavigator.openFacebook(this)
                        },
                        onCheckAccessibility = ::isAccessibilityServiceEnabled
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyManagerScreen(
    onOpenAccessibility: () -> Unit,
    onOpenFacebook: () -> Unit,
    onCheckAccessibility: () -> Boolean
) {
    var dryRun by remember { mutableStateOf(true) }
    val running by PrivacyAutomationController.runningState.collectAsState()
    val entries by PrivacyAutomationController.entries.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("FB Privacy Manager", style = MaterialTheme.typography.headlineMedium)

        Text(
            "Helps change your own post audience to Only Me through Facebook’s visible UI. " +
                "Sign in only in Facebook or your browser; this app never asks for your password."
        )

        HorizontalDivider()

        Text("Before starting:")
        Text("1. Enable the Accessibility Service below.")
        Text("2. Open Facebook (app or browser) and sign in there.")
        Text("3. Open Activity Log → Posts / Your posts.")
        Text("4. Return here and tap Start, then switch back to Facebook and leave Your posts visible.")

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
            "Status: ${if (running) "Running" else "Stopped"} · ${if (dryRun) "Dry run" else "Write mode"}",
            style = MaterialTheme.typography.titleMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running,
                onClick = {
                    if (onCheckAccessibility()) {
                        PrivacyAutomationController.start(dryRun)
                    } else {
                        PrivacyAutomationController.log(
                            "Cannot start: enable FB Privacy Manager in Android Accessibility settings first."
                        )
                        onOpenAccessibility()
                    }
                }
            ) {
                Text("Start")
            }

            OutlinedButton(
                enabled = running,
                onClick = {
                    PrivacyAutomationController.stop()
                }
            ) {
                Text("Stop")
            }
        }

        Text(
            "Safety: automation handles one visible post at a time and stops if it cannot identify " +
                "the Facebook audience controls. It never selects Delete or uses blind taps."
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Recent activity", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { PrivacyAutomationController.clearLog() }) {
                Text("Clear")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (entries.isEmpty()) {
                Text("No activity yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                entries.asReversed().take(8).forEach { entry ->
                    Text(
                        "${DateFormat.format("HH:mm:ss", Date(entry.timestamp))}  ${entry.message}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun ComponentActivity.isAccessibilityServiceEnabled(): Boolean {
    val manager = getSystemService(AccessibilityManager::class.java) ?: return false
    val expected = ComponentName(this, PrivacyAccessibilityService::class.java)
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { service ->
            val component = service.resolveInfo.serviceInfo
            component.packageName == expected.packageName && component.name == expected.className
        }
}
