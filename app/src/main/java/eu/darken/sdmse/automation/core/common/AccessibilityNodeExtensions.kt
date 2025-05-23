package eu.darken.sdmse.automation.core.common

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import java.util.concurrent.LinkedBlockingDeque

private val TAG: String = logTag("Automation", "Crawler", "Common")

fun AccessibilityNodeInfo.toStringShort(): String {
    val identity = Integer.toHexString(System.identityHashCode(this))
    val bounds = Rect().apply { getBoundsInScreen(this) }
    return "text='${this.text}', class=${this.className}, clickable=$isClickable, checkable=$isCheckable enabled=$isEnabled, id=$viewIdResourceName pkg=$packageName, identity=$identity, bounds=$bounds"
}

val AccessibilityNodeInfo.textVariants: Set<String>
    get() {
        val target = text?.toString() ?: return emptySet()

        return setOf(
            target,
            target.replace(' ', ' ')
        )
    }

fun AccessibilityNodeInfo.textMatchesAny(candidates: Collection<String>): Boolean {
    candidates.forEach { candidate ->
        if (textVariants.any { it.equals(candidate, ignoreCase = true) }) {
            return true
        }
    }
    return false
}

fun AccessibilityNodeInfo.textContainsAny(candidates: Collection<String>): Boolean {
    candidates.forEach { candidate ->
        if (textVariants.any { it.contains(candidate, ignoreCase = true) }) {
            return true
        }
    }
    return false
}

fun AccessibilityNodeInfo.textEndsWithAny(candidates: Collection<String>): Boolean {
    candidates.forEach { candidate ->
        if (textVariants.any { it.endsWith(candidate, ignoreCase = true) }) {
            return true
        }
    }
    return false
}

fun AccessibilityNodeInfo.idMatches(id: String): Boolean {
    return viewIdResourceName == id
}

fun AccessibilityNodeInfo.idContains(id: String): Boolean {
    return viewIdResourceName?.contains(id) == true
}

fun AccessibilityNodeInfo.isClickyButton(): Boolean {
    return isClickable && className == "android.widget.Button"
}

fun AccessibilityNodeInfo.isTextView(): Boolean {
    return className == "android.widget.TextView"
}

fun AccessibilityNodeInfo.isRadioButton(): Boolean {
    return className == "android.widget.RadioButton"
}

fun AccessibilityNodeInfo.children() = (0 until childCount).mapNotNull { getChild(it) }

fun AccessibilityNodeInfo.findParentOrNull(
    maxNesting: Int = 3,
    predicate: (AccessibilityNodeInfo) -> Boolean
): AccessibilityNodeInfo? {
    var target = this.parent ?: return null
    for (i in 1..maxNesting) {
        if (predicate(target)) return target
        target = target.parent ?: break
    }
    return null
}

fun AccessibilityNodeInfo.getRoot(maxNesting: Int = 15 /*AOSP*/): AccessibilityNodeInfo {
    var target: AccessibilityNodeInfo = this
    for (i in 1..maxNesting) {
        target.parent?.let {
            target = it
        } ?: break
    }
    return target
}

fun AccessibilityNodeInfo.crawl(debug: Boolean = Bugs.isTrace): Sequence<CrawledNode> = sequence {
    try {
        if (this@crawl.getChild(0) == null) {
            this@crawl.refresh().let { log(TAG) { "Refresh success: $it" } }
        }
    } catch (e: Exception) {
        log(TAG, WARN) { "Null crawl failed to refresh: ${e.asLog()}" }
    }

    val queue = LinkedBlockingDeque<CrawledNode>()
    queue.add(CrawledNode(this@crawl, 0))

    while (!queue.isEmpty()) {
        val cur = queue.poll()!!

        if (debug) log(TAG, VERBOSE) { cur.infoShort }

        yield(cur)

        cur.node.children().reversed().forEach { child ->
            queue.addFirst(CrawledNode(child, cur.level + 1))
        }
    }
}

// Recursive
fun AccessibilityNodeInfo.scrollNode(): Boolean {
    if (!isScrollable) return false

    log(TAG, VERBOSE) { "Scrolling node: ${toStringShort()}" }
    return performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
}

val AccessibilityEvent.pkgId: Pkg.Id? get() = packageName.takeIf { !it.isNullOrBlank() }?.toString()?.toPkgId()