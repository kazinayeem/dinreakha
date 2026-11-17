package com.example.tracker.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class Clause(val icon: ImageVector, val title: String, val body: String)

private const val UPDATED = "17 November 2026"

private val PRIVACY = listOf(
    Clause(Icons.Rounded.PhoneAndroid, "Everything stays on your phone",
        "Habits, mood, journal, sleep, money, calendar, focus sessions, class notes and photos are saved only in this app's storage on your phone. There is no account, no server and no cloud sync. We never see your data."),
    Clause(Icons.Rounded.Cloud, "Weather is the one online feature",
        "To show weather and air quality, the app sends your approximate coordinates (latitude and longitude, no name or ID) to Open-Meteo (open-meteo.com). Your phone's location service may also look up the area name. Turn weather off in Settings and nothing is sent."),
    Clause(Icons.Rounded.Accessibility, "App Blocker and screen reading",
        "If you switch on the App Blocker's accessibility service, it reads which app is open and, in Facebook, Instagram and YouTube, the on-screen labels needed to count reels. This happens on the phone only. Nothing is recorded except app names, minutes and reel counts, and nothing leaves the device."),
    Clause(Icons.Rounded.CameraAlt, "Camera, photos and text recognition",
        "Class-note photos you take or pick are copied into the app's private folder. Text is read from them on your phone with Google ML Kit's on-device model; images are not uploaded."),
    Clause(Icons.Rounded.Notifications, "Notifications and alarms",
        "Reminders, the wake-up alarm and the status countdown are scheduled on your phone with Android's alarm system. They need no internet."),
    Clause(Icons.Rounded.Share, "Sharing and backups are your choice",
        "Share cards, PDF notes and backup files are created only when you tap share or export, and go only where you send them. Mood, journal, money and exact sleep times are off on share cards unless you turn them on."),
    Clause(Icons.Rounded.Shield, "No ads, no tracking",
        "The app contains no advertising, no analytics and no third-party tracking."),
    Clause(Icons.Rounded.Delete, "Deleting your data",
        "Use Settings, Reset to wipe everything, or uninstall the app. Because no copy exists anywhere else, deleted data cannot be recovered unless you exported a backup."),
    Clause(Icons.Rounded.Mail, "Contact",
        "Questions about this policy: Bornosoft, bornosoft.bd."),
)

private val TERMS = listOf(
    Clause(Icons.Rounded.Gavel, "Personal use",
        "Dinrekha is a personal tool for planning your day, habits and money. Use it at your own discretion."),
    Clause(Icons.Rounded.Shield, "Not professional advice",
        "Money summaries, sleep and mood insights are for your own tracking only. They are not financial, medical or mental-health advice. If you are struggling, please talk to someone you trust or a professional."),
    Clause(Icons.Rounded.Notifications, "Reminders can be missed",
        "Battery savers, phone restarts or system settings can delay or stop alarms. Don't rely on the app for anything critical."),
    Clause(Icons.Rounded.PhoneAndroid, "Your data, your responsibility",
        "Your data lives only on your phone. Export a backup regularly; a lost or reset phone means lost data."),
    Clause(Icons.Rounded.Update, "Changes",
        "These terms and the privacy policy may change with app updates. The date at the top shows the latest version."),
)

/** Privacy policy or terms of use, shown as short icon-led sections. */
@Composable
fun PolicyScreen(terms: Boolean) {
    val list = if (terms) TERMS else PRIVACY
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                Text(if (terms) "Terms of use" else "Privacy policy",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text("Updated $UPDATED", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(list, key = { it.title }) { c ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(c.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(c.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.padding(top = 4.dp))
                        Text(c.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
