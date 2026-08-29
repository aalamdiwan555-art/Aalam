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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import com.autopilot.driver.R
import com.autopilot.driver.model.RunState
import java.util.Locale

@Composable
fun AutopilotScreen(
    state: UiState,
    onMinimumChanged: (String) -> Unit,
    onMaximumChanged: (String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val background = Color(0xFF081013)
    val panel = Color(0xFF102126)
    val accent = Color(0xFF78E6D0)
    val started = state.snapshot.state in setOf(
        RunState.STARTING,
        RunState.RUNNING,
        RunState.PAUSED,
        RunState.STOPPING,
    )
    val paused = state.snapshot.state == RunState.PAUSED
    val statusLabel = when (state.snapshot.state) {
        RunState.RUNNING -> "RUNNING"
        RunState.PAUSED -> "PAUSED"
        RunState.STARTING -> "STARTING"
        RunState.ERROR -> "ERROR"
        RunState.STOPPING -> "STOPPING"
        RunState.STOPPED -> "STOPPED"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_autopilot),
                contentDescription = "Aalam logo",
                tint = Color.Unspecified,
                modifier = Modifier.size(48.dp),
            )
            Column {
                Text(
                    "AALAM",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    "AUTO CLICKER",
                    color = accent,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = panel),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    "STATUS",
                    color = Color(0xFF789094),
                    fontSize = 11.sp,
                    letterSpacing = 1.3.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    statusLabel,
                    color = when (state.snapshot.state) {
                        RunState.ERROR -> Color(0xFFFFA9B2)
                        RunState.RUNNING -> accent
                        else -> Color(0xFFB5C4C7)
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                state.snapshot.errorMessage?.let {
                    Text(
                        it,
                        color = Color(0xFFFFA9B2),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                state.snapshot.detectedPrice?.let {
                    Text(
                        "Detected price: ${formatPrice(it)}",
                        color = Color(0xFFD5E4E6),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                state.snapshot.diagnostics.latencyMs?.let {
                    Text(
                        "Analysis ${it} ms · OCR ${state.snapshot.diagnostics.ocr.name}",
                        color = Color(0xFF9FB5B9),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = panel),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "PRICE RANGE",
                    color = accent,
                    fontSize = 11.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.minimumPrice,
                        onValueChange = onMinimumChanged,
                        label = { Text("MIN") },
                        prefix = { Text("₹ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.maximumPrice,
                        onValueChange = onMaximumChanged,
                        label = { Text("MAX") },
                        prefix = { Text("₹ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                state.inputError?.let {
                    Text(it, color = Color(0xFFFFA9B2), fontSize = 12.sp)
                }
            }
        }

        if (started) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = if (paused) onResume else onPause,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (paused) accent else Color(0xFFE8B45D),
                        contentColor = background,
                    ),
                ) {
                    Text(if (paused) "RESUME" else "PAUSE", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE35D6A),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("STOP", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = background,
                ),
            ) {
                Text("START AUTO CLICKER", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        }
    }
}

private fun formatPrice(price: Double): String = "₹" + String.format(Locale.US, "%.2f", price)