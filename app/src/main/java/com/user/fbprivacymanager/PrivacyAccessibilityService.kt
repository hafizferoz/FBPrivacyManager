package com.user.fbprivacymanager

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Conservative, user-directed UI automation.
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
        APPLYING_AUDIENCE,
        VERIFYING_AUDIENCE
    }

    private val browserPackages = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "org.chromium.webview_shell",
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.gx",
        "com.duckduckgo.mobile.android"
    )

    private var state = State.IDLE
    private var lastActionAt = 0L
    private var lastRecognizedSurface = ""
    private var postCount = 0
    private var verificationStartedAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audienceTimeout = Runnable {
        if (state == State.APPLYING_AUDIENCE || state == State.VERIFYING_AUDIENCE) {
            stopSafely("Facebook did not expose a clear result after selecting Only Me. Check the post manually; no further post was changed.")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        PrivacyAutomationController.initialize(this)
        PrivacyAutomationController.log("Accessibility service connected and ready.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!PrivacyAutomationController.running || event == null) return

        // Avoid acting too rapidly on repeated content-change events.
        val now = System.currentTimeMillis()
        if (now - lastActionAt < 800) return

        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: return

        val facebookApp = packageName == FACEBOOK_PACKAGE
        val facebookBrowser = packageName in browserPackages && isFacebookPage(root)
        if (!facebookApp && !facebookBrowser) {
            if (lastRecognizedSurface.isNotEmpty()) {
                PrivacyAutomationController.log("Facebook screen is no longer active; waiting safely.")
                lastRecognizedSurface = ""
                state = State.IDLE
            }
            return
        }

        val surface = if (facebookApp) "Facebook app" else "Facebook in browser"
        if (lastRecognizedSurface != surface) {
            lastRecognizedSurface = surface
            PrivacyAutomationController.log("Detected $surface. Waiting for Activity Log → Your posts.")
        }

        when (state) {
            State.IDLE -> detectActivityLog(root)
            State.ACTIVITY_LOG -> detectPostsSection(root)
            State.POSTS_LIST -> processVisiblePost(root)
            State.POST_MENU -> choosePrivacyMenu(root)
            State.AUDIENCE_MENU -> chooseOnlyMe(root)
            State.APPLYING_AUDIENCE -> applyAudienceChange(root)
            State.VERIFYING_AUDIENCE -> verifyOnlyMe(root)
        }
    }

    private fun detectActivityLog(root: AccessibilityNodeInfo) {
        if (hasText(root, listOf("Activity log", "Activity Log"))) {
            state = State.ACTIVITY_LOG
            logTransition("Activity Log found. Waiting for Your posts.")
        }
    }

    private fun detectPostsSection(root: AccessibilityNodeInfo) {
        if (hasText(root, listOf("Your posts", "Posts"))) {
            state = State.POSTS_LIST
            menuSearchLogged = false
            logTransition("Posts list found. Looking for a visible post options menu.")
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
                PrivacyAutomationController.log(
                    "Dry run: found a visible post menu. No privacy change was made; stop and restart with Dry Run off to apply."
                )
                PrivacyAutomationController.stop("Dry run finished without making changes.")
                state = State.IDLE
            } else if (menu.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                state = State.POST_MENU
                logTransition("Opened a visible post options menu.")
            } else {
                stopSafely("Could not open the visible post menu.")
            }
        } else if (!menuSearchLogged) {
            menuSearchLogged = true
            PrivacyAutomationController.log(
                "Posts list is active, but no accessible post options menu was found. Scroll to a post or use a supported Facebook screen."
            )
        }
    }

    private fun choosePrivacyMenu(root: AccessibilityNodeInfo) {
        val privacy = findClickableByText(
            root,
            listOf("Edit privacy", "Edit audience", "Privacy", "Audience")
        )

        if (privacy != null && privacy.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            state = State.AUDIENCE_MENU
            logTransition("Opened the post audience controls.")
        } else {
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

        if (onlyMe.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            state = State.APPLYING_AUDIENCE
            scheduleAudienceTimeout()
            logTransition("Selected Only Me for the visible post. Looking for Facebook’s Save/Done control.")
        } else {
            stopSafely("Could not select Only Me; no other action was taken.")
        }
    }

    private fun applyAudienceChange(root: AccessibilityNodeInfo) {
        val save = findClickableByText(root, listOf("Save", "Done"))
        if (save != null) {
            if (save.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                state = State.VERIFYING_AUDIENCE
                verificationStartedAt = System.currentTimeMillis()
                logTransition("Submitted Facebook’s Save/Done control. Waiting for the post to show its audience.")
            } else {
                stopSafely("Facebook showed Save/Done but the control could not be activated.")
            }
            return
        }

        if (!isAudiencePickerOpen(root)) {
            state = State.VERIFYING_AUDIENCE
            verificationStartedAt = System.currentTimeMillis()
            logTransition("Audience picker closed after selecting Only Me. Checking the post’s visible audience label.")
        }
    }

    private fun verifyOnlyMe(root: AccessibilityNodeInfo) {
        if (hasText(root, listOf("Only me", "Only Me")) && !isAudiencePickerOpen(root)) {
            postCount += 1
            PrivacyAutomationController.log(
                "Post $postCount: Facebook now visibly shows Only Me. Verify the result in Facebook; automation stopped after one post."
            )
            PrivacyAutomationController.stop("One post processed; start again only after reviewing the result.")
            state = State.IDLE
            mainHandler.removeCallbacks(audienceTimeout)
        } else if (System.currentTimeMillis() - verificationStartedAt > VERIFY_TIMEOUT_MS) {
            stopSafely("Facebook did not expose a clear Only Me result. Check the post manually; no further post was changed.")
        }
    }

    private fun stopSafely(message: String) {
        mainHandler.removeCallbacks(audienceTimeout)
        PrivacyAutomationController.stop("Stopped safely: $message")
        state = State.IDLE
        lastActionAt = System.currentTimeMillis()
    }

    private fun logTransition(message: String) {
        PrivacyAutomationController.log(message)
        lastActionAt = System.currentTimeMillis()
    }

    private fun scheduleAudienceTimeout() {
        mainHandler.removeCallbacks(audienceTimeout)
        mainHandler.postDelayed(audienceTimeout, VERIFY_TIMEOUT_MS)
    }

    private fun isFacebookPage(root: AccessibilityNodeInfo): Boolean {
        return visibleText(root).any { text ->
            text.contains("facebook.com", ignoreCase = true) ||
                text.contains("facebook", ignoreCase = true)
        }
    }

    private fun isAudiencePickerOpen(root: AccessibilityNodeInfo): Boolean {
        val hasAudienceOption = findClickableByText(root, listOf("Only Me", "Only me")) != null
        val hasOtherAudienceOption = hasText(root, listOf("Public", "Friends", "Friends except"))
        return hasAudienceOption && hasOtherAudienceOption
    }

    private fun visibleText(root: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            node.text?.toString()?.let(result::add)
            node.contentDescription?.toString()?.let(result::add)
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
        }
        return result
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
        PrivacyAutomationController.stop("Android interrupted the accessibility service.")
    }

    private var menuSearchLogged = false

    companion object {
        private const val FACEBOOK_PACKAGE = "com.facebook.katana"
        private const val VERIFY_TIMEOUT_MS = 10_000L
    }
}
