package com.example.tracker.blocker

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.tracker.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

class TrackerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var currentPackage: String? = null
    private var lastPackageStartTime: Long = 0L
    private var isScreenOn: Boolean = true

    // Reels & Shorts tracking state
    private val lastReelCountTime = mutableMapOf<String, Long>()
    private val inReelsMode = mutableMapOf<String, Boolean>()
    private val lastReelSignature = mutableMapOf<String, String?>()
    private val lastPagerIndex = mutableMapOf<String, Int>()
    private val lastHeavyScan = mutableMapOf<String, Long>()
    private var lastEvalKey: String? = null
    private var lastEvalAt = 0L

    /** Small pill on the right edge while Facebook / Instagram / YouTube is open: time today + reels. */
    private val overlay by lazy {
        UsageOverlay(
            service = this,
            scope = serviceScope,
            usedMsToday = { pkg ->
                val stored = applicationContext.container.blocker.getTodayUsage(pkg)?.foregroundMs ?: 0L
                stored + if (pkg == currentPackage) System.currentTimeMillis() - lastPackageStartTime else 0L
            },
            reelsToday = { platform ->
                applicationContext.container.blocker.getReelsUsage().firstOrNull { it.platform == platform }?.count ?: 0
            },
        )
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    overlay.hide()
                    flushCurrentPackageUsage()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    lastPackageStartTime = System.currentTimeMillis()
                }
            }
        }
    }

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _lastDetectedPackage = MutableStateFlow<String?>(null)
        val lastDetectedPackage: StateFlow<String?> = _lastDetectedPackage.asStateFlow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        lastPackageStartTime = System.currentTimeMillis()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isScreenOn) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg.isBlank() || pkg == "android" || pkg == "com.android.systemui") return
        // Our own side overlay redraws every second; those events must not count as "opened Dinrekha".
        if (pkg == packageName && (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.className?.startsWith("android.widget") == true)) return
        // Keyboards and other overlays fire events too, but they are not the app in front.
        if (pkg.contains("inputmethod") || pkg.contains("keyboard")) return

        _lastDetectedPackage.value = pkg

        // Inspect website if Chrome
        var detectedDomain: String? = null
        if (pkg == "com.android.chrome") {
            detectedDomain = extractDomainFromChrome(rootInActiveWindow)
        }

        val nowMs = System.currentTimeMillis()

        // 1. Record foreground usage transition
        if (currentPackage != null && currentPackage != pkg) {
            val elapsed = nowMs - lastPackageStartTime
            if (elapsed > 500L) {
                val oldPkg = currentPackage!!
                serviceScope.launch {
                    runCatching { applicationContext.container.blocker.recordForegroundTime(oldPkg, elapsed) }
                }
            }
            lastPackageStartTime = nowMs
            serviceScope.launch {
                runCatching { applicationContext.container.blocker.recordAppOpen(pkg) }
            }
        } else if (currentPackage == null) {
            lastPackageStartTime = nowMs
            serviceScope.launch {
                runCatching { applicationContext.container.blocker.recordAppOpen(pkg) }
            }
        }
        currentPackage = pkg
        overlay.onForeground(pkg)

        // 2. Detect Reels & Shorts scroll / views on FB, YT, and Insta
        detectReelsAndShorts(event, pkg)

        // 3. Evaluate blocker rules — on app switch, window change or domain change, else at most every 10 s.
        val evalKey = pkg + "|" + (detectedDomain ?: "")
        val dueEval = evalKey != lastEvalKey || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            nowMs - lastEvalAt >= 10_000L
        if (!dueEval) return
        lastEvalKey = evalKey
        lastEvalAt = nowMs
        serviceScope.launch {
            val decision = runCatching {
                applicationContext.container.blocker.evaluate(
                    packageName = pkg,
                    activeDomain = detectedDomain,
                )
            }.getOrNull() ?: return@launch

            if (decision.isBlocked) {
                // Minimize blocked app immediately
                performGlobalAction(GLOBAL_ACTION_HOME)

                // Log block event
                applicationContext.container.blocker.logBlockEvent(
                    packageName = pkg,
                    reason = decision.reason,
                )

                // Launch block overlay / full screen activity
                BlockActivity.start(
                    context = this@TrackerAccessibilityService,
                    packageName = pkg,
                    appName = getAppName(pkg),
                    reason = decision.reason,
                    ruleName = decision.ruleName,
                    timeUntilUnlock = decision.timeUntilUnlock,
                    isStrict = decision.isStrict,
                    limitMinutes = decision.limitMinutes,
                    usedMinutes = decision.usedMinutes,
                )
            }
        }
    }

    private fun extractDomainFromChrome(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        return runCatching {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
            val text = nodes.firstOrNull()?.text?.toString() ?: ""
            if (text.isNotBlank()) {
                cleanDomain(text)
            } else null
        }.getOrNull()
    }

    private fun cleanDomain(rawUrl: String): String {
        return rawUrl.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore('?')
    }

    private fun flushCurrentPackageUsage() {
        val pkg = currentPackage ?: return
        val nowMs = System.currentTimeMillis()
        val elapsed = nowMs - lastPackageStartTime
        if (elapsed > 500L) {
            serviceScope.launch {
                runCatching { applicationContext.container.blocker.recordForegroundTime(pkg, elapsed) }
            }
        }
        lastPackageStartTime = nowMs
    }

    private fun getAppName(packageName: String): String {
        return runCatching {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    private fun getEventRoot(event: AccessibilityEvent): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null) return active
        var node = event.source ?: return null
        var parent = node.parent
        while (parent != null) {
            node = parent
            parent = node.parent
        }
        return node
    }

    private fun detectReelsAndShorts(event: AccessibilityEvent, pkg: String) {
        val platform = when {
            pkg.startsWith("com.instagram") -> "instagram"
            pkg.startsWith("com.google.android.youtube") || pkg.contains("youtube") -> "youtube"
            pkg.startsWith("com.facebook") -> "facebook"
            pkg == "com.android.chrome" -> {
                val domain = extractDomainFromChrome(rootInActiveWindow)
                when {
                    domain?.contains("instagram.com") == true -> "instagram"
                    domain?.contains("youtube.com") == true -> "youtube"
                    domain?.contains("facebook.com") == true -> "facebook"
                    else -> null
                }
            }
            else -> null
        } ?: return

        // Signal that works in all three apps: Reels/Shorts are a full-screen pager that shows exactly
        // one item when it settles. A new settled index = one more reel. Feeds show several items at once.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && pkg != "com.android.chrome") {
            val idx = event.fromIndex
            if (idx >= 0 && event.toIndex == idx && isFullScreen(event.source)) {
                inReelsMode[platform] = true
                if (idx != lastPagerIndex[platform]) {
                    lastPagerIndex[platform] = idx
                    countReel(platform, minGapMs = 600L)
                }
                return
            }
        }

        // The keyword/tree search below is expensive (many IPC calls); content changes fire many times a second.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val t = System.currentTimeMillis()
            if (t - (lastHeavyScan[platform] ?: 0L) < 500L) return
            lastHeavyScan[platform] = t
        }
        val root = getEventRoot(event) ?: event.source ?: return
        val isNowInReels = isReelsOrShortsActive(platform, pkg, root, event)

        val wasInReels = inReelsMode[platform] ?: false
        val now = System.currentTimeMillis()
        val lastCount = lastReelCountTime[platform] ?: 0L

        if (isNowInReels) {
            inReelsMode[platform] = true
            // Case A: Just entered Reels/Shorts viewer (first reel)
            if (!wasInReels) {
                countReel(platform, minGapMs = 800L)
            }
            // Case B: Swiped / scrolled to next reel
            else if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                countReel(platform, minGapMs = 800L)
            }
            // Case C: New video/sound signature transition (e.g. autoplay / tap next)
            else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val currentSig = extractReelSignature(root, event)
                val lastSig = lastReelSignature[platform]
                if (currentSig != null && currentSig != lastSig && (now - lastCount >= 1000L)) {
                    lastReelSignature[platform] = currentSig
                    countReel(platform, minGapMs = 1000L)
                }
            }
        } else {
            inReelsMode[platform] = false
            lastReelSignature[platform] = null
            lastPagerIndex.remove(platform)
        }
    }

    private fun countReel(platform: String, minGapMs: Long) {
        val now = System.currentTimeMillis()
        if (now - (lastReelCountTime[platform] ?: 0L) < minGapMs) return
        lastReelCountTime[platform] = now
        serviceScope.launch {
            runCatching { applicationContext.container.blocker.recordReelWatched(platform) }
            overlay.refreshSoon()
        }
    }

    private fun isFullScreen(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val r = android.graphics.Rect()
        node.getBoundsInScreen(r)
        val dm = resources.displayMetrics
        return r.height() >= dm.heightPixels * 0.6f && r.width() >= dm.widthPixels * 0.9f
    }

    private fun extractReelSignature(root: AccessibilityNodeInfo, event: AccessibilityEvent): String? {
        return runCatching {
            val audioNodes = root.findAccessibilityNodeInfosByText("Original audio")
                .ifEmpty { root.findAccessibilityNodeInfosByText("Sound") }
                .ifEmpty { root.findAccessibilityNodeInfosByText("Remix") }
            val text = audioNodes.firstOrNull()?.text?.toString() ?: audioNodes.firstOrNull()?.contentDescription?.toString()
            if (!text.isNullOrBlank()) return text
            event.text?.joinToString(" ")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun isReelsOrShortsActive(
        platform: String,
        pkg: String,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent,
    ): Boolean {
        val source = event.source
        return runCatching {
            when (platform) {
                "instagram" -> isInstagramReels(root, event) || (source != null && isInstagramReels(source, event))
                "youtube" -> isYouTubeShorts(root, event) || (source != null && isYouTubeShorts(source, event))
                "facebook" -> isFacebookReels(root, event) || (source != null && isFacebookReels(source, event))
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun isYouTubeShorts(node: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        return runCatching {
            // 1. YouTube Shorts known view IDs
            val knownIds = listOf(
                "com.google.android.youtube:id/reel_player_page_tree",
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_watch_fragment",
                "com.google.android.youtube:id/reel_player_overlay_layout",
                "com.google.android.youtube:id/reel_player_view",
                "com.google.android.youtube:id/shorts_container",
                "com.google.android.youtube:id/shorts_shelf",
                "com.google.android.youtube:id/shorts_player_fragment",
                "com.google.android.youtube:id/reel_page_holder",
            )
            if (knownIds.any { node.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }) {
                return true
            }

            // 2. Class name matching
            val className = event.className?.toString() ?: ""
            if (className.contains("Reel", ignoreCase = true) || className.contains("Short", ignoreCase = true)) {
                return true
            }

            // 3. YouTube Shorts unique controls & action buttons
            val shortsKeywords = listOf(
                "Sound used in this Short",
                "Dislike this short",
                "Remix this video",
                "Remix",
                "Shorts sound",
                "Create with this sound",
            )
            if (shortsKeywords.any { node.findAccessibilityNodeInfosByText(it).isNotEmpty() }) {
                return true
            }

            // 4. Pivot bar / Navigation bar: "Shorts" tab selected
            val shortsNodes = node.findAccessibilityNodeInfosByText("Shorts")
            if (shortsNodes.any { it.isSelected || it.contentDescription?.contains("Shorts, selected", ignoreCase = true) == true }) {
                return true
            }

            // 5. Deep text search fallback
            hasDeepTextMatching(node, listOf("Dislike", "Remix", "Sound used in this Short"))
        }.getOrDefault(false)
    }

    private fun isFacebookReels(node: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        return runCatching {
            // 1. Facebook Reels known view IDs
            val knownIds = listOf(
                "com.facebook.katana:id/fb_shorts_viewer_container",
                "com.facebook.katana:id/fb_shorts_viewer_fragment",
                "com.facebook.katana:id/fb_shorts_video_player",
                "com.facebook.katana:id/fb_shorts_chaining_feed",
                "com.facebook.katana:id/reel_view_root",
                "com.facebook.katana:id/fb_shorts_actions_container",
                "com.facebook.katana:id/fb_shorts_unified_actions_view",
                "com.facebook.katana:id/reel_page_holder",
                "com.facebook.katana:id/viewer_root_container",
            )
            if (knownIds.any { node.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }) {
                return true
            }

            // 2. Class name matching
            val className = event.className?.toString() ?: ""
            if (className.contains("FbShorts", ignoreCase = true) ||
                className.contains("ReelViewer", ignoreCase = true) ||
                className.contains("FbShortsViewerFragment", ignoreCase = true) ||
                className.contains("Reel", ignoreCase = true)
            ) {
                return true
            }

            // 3. Facebook Reels specific text / button indicators
            val reelsKeywords = listOf(
                "Remix this reel",
                "Original audio",
                "Use audio",
                "Audio is unavailable",
                "Reel by",
                "Create Reel",
                "Watch more reels",
                "Reels audio",
                "Send Stars",
            )
            if (reelsKeywords.any { node.findAccessibilityNodeInfosByText(it).isNotEmpty() }) {
                return true
            }

            // 4. Check if "Reels" tab or header is selected
            val reelsTabs = node.findAccessibilityNodeInfosByText("Reels")
            if (reelsTabs.any { it.isSelected || it.contentDescription?.contains("Reels, selected", ignoreCase = true) == true }) {
                return true
            }

            // 5. Deep text search fallback
            hasDeepTextMatching(node, listOf("Remix this reel", "Original audio", "Reel by", "Use audio"))
        }.getOrDefault(false)
    }

    private fun isInstagramReels(node: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        return runCatching {
            val knownIds = listOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/clips_video_container",
                "com.instagram.android:id/reel_viewer_title",
                "com.instagram.android:id/clips_swipe_refresh_layout",
            )
            if (knownIds.any { node.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }) {
                return true
            }

            val className = event.className?.toString() ?: ""
            if (className.contains("Clips", ignoreCase = true) || className.contains("Reel", ignoreCase = true)) {
                return true
            }

            val instaKeywords = listOf("Original audio", "Audio is unavailable", "Remix with", "Reel by")
            if (instaKeywords.any { node.findAccessibilityNodeInfosByText(it).isNotEmpty() }) {
                return true
            }

            val reelsTabs = node.findAccessibilityNodeInfosByText("Reels")
            if (reelsTabs.any { it.isSelected || it.contentDescription?.contains("Reels, selected", ignoreCase = true) == true }) {
                return true
            }

            hasDeepTextMatching(node, listOf("Remix with", "Reel by", "Original audio"))
        }.getOrDefault(false)
    }

    private fun hasDeepTextMatching(root: AccessibilityNodeInfo, keywords: List<String>, maxDepth: Int = 10, maxChecked: Int = 150): Boolean {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var checked = 0
        while (queue.isNotEmpty() && checked < maxChecked) {
            val (node, depth) = queue.removeFirst()
            checked++
            val desc = node.contentDescription?.toString()
            val text = node.text?.toString()
            if (keywords.any { k ->
                (desc != null && desc.contains(k, ignoreCase = true)) ||
                    (text != null && text.contains(k, ignoreCase = true))
            }) {
                return true
            }
            if (depth < maxDepth) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it to depth + 1) }
                }
            }
        }
        return false
    }

    override fun onInterrupt() {
        flushCurrentPackageUsage()
    }

    override fun onDestroy() {
        overlay.hide()
        super.onDestroy()
        _isRunning.value = false
        flushCurrentPackageUsage()
        runCatching { unregisterReceiver(screenReceiver) }
        serviceScope.cancel()
    }
}
