package com.example.tracker.blocker

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.tracker.MainActivity
import com.example.tracker.container
import com.example.tracker.ui.blocker.EmergencyUnlockDialog
import com.example.tracker.ui.theme.TrackerTheme
import kotlinx.coroutines.launch

class BlockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_RULE_NAME = "extra_rule_name"
        const val EXTRA_TIME_UNTIL_UNLOCK = "extra_time_until_unlock"
        const val EXTRA_IS_STRICT = "extra_is_strict"
        const val EXTRA_LIMIT_MINUTES = "extra_limit_minutes"
        const val EXTRA_USED_MINUTES = "extra_used_minutes"

        fun start(
            context: Context,
            packageName: String,
            appName: String,
            reason: String,
            ruleName: String,
            timeUntilUnlock: String?,
            isStrict: Boolean,
            limitMinutes: Int,
            usedMinutes: Int,
        ) {
            val intent = Intent(context, BlockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_REASON, reason)
                putExtra(EXTRA_RULE_NAME, ruleName)
                putExtra(EXTRA_TIME_UNTIL_UNLOCK, timeUntilUnlock)
                putExtra(EXTRA_IS_STRICT, isStrict)
                putExtra(EXTRA_LIMIT_MINUTES, limitMinutes)
                putExtra(EXTRA_USED_MINUTES, usedMinutes)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "App is restricted"
        val ruleName = intent.getStringExtra(EXTRA_RULE_NAME) ?: ""
        val timeUntilUnlock = intent.getStringExtra(EXTRA_TIME_UNTIL_UNLOCK)
        val isStrict = intent.getBooleanExtra(EXTRA_IS_STRICT, false)
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, 0)
        val usedMinutes = intent.getIntExtra(EXTRA_USED_MINUTES, 0)

        val appIconDrawable = runCatching {
            packageManager.getApplicationIcon(packageName)
        }.getOrNull()

        setContent {
            TrackerTheme {
                BlockScreenContent(
                    packageName = packageName,
                    appName = appName,
                    appIcon = appIconDrawable,
                    reason = reason,
                    ruleName = ruleName,
                    timeUntilUnlock = timeUntilUnlock,
                    isStrict = isStrict,
                    limitMinutes = limitMinutes,
                    usedMinutes = usedMinutes,
                    onGoHome = {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    },
                    onStartFocus = {
                        val focusIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            // A preset needs a type, otherwise the 45 minutes were ignored.
                            putExtra(com.example.tracker.alarm.Notifications.EXTRA_ROUTE, "focus?type=STUDY&min=45")
                        }
                        startActivity(focusIntent)
                        finish()
                    },
                    onUnlocked = {
                        finish()
                    },
                )
            }
        }
    }

    /**
     * Back (button OR gesture) must never reveal the blocked app underneath: always go to the home screen.
     * Uses OnBackPressedDispatcher because Android 16+ no longer calls onBackPressed() for back gestures.
     */
    private val goHomeOnBack = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            finish()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, goHomeOnBack)
    }
}

@Composable
private fun BlockScreenContent(
    packageName: String,
    appName: String,
    appIcon: Drawable?,
    reason: String,
    ruleName: String,
    timeUntilUnlock: String?,
    isStrict: Boolean,
    limitMinutes: Int,
    usedMinutes: Int,
    onGoHome: () -> Unit,
    onStartFocus: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEmergencyDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF131316),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header: Dinrekha Shield
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "DINREKHA APP BLOCKER",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = Color.White.copy(alpha = 0.7f),
                )
            }

            // Center Content: App Icon & Block Details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // App Icon badge with lock overlay
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (appIcon != null) {
                        val bmp = remember(appIcon) { appIcon.toBitmap(160, 160) }
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = appName,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(24.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF26262B)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = appName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                // Reason pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE53935).copy(alpha = 0.18f),
                ) {
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFFF8A80),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                if (timeUntilUnlock != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = timeUntilUnlock,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }

                // Progress Bar for Daily Limit
                if (limitMinutes > 0) {
                    Spacer(Modifier.height(18.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1E1E24))
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Today's usage",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                            Text(
                                text = "$usedMinutes / $limitMinutes min",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        val progress = (usedMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFFE53935),
                            trackColor = Color.White.copy(alpha = 0.15f),
                            drawStopIndicator = {},
                        )
                    }
                }

                // Strict Mode badge
                if (isStrict) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFF2A93B),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Anti-cheat strict mode is active",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF2A93B),
                        )
                    }
                }
            }

            // Bottom Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onGoHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                ) {
                    Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Exit to Home", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = onStartFocus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Focus Session Instead", color = Color.White)
                }

                TextButton(
                    onClick = { showEmergencyDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Emergency 5 min unlock",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }

    if (showEmergencyDialog) {
        EmergencyUnlockDialog(
            onDismiss = { showEmergencyDialog = false },
            onUnlocked = {
                showEmergencyDialog = false
                onUnlocked()
            },
        )
    }
}
