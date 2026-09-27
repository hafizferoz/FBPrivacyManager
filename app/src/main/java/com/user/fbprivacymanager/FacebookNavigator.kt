package com.feroz.fbprivacymanager

import android.content.Context
import android.content.Intent

object FacebookNavigator {
    private const val FACEBOOK_PACKAGE = "com.facebook.katana"

    fun openFacebook(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(FACEBOOK_PACKAGE)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        } else {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://www.facebook.com/")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
