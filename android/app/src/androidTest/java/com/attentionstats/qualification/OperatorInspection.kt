package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import com.attentionstats.detection.SnapshotReader
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.reels.ReelApp
import java.io.File
import java.security.MessageDigest

/** Test-APK-only bounded inspection. Never serializes raw source labels or editable field values. */
internal object OperatorInspection {
    fun run(instrumentation: Instrumentation, target: String): String {
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        var pendingRoot = automation.rootInActiveWindow
        val until = android.os.SystemClock.elapsedRealtime() + 3_000
        while (pendingRoot == null && android.os.SystemClock.elapsedRealtime() < until) {
            android.os.SystemClock.sleep(100); pendingRoot = automation.rootInActiveWindow
        }
        val root = pendingRoot ?: return "UNAVAILABLE: no foreground window"
        val context = instrumentation.targetContext
        try {
            val own = target == "own"
            val app = if (own) null else ReelApp.byKey(target)
            val expected = app?.packages ?: if (own) listOf(context.packageName) else listOf("com.android.settings")
            if (root.packageName?.toString() !in expected) return "UNAVAILABLE: requested surface is not foreground"
            if (own || target == "settings") {
                val rows = mutableListOf<String>()
                val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
                queue.add(root)
                var visited = 0
                try {
                    while (queue.isNotEmpty() && visited++ < 800) {
                        val node = queue.removeFirst()
                        try {
                            val label = (node.text ?: node.contentDescription)?.toString() ?: ""
                            val allowed = own || label.contains("Attention Stats") || label in setOf("Allow", "Use service", "On", "Off", "Installed apps", "Downloaded apps", "Accessibility", "Use Attention Stats: Digital Wellbeing & BrainRot Shield")
                            val safe = !node.isEditable && !Regex("[A-Za-z0-9_-]{43}").matches(label)
                            if (allowed && safe && label.isNotBlank() && label.length <= 180) {
                                val box = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                                rows.add("${if (node.isClickable) "button" else "text"}: $label @${box.left},${box.top},${box.right},${box.bottom}")
                            }
                            for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it) }
                        } finally { if (node !== root) SnapshotReader.recycle(node) }
                    }
                } finally { queue.forEach { if (it !== root) SnapshotReader.recycle(it) } }
                return rows.take(70).joinToString("\n")
            }
            if (app == null) return "UNAVAILABLE: unsupported source"
            val snapshot = SnapshotReader.read(root, app.packages)
            if (!snapshot.complete || snapshot.typing) return "UNAVAILABLE: incomplete or editable context"
            val frame = AdapterRegistry.of(app).read(snapshot)
            val file = File(context.cacheDir, "beta-layout-${app.key}.xml")
            file.outputStream().use { output ->
                val xml = android.util.Xml.newSerializer(); xml.setOutput(output, "UTF-8"); xml.startDocument("UTF-8", true)
                xml.startTag(null, "hierarchy")
                fun write(parent: Int) {
                    snapshot.nodes.filter { it.parent == parent }.forEach { node ->
                        xml.startTag(null, "node")
                        xml.attribute(null, "package", snapshot.packageName)
                        xml.attribute(null, "resource-id", node.id?.let { "${snapshot.packageName}:id/$it" } ?: "")
                        xml.attribute(null, "class", node.cls)
                        val box = node.bounds
                        xml.attribute(null, "bounds", "[${box.left},${box.top}][${box.right},${box.bottom}]")
                        xml.attribute(null, "text", sanitize(node.label ?: ""))
                        xml.attribute(null, "content-desc", "")
                        xml.attribute(null, "clickable", node.clickable.toString())
                        xml.attribute(null, "scrollable", node.scrollable.toString())
                        xml.attribute(null, "selected", node.selected.toString())
                        write(node.index); xml.endTag(null, "node")
                    }
                }
                write(-1); xml.endTag(null, "hierarchy"); xml.endDocument()
            }
            val navigation = snapshot.nodes.filter { node ->
                node.clickable && node.bounds.height < snapshot.screen.height / 6 &&
                    (node.bounds.top < snapshot.screen.top + snapshot.screen.height / 5 ||
                        node.bounds.bottom > snapshot.screen.bottom - snapshot.screen.height / 6) &&
                    node.label?.matches(Regex("Reels(?:[, ]+(?:tab|selected|not selected|[0-9]+|of|new|button))*", RegexOption.IGNORE_CASE)) == true
            }.take(2).joinToString(";") { node ->
                "${node.bounds.left},${node.bounds.top},${node.bounds.right},${node.bounds.bottom}"
            }
            return "CAPTURE ${app.key}: ${snapshot.nodes.size} sanitized nodes; ${frame.surface}/${frame.route}/${frame.category}; numericPlayback=${frame.positionMs != null}; progress=${frame.progress}; page=${frame.page}; fixedReelsNavigation=$navigation"
        } finally { SnapshotReader.recycle(root) }
    }
    private fun sanitize(value: String): String {
        val generic = setOf("home", "reels", "reel", "photo", "photos", "video", "videos", "share", "like", "comment", "comments", "react", "send", "sponsored", "ad", "spotlight", "stories", "camera", "chat", "map", "search", "follow", "following", "save", "more", "play", "pause", "turn sound on", "turn sound off", "play current reel", "watch reel", "story tray", "hide post", "hide ad", "for you", "marketplace")
        fun hash(text: String) = "syn" + MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        if (value.isBlank() || value.trim().lowercase() in generic) return value
        if (Regex("(Home|Reels|Friends|Marketplace|Notifications|Profile), [Tt]ab \\d+ of \\d+.*").matches(value)) return value
        for (prefix in listOf("Reel by ", "Video by ", "Photo by ", "Photo:", "Photo, ", "Photo ", "photo ", "May be an image of", "Image:", "Clip by ", "Profile picture of ", "Sponsored", "Ad•")) {
            if (value.startsWith(prefix)) return prefix + hash(value.removePrefix(prefix))
        }
        return hash(value)
    }
}
