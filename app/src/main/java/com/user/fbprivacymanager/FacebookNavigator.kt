package com.user.fbprivacymanager

import android.content.Context
import android.content.Intent
import android.widget.Toast

object FacebookNavigator {
    private const val FACEBOOK_PACKAGE = "com.facebook.katana"

    fun openFacebook(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(FACEBOOK_PACKAGE)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            PrivacyAutomationController.log("Opened the Facebook app. Sign in there if prompted.")
        } else {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.facebook.com/")
            )
            runCatching {
                context.startActivity(Intent.createChooser(browserIntent, "Open Facebook in browser"))
                PrivacyAutomationController.log(
                    "Facebook app not found. Opened facebook.com in a browser; sign in on Facebook. " +
                        "FB Privacy Manager does not receive your password."
                )
            }.onFailure {
                PrivacyAutomationController.log("Could not open Facebook: no browser is available.")
                Toast.makeText(context, "Install or enable a browser to open Facebook.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
