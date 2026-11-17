package com.example.tracker.ui.blocker

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.container
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val REQUIRED_UNLOCK_SENTENCE = "I choose to break my own rule"

@Composable
fun EmergencyUnlockDialog(
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var canUnlock by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(60) }
    var typedSentence by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        canUnlock = c.blocker.canEmergencyUnlock()
        isChecking = false
    }

    // 60-second active timer
    LaunchedEffect(canUnlock) {
        if (!canUnlock) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
    }

    val sentenceMatches = typedSentence.trim().equals(REQUIRED_UNLOCK_SENTENCE, ignoreCase = false)
    val readyToUnlock = canUnlock && secondsLeft == 0 && sentenceMatches

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF2A93B),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Emergency 5-Min Unlock", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isChecking) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (!canUnlock) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE53935).copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Daily limit reached: you have already used your 2 emergency unlocks for today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF8A80),
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                } else {
                    Text(
                        text = "To prevent mindless bypassing, you must wait 60 seconds and type the sentence below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(14.dp))

                    // Timer display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Cool-down wait", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                        Text(
                            text = if (secondsLeft > 0) "${secondsLeft}s" else "Ready ✓",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (secondsLeft > 0) Color(0xFFF2A93B) else Color(0xFF4CAF50),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Type this exact sentence:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = REQUIRED_UNLOCK_SENTENCE,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(8.dp),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = typedSentence,
                        onValueChange = { typedSentence = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Type here…") },
                        singleLine = true,
                        isError = typedSentence.isNotBlank() && !sentenceMatches,
                    )
                }
            }
        },
        confirmButton = {
            if (canUnlock) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            val ok = c.blocker.performEmergencyUnlock()
                            if (ok) {
                                Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Emergency unlock active for 5 minutes"), Toast.LENGTH_LONG).show()
                                onUnlocked()
                            }
                        }
                    },
                    enabled = readyToUnlock,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2A93B)),
                ) {
                    Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Unlock (5 min)", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
