package dev.instablock.instagram

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** The node's position on screen, in pixels. */
fun AccessibilityNodeInfo.bounds(): Rect = Rect().also { getBoundsInScreen(it) }

/** The node's resource id without the package prefix, such as "action_bar_title". Empty if none. */
fun AccessibilityNodeInfo.idName(): String = viewIdResourceName?.substringAfter(":id/") ?: ""

/** True if the node's text or content description is what Instagram shows for [label]. */
fun AccessibilityNodeInfo.hasLabel(label: Label): Boolean =
    Labels.matches(label, text) || Labels.matches(label, contentDescription)

/**
 * Reads Instagram's views through the accessibility service.
 *
 * Android keeps a cached copy of the screen for each accessibility service and refreshes it
 * from the events the service receives. That's why accessibility_config.xml subscribes to
 * every event type: without content-change events, this class would keep returning views
 * that have already changed or disappeared.
 */
class Screen(private val svc: AccessibilityService) {

    fun instagramInFront(): Boolean =
        svc.windows.any {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                (it.isActive || it.isFocused) &&
                it.root?.packageName == INSTAGRAM_PACKAGE
        } || svc.rootInActiveWindow?.packageName == INSTAGRAM_PACKAGE

    /** Number of Instagram windows. Popup menus open in a window of their own. */
    fun instagramWindowCount(): Int =
        svc.windows.count { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName == INSTAGRAM_PACKAGE }

    /** Every visible node in every Instagram window (main window, popup menus, sheets). */
    fun nodes(): List<AccessibilityNodeInfo> {
        val roots = svc.windows.mapNotNull { w -> w.root?.takeIf { it.packageName == INSTAGRAM_PACKAGE } }
            .ifEmpty { listOfNotNull(svc.rootInActiveWindow?.takeIf { it.packageName == INSTAGRAM_PACKAGE }) }
        val out = ArrayList<AccessibilityNodeInfo>(512)
        roots.forEach { collect(it, out) }
        return out
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.isVisibleToUser) out += node
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, out) }
    }

    /**
     * The area to tap to activate [node]. Labels are often plain text inside a clickable row, so
     * this is the nearest clickable ancestor, unless that ancestor is a large container (taller
     * than a third of the screen), in which case it's the node itself.
     */
    fun tapTarget(node: AccessibilityNodeInfo, screenHeight: Int): Rect {
        var current: AccessibilityNodeInfo? = node
        repeat(5) {
            val n = current ?: return node.bounds()
            if (n.isClickable) {
                val b = n.bounds()
                return if (b.height() < screenHeight / 3) b else node.bounds()
            }
            current = n.parent
        }
        return node.bounds()
    }
}
