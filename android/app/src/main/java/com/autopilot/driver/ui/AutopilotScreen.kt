package com.autopilot.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.driver.model.ActionStatus
import com.autopilot.driver.model.DiagnosticState
import com.autopilot.driver.model.RunState

@Composable
fun AutopilotScreen(
    state: UiState,
    onMinimumChanged: (String) -> Unit,
    onMaximumChanged: (String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onOverlayChanged: (Boolean) -> Unit,
) {
    val ink = Color(0xFF0B161A)
    val teal = Color(0xFF78E6D0)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Dashboard, contentDescription = null, tint = teal, modifier = Modifier.size(30.dp))
            Column {
                Text("AUTOPILOT", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("AUTHORIZED SCREEN CONTROL", color = teal, fontSize = 10.sp, letterSpacing = 1.6.sp)
            }
        }
        StatusCard(state.snapshot.state, state.snapshot.errorMessage)
        PriceCard(
            minimum = state.minimumPrice,
            maximum = state.maximumPrice,
            onMinimumChanged = onMinimumChanged,
            onMaximumChanged = onMaximumChanged,
            inputError = state.inputError,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = teal, contentColor = ink),
            ) { Text(if (state.snapshot.state == RunState.PAUSED) "RESUME" else "START", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Text("PAUSE", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE35D6A), contentColor = Color.White),
            ) { Text("STOP", fontWeight = FontWeight.Bold) }
        }
        DetectionCard(state)
        DiagnosticsCard(state)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF13262B)), shape = RoundedCornerShape(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, tint = teal)
                    Column {
                        Text("FLOATING CONTROL PANEL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Show controls above other apps", color = Color(0xFF9EB3B6), fontSize = 12.sp)
                    }
                }
                Switch(checked = state.overlayEnabled, onCheckedChange = onOverlayChanged)
            }
        }
        Text(
            "Autopilot only acts after you grant screen capture and accessibility permissions. Low-confidence results produce no action.",
            color = Color(0xFF789094),
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun StatusCard(status: RunState, error: String?) {
    val color = when (status) {
        RunState.RUNNING -> Color(0xFF78E6D0)
        RunState.ERROR -> Color(0xFFE35D6A)
        RunState.PAUSED -> Color(0xFFFFC86B)
        else -> Color(0xFF9EB3B6)
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF13262B)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("STATUS", color = Color(0xFF789094), fontSize = 11.sp, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(6.dp))
            Text(status.name.replace('_', ' '), color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            error?.let { Text(it, color = Color(0xFFFFA9B2), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun PriceCard(
    minimum: String,
    maximum: String,
    onMinimumChanged: (String) -> Unit,
    onMaximumChanged: (String) -> Unit,
    inputError: String?,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF13262B)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PRICE RANGE", color = Color(0xFF78E6D0), fontSize = 11.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minimum,
                    onValueChange = onMinimumChanged,
                    label = { Text("MIN PRICE") },
                    prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = maximum,
                    onValueChange = onMaximumChanged,
                    label = { Text("MAX PRICE") },
                    prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            inputError?.let { Text(it, color = Color(0xFFFFA9B2), fontSize = 12.sp) }
        }
    }
}

@Composable
private fun DetectionCard(state: UiState) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF13262B)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("LIVE DETECTION", color = Color(0xFF78E6D0), fontSize = 11.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
            Text("DETECTED PRICE", color = Color(0xFF789094), fontSize = 11.sp)
            Text(state.snapshot.detectedPrice?.let { "₹%.2f".format(it) } ?: "Not detected", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Divider(color = Color(0xFF274248))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("TARGET", state.snapshot.diagnostics.target.name.replace('_', ' '))
                Metric("CONFIDENCE", "%.0f%%".format(state.snapshot.diagnostics.confidence * 100))
                Metric("LATENCY", state.snapshot.diagnostics.latencyMs?.let { "$it ms" } ?: "—")
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF789094), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun DiagnosticsCard(state: UiState) {
    val d = state.snapshot.diagnostics
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2024)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.BugReport, contentDescription = null, tint = Color(0xFF78E6D0), modifier = Modifier.size(18.dp))
                Text("DIAGNOSTICS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            DiagnosticLine("Capture", d.capture.name)
            DiagnosticLine("Frame", d.frame.name)
            DiagnosticLine("OCR", d.ocr.name)
            DiagnosticLine("OpenCV", d.openCv.name)
            DiagnosticLine("Price", d.price.name)
            DiagnosticLine("Price match", d.priceMatch.name)
            DiagnosticLine("Action", d.action.name)
            d.lastMessage?.let { Text(it, color = Color(0xFF9EB3B6), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp)) }
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF789094), fontSize = 12.sp)
        Text(value, color = if (value == "ERROR" || value == "FAILED") Color(0xFFFFA9B2) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}