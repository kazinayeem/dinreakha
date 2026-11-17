package com.example.tracker.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.tracker.MainActivity
import com.example.tracker.alarm.Notifications

/** Opens the app on [route] (each route gets its own URI so Android keeps the intents apart). */
fun openRoute(ctx: Context, route: String): Action = actionStartActivity(
    Intent(ctx, MainActivity::class.java)
        .setData(Uri.parse("dinrekha://widget/${Uri.encode(route)}"))
        .putExtra(Notifications.EXTRA_ROUTE, route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

/** Base: loads one snapshot, then draws with the wallpaper-based Material You colours. */
abstract class DinrekhaWidget : GlanceAppWidget() {
    @Composable abstract fun Content(ctx: Context, d: WidgetSnapshot)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = runCatching { WidgetSnapshot.load(context) }.getOrNull()
        provideContent {
            GlanceTheme {
                if (d == null) Card(GlanceModifier.clickable(openRoute(context, "today"))) { Label("Open Dinrekha") }
                else Content(context, d)
            }
        }
    }
}

@Composable
fun Card(modifier: GlanceModifier = GlanceModifier, pad: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground).padding(pad).then(modifier),
        content = content,
    )
}

@Composable
fun Icon(res: Int, tint: ColorProvider = GlanceTheme.colors.primary, size: Dp = 20.dp, description: String? = null) {
    Image(ImageProvider(res), description, GlanceModifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Round tinted icon button. */
@Composable
fun RoundButton(res: Int, description: String, action: Action, size: Dp = 44.dp, filled: Boolean = false) {
    Box(
        GlanceModifier.size(size).cornerRadius(size / 2)
            .background(if (filled) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Icon(res, if (filled) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer, size * 0.5f, description)
    }
}

@Composable
fun Big(text: String, size: TextUnit = 26.sp, color: ColorProvider = GlanceTheme.colors.onSurface) =
    Text(com.example.tracker.ui.i18n.tr(text), style = TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold), maxLines = 1)

@Composable
fun Title(text: String) =
    Text(com.example.tracker.ui.i18n.tr(text), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)

@Composable
fun Label(text: String, color: ColorProvider = GlanceTheme.colors.onSurfaceVariant, size: TextUnit = 13.sp) =
    Text(com.example.tracker.ui.i18n.tr(text), style = TextStyle(color = color, fontSize = size), maxLines = 1)
