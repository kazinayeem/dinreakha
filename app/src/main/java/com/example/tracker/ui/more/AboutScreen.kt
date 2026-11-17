package com.example.tracker.ui.more

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tracker.R

private const val DEVELOPER = "Mohammad Ali Nayeem"
private const val STUDIO = "Bornosoft"
private const val SITE = "bornosoft.bd"

/** About: who made the app, the studio, version, and the privacy promise in three icons. */
@Composable
fun AboutScreen(nav: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Developer card
        Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .background(Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.16f), cs.surfaceContainerLow)))
                    .padding(vertical = 28.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(132.dp).clip(CircleShape).background(cs.surfaceContainerHighest)
                        .border(3.dp, cs.primary, CircleShape),
                ) {
                    Image(
                        painterResource(R.drawable.developer_photo),
                        contentDescription = "Photo of $DEVELOPER",
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(DEVELOPER, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text("Developer", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(cs.primaryContainer).padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Verified, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(STUDIO, style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
                }
                Spacer(Modifier.height(18.dp))
                FilledTonalButton(onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://$SITE"))) }
                }) {
                    Text(SITE)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // App card
        Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Adaptive icon layers drawn by hand (painterResource can't load the adaptive XML).
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) {
                        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().scale(1.5f))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Dinrekha", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Text("Version $version", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
                Fact(Icons.Rounded.CloudOff, "Habits, money and notes stay on this phone")
                Fact(Icons.Rounded.Lock, "No account, no ads, no tracking")
            }
        }

        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column {
                LinkRow(Icons.Rounded.Shield, "Privacy policy") { nav("privacy") }
                LinkRow(Icons.Rounded.Gavel, "Terms of use") { nav("terms") }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Made in Bangladesh by $STUDIO", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
