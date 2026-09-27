package com.user.fbprivacymanager

object PrivacyAutomationController {
    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var dryRun: Boolean = true
        private set

    fun start(isDryRun: Boolean) {
        dryRun = isDryRun
        running = true
    }

    fun stop() {
        running = false
    }
}
