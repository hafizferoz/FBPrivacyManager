# FB Privacy Manager

An Android Accessibility-based utility intended to help change the audience of
your own Facebook posts to **Only me** through the normal Facebook UI.

## Important

This project is intentionally conservative.

Facebook's UI changes frequently and can differ by:
- Facebook app version
- Android version
- account/feature rollout
- language
- accessibility implementation

Therefore the included service is a **conservative one-post-at-a-time helper**,
not a bulk processor or a promise that it will work unchanged against every
Facebook release. It stops after one visible post; bulk traversal and progress
tracking across a complete history are not implemented.

It never asks for or stores a Facebook password, access token, or cookies.


## Build

Open this folder in Android Studio.

Recommended:
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35

Build:
`./gradlew assembleDebug`

The APK will be:
`app/build/outputs/apk/debug/app-debug.apk`

## Install

Enable USB debugging on your Android phone and install the debug APK from
Android Studio, or use:

`adb install app/build/outputs/apk/debug/app-debug.apk`

## Enable Accessibility Service

Android Settings → Accessibility → Installed apps / Downloaded apps →
FB Privacy Manager → Allow.

The exact menu names vary by phone manufacturer.

## First test

1. Enable the service.
2. Keep **Dry Run** enabled.
3. Tap **Open Facebook**. The app opens Facebook if installed, otherwise it
	opens facebook.com in an available browser. Sign in only on Facebook.
4. Navigate manually to Activity Log → Your posts.
5. Return to FB Privacy Manager and tap **Start**.
6. Switch back to Facebook and keep Your posts in the foreground.
7. Check **Recent activity** in the manager for detection and status messages.
8. Confirm the dry-run behavior before turning Dry Run off. Write mode handles
	one visible post and stops; verify the result in Facebook before proceeding.

## Safety behavior

The service:
- only responds while explicitly started
- only responds to Facebook or a recognized browser displaying Facebook
- uses accessibility node text/content descriptions
- does not blind-tap screen coordinates
- stops if an expected privacy control cannot be positively identified
- supports a dry-run mode
- keeps a local, limited recent-activity log (without post text or credentials)
- does not collect credentials

## Production improvements

For a robust release, add:
- Room database for per-post progress
- foreground service notification
- resume/retry state machine
- locale-specific labels
- stronger post ownership detection
- screenshot/debug-tree capture with explicit user consent
- rate limiting
- manual confirmation before every write action during initial calibration
- handling for already-Only-Me posts
- handling for pagination/infinite scrolling
- explicit detection of "post owner" vs tagged/shared content
- tests against the exact Facebook version installed on the target device

## Privacy

The utility itself does not need network access. Accessibility access is
powerful and should be granted only to software you trust.

## Codemagic build

This project includes `codemagic.yaml` with a debug APK workflow and an unsigned release workflow.

1. Put the project in a Git repository (GitHub is convenient).
2. Add the repository to Codemagic.
3. Let Codemagic detect `codemagic.yaml`.
4. Run the `android-debug` workflow.
5. Download `app-debug.apk` from the build artifacts and install it on your Android phone.

The release workflow is intentionally unsigned. For a release-signed APK, use a keystore you control and configure Codemagic Android signing; never commit the keystore or passwords to the repository.

Test the app in Dry Run first. Facebook's UI can vary by app version, language, account, and feature rollout.
