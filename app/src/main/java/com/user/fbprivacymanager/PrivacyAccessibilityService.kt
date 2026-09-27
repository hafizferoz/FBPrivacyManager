package com.feroz.fbprivacymanager

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Conservative UI automation skeleton.
 *
 * This service deliberately does not blindly click arbitrary coordinates.
 * Facebook changes its UI frequently, so each action is gated by visible
 * text/content descriptions and a small state machine.
 *
 * IMPORTANT:
 * Facebook may use different labels/layouts by app version, language,
 * account type, and feature rollout. The candidate labels below should be
 * validated on the user's actual device before enabling write actions.
 */
class PrivacyAccessibilityService : AccessibilityService() {

    private enum class State {
        IDLE,
        ACTIVITY_LOG,
        POSTS_LIST,
        POST_MENU,
        AUDIENCE_MENU,
        CONFIRMING
    }

    private var state = State.IDLE
    private var lastActionAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Toast.makeText(this, "FB Privacy Manager ready", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!PrivacyAutomationController.running || event == null) return

        // Avoid acting too rapidly on repeated content-change events.
        val now = System.currentTimeMillis()
        if (now - lastActionAt < 1200) return

        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: return

        if (packageName != "com.facebook.katana") return

        when (state) {
            State.IDLE -> detectActivityLog(root)
            State.ACTIVITY_LOG -> detectPostsSection(root)
            State.POSTS_LIST -> processVisiblePost(root)
            State.POST_MENU -> choosePrivacyMenu(root)
            State.AUDIENCE_MENU -> chooseOnlyMe(root)
            State.CONFIRMING -> verifyOnlyMe(root)
        }
    }

    private fun detectActivityLog(root: AccessibilityNodeInfo) {
        if (hasText(root, listOf("Activity log", "Activity Log"))) {
            state = State.ACTIVITY_LOG
        }
    }

    private fun detectPostsSection(root: AccessibilityNodeInfo) {
        if (hasText(root, listOf("Your posts", "Posts"))) {
            state = State.POSTS_LIST
        }
    }

    private fun processVisiblePost(root: AccessibilityNodeInfo) {
        // Conservative implementation: find a visible overflow/menu control.
        // Exact labels vary across Facebook releases and languages.
        val menu = findClickableByContentDescription(
            root,
            listOf("More", "More options", "Post options")
        )

        if (menu != null) {
            if (PrivacyAutomationController.dryRun) {
                Toast.makeText(
                    this,
                    "Dry run: found a post menu; no change made.",
                    Toast.LENGTH_SHORT
                ).show()
                lastActionAt = System.currentTimeMillis()
            } else if (menu.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                state = State.POST_MENU
                lastActionAt = System.currentTimeMillis()
            }
        }
    }

    private fun choosePrivacyMenu(root: AccessibilityNodeInfo) {
        val privacy = findClickableByText(
            root,
            listOf("Edit privacy", "Edit audience", "Privacy", "Audience")
        )

        if (privacy != null && privacy.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            state = State.AUDIENCE_MENU
            lastActionAt = System.currentTimeMillis()
        } else {
            // Never guess if Facebook doesn't expose an expected control.
            stopSafely("Privacy control not positively identified.")
        }
    }

    private fun chooseOnlyMe(root: AccessibilityNodeInfo) {
        val onlyMe = findClickableByText(
            root,
            listOf("Only me", "Only Me")
        )

        if (onlyMe == null) {
            stopSafely("Only Me option not positively identified.")
            return
        }

        if (PrivacyAutomationController.dryRun) {
            Toast.makeText(this, "Dry run: Only Me detected.", Toast.LENGTH_SHORT).show()
            PrivacyAutomationController.stop()
            state = State.IDLE
            return
        }

        if (onlyMe.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            state = State.CONFIRMING
            lastActionAt = System.currentTimeMillis()
        }
    }

    private fun verifyOnlyMe(root: AccessibilityNodeInfo) {
        if (hasText(root, listOf("Only me", "Only Me"))) {
            Toast.makeText(this, "Audience appears to be Only Me.", Toast.LENGTH_SHORT).show()
            // A production version should record a post identifier or stable
            // UI signature before advancing to the next post.
            state = State.POSTS_LIST
            lastActionAt = System.currentTimeMillis()
        }
    }

    private fun stopSafely(message: String) {
        PrivacyAutomationController.stop()
        Toast.makeText(this, "Stopped safely: $message", Toast.LENGTH_LONG).show()
        state = State.IDLE
    }

    private fun hasText(root: AccessibilityNodeInfo, candidates: List<String>): Boolean {
        return candidates.any { candidate ->
            root.findAccessibilityNodeInfosByText(candidate).isNotEmpty()
        }
    }

    private fun findClickableByText(
        root: AccessibilityNodeInfo,
        candidates: List<String>
    ): AccessibilityNodeInfo? {
        for (candidate in candidates) {
            val nodes = root.findAccessibilityNodeInfosByText(candidate)
            for (node in nodes) {
                if (node.isClickable) return node
                var parent = node.parent
                repeat(4) {
                    if (parent?.isClickable == true) return parent
                    parent = parent?.parent
                }
            }
        }
        return null
    }

    private fun findClickableByContentDescription(
        root: AccessibilityNodeInfo,
        candidates: List<String>
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString()
            if (desc != null &&
                candidates.any { it.equals(desc, ignoreCase = true) } &&
                node.isClickable
            ) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return null
    }

    override fun onInterrupt() {
        PrivacyAutomationController.stop()
    }
}
