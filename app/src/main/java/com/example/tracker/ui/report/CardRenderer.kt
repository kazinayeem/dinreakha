package com.example.tracker.ui.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.tracker.data.report.CardFormat
import com.example.tracker.data.report.CardPrivacySettings
import com.example.tracker.data.report.CardTheme
import com.example.tracker.data.report.CardType
import com.example.tracker.data.report.DailyReport
import com.example.tracker.data.report.ThirtyDayReport
import com.example.tracker.data.report.WeeklyReport
import com.example.tracker.ui.components.SHORT_DATE
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure vector bitmap rendering engine.
 * Generates exact 1080x1920 (Story) and 1080x1080 (Square) pixel-perfect graphics
 * completely independent of device display density and screen resolution.
 */
object CardRenderer {

    private val DATE_FMT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val MONTH_YEAR_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun renderDailyCard(
        report: DailyReport,
        format: CardFormat,
        theme: CardTheme,
        privacy: CardPrivacySettings,
    ): Bitmap {
        val width = format.width
        val height = format.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colors = getThemeColors(theme)
        drawBackground(canvas, width, height, colors)

        var y = 80f
        val margin = 64f
        val contentWidth = width - (margin * 2)

        // 1. Header & App Branding
        y = drawHeader(
            canvas = canvas,
            y = y,
            margin = margin,
            contentWidth = contentWidth,
            brandSubtitle = "DAILY PROGRESS",
            dateText = DATE_FMT.format(report.date),
            userName = if (privacy.showName) privacy.userName else "",
            colors = colors,
        )

        y += 40f

        // 2. Headline / Hero: Improvement Score
        y = drawDailyHeroScore(
            canvas = canvas,
            y = y,
            margin = margin,
            contentWidth = contentWidth,
            score = report.improvementScore,
            wokeOnTime = report.wokeOnTime,
            hoursSlept = report.hoursSlept,
            showExactTimes = privacy.showExactSleepTimes,
            actualWake = report.actualWake,
            colors = colors,
        )

        y += 40f

        // 3. Key Stat Tiles (2x2 Grid)
        val statTiles = mutableListOf<StatTileData>()
        if (privacy.showFocus) {
            val fHours = report.focusMinutes / 60
            val fMins = report.focusMinutes % 60
            val fVal = if (fHours > 0) "${fHours}h ${fMins}m" else "${fMins}m"
            statTiles.add(StatTileData("⚡", "DEEP FOCUS", fVal, "${report.studyMinutes}m study • ${report.codeMinutes}m code", colors.accentCyan))
        }
        if (privacy.showHabits) {
            statTiles.add(StatTileData("✅", "HABITS DONE", "${report.habitPercent}%", "${report.habitsCompleted}/${report.habitsTotal} checked", colors.accentGreen))
        }
        if (privacy.showPrayers) {
            statTiles.add(StatTileData("🕌", "PRAYERS", "${report.prayersCompleted}/5", if (report.prayersCompleted == 5) "All 5 on time ✓" else "Daily prayers", colors.accentGold))
        }
        if (privacy.showScreenTime) {
            val sHours = report.screenTimeMinutes / 60
            val sMins = report.screenTimeMinutes % 60
            val sVal = if (sHours > 0) "${sHours}h ${sMins}m" else "${sMins}m"
            val diffText = if (report.screenTimeDiffMinutes <= 0) "${-report.screenTimeDiffMinutes}m below avg" else "+${report.screenTimeDiffMinutes}m vs avg"
            statTiles.add(StatTileData("📱", "SCREEN TIME", sVal, diffText, colors.accentRose))
        }

        y = drawStatGrid(canvas, y, margin, contentWidth, statTiles, colors)

        y += 36f

        // 4. Mini Timeline Bar
        y = drawDailyMiniBar(
            canvas = canvas,
            y = y,
            margin = margin,
            contentWidth = contentWidth,
            focusMinutes = report.focusMinutes,
            targetMinutes = 90,
            colors = colors,
        )

        // 5. Optional Private Journal / Mood (Only if explicitly enabled)
        if (privacy.showMood && report.mood != null) {
            y += 24f
            val moodEmojis = listOf("😞", "😕", "😐", "🙂", "😄")
            val moodEmoji = moodEmojis.getOrElse(report.mood - 1) { "🙂" }
            y = drawInfoPill(canvas, y, margin, contentWidth, "$moodEmoji Today's Mood: ${report.mood}/5", colors)
        }
        if (privacy.showJournal && report.journalNote.isNotBlank()) {
            y += 24f
            y = drawInfoPill(canvas, y, margin, contentWidth, "📝 \"${report.journalNote}\"", colors)
        }

        // 6. Badges Row
        if (privacy.showBadges && report.earnedBadges.isNotEmpty()) {
            y += 32f
            y = drawBadgesRow(canvas, y, margin, contentWidth, report.earnedBadges.map { it.iconEmoji + " " + it.title }, colors)
        }

        // 7. Footer Watermark
        drawFooter(canvas, height - 70f, margin, contentWidth, colors)

        return bitmap
    }

    fun renderWeeklyCard(
        report: WeeklyReport,
        format: CardFormat,
        theme: CardTheme,
        privacy: CardPrivacySettings,
    ): Bitmap {
        val width = format.width
        val height = format.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colors = getThemeColors(theme)
        drawBackground(canvas, width, height, colors)

        var y = 80f
        val margin = 64f
        val contentWidth = width - (margin * 2)

        // 1. Header & App Branding
        y = drawHeader(
            canvas = canvas,
            y = y,
            margin = margin,
            contentWidth = contentWidth,
            brandSubtitle = "WEEKLY PROGRESS",
            dateText = "${SHORT_DATE.format(report.startDate)} – ${SHORT_DATE.format(report.endDate)}",
            userName = if (privacy.showName) privacy.userName else "",
            colors = colors,
        )

        y += 40f

        // 2. Headline / Hero: Big Weekly Focus or Score
        val fHours = report.thisWeekFocusMinutes / 60
        val fMins = report.thisWeekFocusMinutes % 60
        val focusDisplay = if (fHours > 0) "${fHours}h ${fMins}m" else "${fMins}m"
        val arrow = if (report.focusDiffPercent >= 0) "↑" else "↓"
        val changeText = if (report.lastWeekFocusMinutes > 0) "$arrow${kotlin.math.abs(report.focusDiffPercent)}% vs last week" else "Week ${report.weekNumber} Total"

        y = drawWeeklyHero(
            canvas = canvas,
            y = y,
            margin = margin,
            contentWidth = contentWidth,
            headlineNumber = focusDisplay,
            headlineLabel = "TOTAL FOCUS THIS WEEK",
            changeBadge = changeText,
            isPositiveChange = report.focusDiffPercent >= 0,
            avgScore = report.avgImprovementScore,
            colors = colors,
        )

        y += 40f

        // 3. Key Stat Tiles (2x2 Grid)
        val statTiles = mutableListOf<StatTileData>()
        if (privacy.showHabits) {
            val hArrow = if (report.habitDiffPercent >= 0) "↑" else "↓"
            statTiles.add(StatTileData("✅", "HABITS CONSISTENCY", "${report.thisWeekHabitPercent}%", "$hArrow${kotlin.math.abs(report.habitDiffPercent)}% vs last week", colors.accentGreen))
        }
        if (privacy.showPrayers) {
            statTiles.add(StatTileData("🕌", "PRAYERS OFFERED", "${report.thisWeekPrayersCount}/35", "${report.thisWeekPrayersCount} completed", colors.accentGold))
        }
        if (privacy.showScreenTime) {
            val sArrow = if (report.screenTimeDiffPercent <= 0) "↓" else "↑"
            val dailyAvgMin = report.thisWeekScreenTimeMinutes / 7
            val sHours = dailyAvgMin / 60
            val sMins = dailyAvgMin % 60
            val sVal = if (sHours > 0) "${sHours}h ${sMins}m /day" else "${sMins}m /day"
            statTiles.add(StatTileData("📱", "DAILY SCREEN TIME", sVal, "$sArrow${kotlin.math.abs(report.screenTimeDiffPercent)}% vs last week", colors.accentRose))
        }
        statTiles.add(StatTileData("⏰", "WOKE ON TIME", "${report.thisWeekWakeOnTimeDays} / 7 days", "Sleep: ${"%.1f".format(report.thisWeekAvgSleepHours)}h avg", colors.accentCyan))

        y = drawStatGrid(canvas, y, margin, contentWidth, statTiles, colors)

        y += 36f

        // 4. Mini Weekly Focus Bars Chart (7 bars)
        y = drawWeeklyFocusBars(
            canvas = canvas,
            y = y,
            margin = margin,
            contentWidth = contentWidth,
            dailyReports = report.dailyReports,
            colors = colors,
        )

        // 5. Plain Language Non-Guilt Insights
        if (report.insights.isNotEmpty()) {
            y += 30f
            y = drawInsightsCard(
                canvas = canvas,
                y = y,
                margin = margin,
                contentWidth = contentWidth,
                insights = report.insights,
                colors = colors,
            )
        }

        // 6. Earned Badges
        if (privacy.showBadges && report.earnedBadges.isNotEmpty()) {
            y += 28f
            y = drawBadgesRow(canvas, y, margin, contentWidth, report.earnedBadges.map { it.iconEmoji + " " + it.title }, colors)
        }

        // 7. Footer Watermark
        drawFooter(canvas, height - 70f, margin, contentWidth, colors)

        return bitmap
    }

    fun renderThirtyDayCard(
        report: ThirtyDayReport,
        format: CardFormat,
        theme: CardTheme,
        privacy: CardPrivacySettings,
    ): Bitmap {
        val width = format.width
        val height = format.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colors = getThemeColors(theme)
        drawBackground(canvas, width, height, colors)

        var y = 80f
        val margin = 64f
        val contentWidth = width - (margin * 2)

        // 1. Header
        y = drawHeader(
            canvas = canvas,
            y = y,
            margin = margin,
            contentWidth = contentWidth,
            brandSubtitle = "30-DAY TRANSFORMATION",
            dateText = "${SHORT_DATE.format(report.planStart)} → ${SHORT_DATE.format(report.planEnd)}",
            userName = if (privacy.showName) privacy.userName else "",
            colors = colors,
        )

        y += 40f

        // 2. Hero: Score Growth
        y = drawTransformationHero(
            canvas = canvas,
            y = y,
            margin = margin,
            contentWidth = contentWidth,
            day1Score = report.day1Score,
            day30Score = report.day30Score,
            growth = report.scoreGrowthPercent,
            colors = colors,
        )

        y += 40f

        // 3. Before vs After Stat Grid
        val statTiles = listOf(
            StatTileData("⚡", "FOCUS TIME", "${report.day1FocusMinutes}m → ${report.day30FocusMinutes}m", "Total: ${"%.1f".format(report.totalFocusHours)} hours logged", colors.accentCyan),
            StatTileData("✅", "HABIT CONSISTENCY", "${report.day1HabitPercent}% → ${report.day30HabitPercent}%", "Routine transformed", colors.accentGreen),
            StatTileData("🕌", "PRAYERS COMPLETED", "${report.totalPrayersCompleted} prayers", "Rhythm established", colors.accentGold),
            StatTileData("📚", "CLASS NOTES SAVED", "${report.totalNotesCaptured} notes", "Fully archived", colors.accentRose),
        )

        y = drawStatGrid(canvas, y, margin, contentWidth, statTiles, colors)

        y += 36f

        // 4. Milestones
        if (report.milestones.isNotEmpty()) {
            y = drawMilestonesCard(
                canvas = canvas,
                y = y,
                margin = margin,
                contentWidth = contentWidth,
                milestones = report.milestones,
                colors = colors,
            )
        }

        // 5. Badges
        if (privacy.showBadges && report.allEarnedBadges.isNotEmpty()) {
            y += 28f
            y = drawBadgesRow(canvas, y, margin, contentWidth, report.allEarnedBadges.map { it.iconEmoji + " " + it.title }, colors)
        }

        drawFooter(canvas, height - 70f, margin, contentWidth, colors)
        return bitmap
    }

    // ==================== Drawing Primitives & Layout Helpers ====================

    private data class StatTileData(
        val icon: String,
        val label: String,
        val value: String,
        val subtitle: String,
        val accentColor: Int,
    )

    private fun drawBackground(canvas: Canvas, width: Int, height: Int, colors: ThemeColors) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            colors.bgTop, colors.bgBottom,
            Shader.TileMode.CLAMP,
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Subtle ambient circle glow in corner
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.glowColor
            alpha = colors.glowAlpha
        }
        canvas.drawCircle(width.toFloat(), 0f, 480f, glowPaint)
    }

    private fun drawHeader(
        canvas: Canvas,
        y: Float,
        margin: Float,
        contentWidth: Float,
        brandSubtitle: String,
        dateText: String,
        userName: String,
        colors: ThemeColors,
    ): Float {
        var curY = y

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textPrimary
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.accentCyan
            textSize = 20f
            letterSpacing = 0.15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = 22f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        canvas.drawText("DINREKHA • দিনরেখা", margin, curY, titlePaint)
        canvas.drawText(dateText, margin + contentWidth, curY, datePaint)

        curY += 28f
        canvas.drawText(brandSubtitle, margin, curY, subPaint)

        if (userName.isNotBlank()) {
            val userPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.textSecondary
                textSize = 20f
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(userName, margin + contentWidth, curY, userPaint)
        }

        return curY
    }

    private fun drawDailyHeroScore(
        canvas: Canvas,
        y: Float,
        margin: Float,
        contentWidth: Float,
        score: Int,
        wokeOnTime: Boolean,
        hoursSlept: Double,
        showExactTimes: Boolean,
        actualWake: java.time.LocalTime?,
        colors: ThemeColors,
    ): Float {
        val cardHeight = 220f
        val rect = RectF(margin, y, margin + contentWidth, y + cardHeight)

        drawCardContainer(canvas, rect, colors)

        // Big Score Circle
        val circleRadius = 78f
        val circleCx = margin + 30f + circleRadius
        val circleCy = y + (cardHeight / 2)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.scoreCircleBg
            style = Paint.Style.FILL
        }
        canvas.drawCircle(circleCx, circleCy, circleRadius, circlePaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (score >= 85) colors.accentGreen else if (score >= 70) colors.accentCyan else colors.accentGold
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawCircle(circleCx, circleCy, circleRadius - 5f, ringPaint)

        // Score text
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textPrimary
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$score", circleCx, circleCy + 14f, scorePaint)

        val scoreSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SCORE", circleCx, circleCy + 42f, scoreSub)

        // Text details beside score
        val textStartX = circleCx + circleRadius + 36f

        val tierTitle = when {
            score >= 90 -> "Mastery Day 🌟"
            score >= 80 -> "High Momentum ⚡"
            score >= 70 -> "Steady Progress 👍"
            else -> "Day in Motion 🔄"
        }
        val headlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textPrimary
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(tierTitle, textStartX, y + 70f, headlinePaint)

        val wakeText = if (wokeOnTime) "Wake: On time ✓" else "Wake: In progress"
        val sleepText = "Sleep: ${"%.1f".format(hoursSlept)}h total"
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = 22f
        }
        canvas.drawText("$wakeText  •  $sleepText", textStartX, y + 115f, detailPaint)

        val statusText = if (score >= 80) "Disciplined morning and deep work." else "Consistent steps toward goals."
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.accentCyan
            textSize = 20f
        }
        canvas.drawText(statusText, textStartX, y + 155f, statusPaint)

        return y + cardHeight
    }

    private fun drawWeeklyHero(
        canvas: Canvas,
        y: Float,
        margin: Float,
        contentWidth: Float,
        headlineNumber: String,
        headlineLabel: String,
        changeBadge: String,
        isPositiveChange: Boolean,
        avgScore: Int,
        colors: ThemeColors,
    ): Float {
        val cardHeight = 210f
        val rect = RectF(margin, y, margin + contentWidth, y + cardHeight)
        drawCardContainer(canvas, rect, colors)

        val pad = 36f
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textPrimary
            textSize = 66f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(headlineNumber, margin + pad, y + 80f, numPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = 22f
            letterSpacing = 0.08f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(headlineLabel, margin + pad, y + 120f, labelPaint)

        // Pill badge
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isPositiveChange) colors.accentGreen else colors.textPrimary
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgeW = badgeTextPaint.measureText(changeBadge) + 32f
        val badgeRect = RectF(margin + pad, y + 140f, margin + pad + badgeW, y + 186f)
        val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isPositiveChange) colors.accentGreen else colors.accentGold
            alpha = 35
        }
        canvas.drawRoundRect(badgeRect, 14f, 14f, badgeBg)
        canvas.drawText(changeBadge, margin + pad + 16f, y + 172f, badgeTextPaint)

        // Score on right
        val rightX = margin + contentWidth - pad
        val scoreNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.accentCyan
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("$avgScore/100", rightX, y + 90f, scoreNumPaint)

        val scoreSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = 20f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("WEEKLY AVERAGE", rightX, y + 124f, scoreSub)

        return y + cardHeight
    }

    private fun drawTransformationHero(
        canvas: Canvas,
        y: Float,
        margin: Float,
        contentWidth: Float,
        day1Score: Int,
        day30Score: Int,
        growth: Int,
        colors: ThemeColors,
    ): Float {
        val cardHeight = 200f
        val rect = RectF(margin, y, margin + contentWidth, y + cardHeight)
        drawCardContainer(canvas, rect, colors)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = 22f
            letterSpacing = 0.1f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("30-DAY HABIT TRANSFORMATION", margin + 36f, y + 54f, titlePaint)

        val compPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textPrimary
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Day 1: $day1Score  →  Day 30: $day30Score", margin + 36f, y + 120f, compPaint)

        val growthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.accentGreen
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sign = if (growth >= 0) "+" else ""
        canvas.drawText("$sign$growth points overall improvement score 🚀", margin + 36f, y + 164f, growthPaint)

        return y + cardHeight
    }

    private fun drawStatGrid(
        canvas: Canvas,
        y: Float,
        margin: Float,
        contentWidth: Float,
        tiles: List<StatTileData>,
        colors: ThemeColors,
    ): Float {
        if (tiles.isEmpty()) return y
        val spacing = 18f
        val tileWidth = (contentWidth - spacing) / 2f
        val tileHeight = 150f

        var curY = y
        for (i in tiles.indices step 2) {
            val leftTile = tiles[i]
            val leftRect = RectF(margin, curY, margin + tileWidth, curY + tileHeight)
            drawStatTile(canvas, leftRect, leftTile, colors)

            if (i + 1 < tiles.size) {
                val rightTile = tiles[i + 1]
                val rightRect = RectF(margin + tileWidth + spacing, curY, margin + contentWidth, curY + tileHeight)
                drawStatTile(canvas, rightRect, rightTile, colors)
            }
            curY += tileHeight + spacing
        }
        return curY - spacing
    }

    private fun drawStatTile(
        canvas: Canvas,
        rect: RectF,
        tile: StatTileData,
        colors: ThemeColors,
    ) {
        drawCardContainer(canvas, rect, colors)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
        }
        canvas.drawText(tile.icon, rect.left + 24f, rect.top + 46f, iconPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = 18f
            letterSpacing = 0.06f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(tile.label, rect.left + 68f, rect.top + 44f, labelPaint)

        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textPrimary
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(tile.value, rect.left + 24f, rect.top + 94f, valPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tile.accentColor
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(tile.subtitle, rect.left + 24f, rect.top + 128f, subPaint)
    }

    private fun drawDailyMiniBar(
        canvas: Canvas,
        y: Float,
        margin: Float,
        contentWidth: Float,
        focusMinutes: Int,
        targetMinutes: Int,
        colors: ThemeColors,
    ): Float {
        val barHeight = 84f
        val rect = RectF(margin, y, margin + contentWidth, y + barHeight)
        drawCardContainer(canvas, rect, colors)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("DAILY FOCUS TARGET: $targetMinutes MIN", margin + 24f, y + 36f, labelPaint)

        val fraction = (focusMinutes.toFloat() / targetMinutes.toFloat()).coerceIn(0f, 1f)
        val percentText = "${(fraction * 100).toInt()}%"
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.accentCyan
            textSize = 19f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(percentText, margin + contentWidth - 24f, y + 36f, pctPaint)

        // Bar track
        val trackY = y + 54f
        val trackH = 12f
        val trackRect = RectF(margin + 24f, trackY, margin + contentWidth - 24f, trackY + trackH)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.cardBorder
        }
        canvas.drawRoundRect(trackRect, 6f, 6f, trackPaint)

        val fillW = (trackRect.width() * fraction)
        val fillRect = RectF(margin + 24f, trackY, margin + 24f + fillW, trackY + trackH)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.accentCyan
        }
        canvas.drawRoundRect(fillRect, 6f, 6f, fillPaint)

        return y + barHeight
    }

    private fun drawWeeklyFocusBars(
        canvas: Canvas,
        y: Float,
        margin: Float,
        contentWidth: Float,
        dailyReports: List<DailyReport>,
        colors: ThemeColors,
    ): Float {
        val chartHeight = 160f
        val rect = RectF(margin, y, margin + contentWidth, y + chartHeight)
        drawCardContainer(canvas, rect, colors)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("DAILY FOCUS HOURS (7 DAYS)", margin + 24f, y + 34f, titlePaint)

        val daysCount = dailyReports.size.coerceAtLeast(1)
        val chartAreaW = contentWidth - 48f
        val slotW = chartAreaW / daysCount.toFloat()
        val barW = 28f
        val maxMinutes = dailyReports.maxOfOrNull { it.focusMinutes }?.coerceAtLeast(120) ?: 120
        val maxBarHeight = 70f
        val baseLineY = y + chartHeight - 34f

        dailyReports.forEachIndexed { idx, day ->
            val cx = margin + 24f + (idx * slotW) + (slotW / 2f)
            val barH = ((day.focusMinutes.toFloat() / maxMinutes.toFloat()) * maxBarHeight).coerceAtLeast(6f)

            val barRect = RectF(cx - (barW / 2f), baseLineY - barH, cx + (barW / 2f), baseLineY)
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (day.focusMinutes >= 90) colors.accentCyan else colors.accentBlue
            }
            canvas.drawRoundRect(barRect, 8f, 8f, barPaint)

            // Day letter
            val dayName = day.date.dayOfWeek.name.take(2)
            val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.textSecondary
                textSize = 16f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(dayName, cx, baseLineY + 20f, dayPaint)
        }

        return y + chartHeight
    }

    private fun drawInsightsCard(
        canvas: Canvas,
        y: Float,
        margin: Float,
        contentWidth: Float,
        insights: List<String>,
        colors: ThemeColors,
    ): Float {
        val pad = 24f
        val lineSpacing = 32f
        val cardHeight = 60f + (insights.size * lineSpacing)
        val rect = RectF(margin, y, margin + contentWidth, y + cardHeight)
        drawCardContainer(canvas, rect, colors)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.accentCyan
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.1f
        }
        canvas.drawText("✨ WEEKLY TAKEAWAYS", margin + pad, y + 34f, headerPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textPrimary
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        var lineY = y + 70f
        insights.forEach { ins ->
            canvas.drawText("• $ins", margin + pad, lineY, textPaint)
            lineY += lineSpacing
        }

        return y + cardHeight
    }

    private fun drawMilestonesCard(
        canvas: Canvas,
        y: Float,
        margin: Float,
        contentWidth: Float,
        milestones: List<String>,
        colors: ThemeColors,
    ): Float {
        val pad = 24f
        val lineSpacing = 32f
        val cardHeight = 60f + (milestones.size * lineSpacing)
        val rect = RectF(margin, y, margin + contentWidth, y + cardHeight)
        drawCardContainer(canvas, rect, colors)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.accentGreen
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.1f
        }
        canvas.drawText("🏆 30-DAY MILESTONES UNLOCKED", margin + pad, y + 34f, headerPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textPrimary
            textSize = 21f
        }

        var lineY = y + 70f
        milestones.forEach { m ->
            canvas.drawText("✓ $m", margin + pad, lineY, textPaint)
            lineY += lineSpacing
        }

        return y + cardHeight
    }

    private fun drawInfoPill(
        canvas: Canvas,
        y: Float,
        margin: Float,
        contentWidth: Float,
        text: String,
        colors: ThemeColors,
    ): Float {
        val pillH = 50f
        val rect = RectF(margin, y, margin + contentWidth, y + pillH)
        drawCardContainer(canvas, rect, colors)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textPrimary
            textSize = 21f
        }
        canvas.drawText(text, margin + 20f, y + 33f, paint)
        return y + pillH
    }

    private fun drawBadgesRow(
        canvas: Canvas,
        y: Float,
        margin: Float,
        contentWidth: Float,
        badgeLabels: List<String>,
        colors: ThemeColors,
    ): Float {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = 18f
            letterSpacing = 0.08f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("EARNED ACHIEVEMENTS", margin, y + 20f, titlePaint)

        var curX = margin
        var curY = y + 36f
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textPrimary
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        badgeLabels.take(4).forEach { bText ->
            val w = badgeTextPaint.measureText(bText) + 32f
            if (curX + w > margin + contentWidth) {
                curX = margin
                curY += 56f
            }
            val bRect = RectF(curX, curY, curX + w, curY + 44f)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.cardBg
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.cardBorder
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(bRect, 22f, 22f, bgPaint)
            canvas.drawRoundRect(bRect, 22f, 22f, borderPaint)
            canvas.drawText(bText, curX + 16f, curY + 29f, badgeTextPaint)
            curX += w + 14f
        }

        return curY + 44f
    }

    private fun drawFooter(
        canvas: Canvas,
        y: Float,
        margin: Float,
        contentWidth: Float,
        colors: ThemeColors,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = 20f
            textAlign = Paint.Align.CENTER
            alpha = 180
        }
        canvas.drawText("Tracked with Dinrekha  •  Personal Growth & Discipline", margin + (contentWidth / 2f), y, paint)
    }

    private fun drawCardContainer(canvas: Canvas, rect: RectF, colors: ThemeColors) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.cardBg
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.cardBorder
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(rect, 24f, 24f, bgPaint)
        canvas.drawRoundRect(rect, 24f, 24f, borderPaint)
    }

    // ==================== Color Palettes ====================

    data class ThemeColors(
        val bgTop: Int,
        val bgBottom: Int,
        val cardBg: Int,
        val cardBorder: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val accentCyan: Int,
        val accentGreen: Int,
        val accentGold: Int,
        val accentRose: Int,
        val accentBlue: Int,
        val scoreCircleBg: Int,
        val glowColor: Int,
        val glowAlpha: Int,
    )

    private fun getThemeColors(theme: CardTheme): ThemeColors {
        return when (theme) {
            CardTheme.CLEAN_LIGHT -> ThemeColors(
                bgTop = Color.parseColor("#F8FAFC"),
                bgBottom = Color.parseColor("#EDF2F7"),
                cardBg = Color.parseColor("#FFFFFF"),
                cardBorder = Color.parseColor("#E2E8F0"),
                textPrimary = Color.parseColor("#0F172A"),
                textSecondary = Color.parseColor("#64748B"),
                accentCyan = Color.parseColor("#0D9488"), // Teal
                accentGreen = Color.parseColor("#16A34A"),
                accentGold = Color.parseColor("#D97706"),
                accentRose = Color.parseColor("#E11D48"),
                accentBlue = Color.parseColor("#2563EB"),
                scoreCircleBg = Color.parseColor("#F1F5F9"),
                glowColor = Color.parseColor("#0D9488"),
                glowAlpha = 20,
            )
            CardTheme.DARK_GRADIENT -> ThemeColors(
                bgTop = Color.parseColor("#090D16"),
                bgBottom = Color.parseColor("#131C31"),
                cardBg = Color.parseColor("#1A2238"),
                cardBorder = Color.parseColor("#2A3656"),
                textPrimary = Color.parseColor("#F8FAFC"),
                textSecondary = Color.parseColor("#94A3B8"),
                accentCyan = Color.parseColor("#38BDF8"), // Electric Cyan
                accentGreen = Color.parseColor("#10B981"), // Emerald
                accentGold = Color.parseColor("#FBBF24"), // Gold
                accentRose = Color.parseColor("#F43F5E"), // Rose
                accentBlue = Color.parseColor("#60A5FA"),
                scoreCircleBg = Color.parseColor("#222C46"),
                glowColor = Color.parseColor("#38BDF8"),
                glowAlpha = 35,
            )
            CardTheme.MINIMALIST -> ThemeColors(
                bgTop = Color.parseColor("#121214"),
                bgBottom = Color.parseColor("#18181B"),
                cardBg = Color.parseColor("#1E1E22"),
                cardBorder = Color.parseColor("#2E2E34"),
                textPrimary = Color.parseColor("#FFFFFF"),
                textSecondary = Color.parseColor("#A1A1AA"),
                accentCyan = Color.parseColor("#E4E4E7"),
                accentGreen = Color.parseColor("#4ADE80"),
                accentGold = Color.parseColor("#F59E0B"),
                accentRose = Color.parseColor("#FB7185"),
                accentBlue = Color.parseColor("#93C5FD"),
                scoreCircleBg = Color.parseColor("#27272A"),
                glowColor = Color.parseColor("#FFFFFF"),
                glowAlpha = 15,
            )
            CardTheme.WARM_SUNSET -> ThemeColors(
                bgTop = Color.parseColor("#1F0B24"),
                bgBottom = Color.parseColor("#5A1838"),
                cardBg = Color.parseColor("#34122C"),
                cardBorder = Color.parseColor("#5A244C"),
                textPrimary = Color.parseColor("#FFF1F2"),
                textSecondary = Color.parseColor("#FECDD3"),
                accentCyan = Color.parseColor("#F97316"), // Warm Orange
                accentGreen = Color.parseColor("#34D399"),
                accentGold = Color.parseColor("#FBBF24"),
                accentRose = Color.parseColor("#FB7185"),
                accentBlue = Color.parseColor("#C084FC"),
                scoreCircleBg = Color.parseColor("#48193D"),
                glowColor = Color.parseColor("#F97316"),
                glowAlpha = 35,
            )
        }
    }
}

