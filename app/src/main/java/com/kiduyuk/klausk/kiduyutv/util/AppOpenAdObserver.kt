package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Lifecycle observer that shows an app open ad when the app comes to the
 * foreground, respecting a minimum 4-hour interval between shows.
 *
 * Priority: AdMob app open → Wortise app open.
 *
 * Usage: instantiate once from [KiduyuTvApp.onCreate]:
 * ```kotlin
 * AppOpenAdObserver(this)
 * ```
 */
class AppOpenAdObserver(private val application: Application) :
    Application.ActivityLifecycleCallbacks {

    private companion object {
        const val TAG = "AppOpenAdObserver"
        const val MIN_APP_OPEN_INTERVAL_MS = 4 * 60 * 60 * 1000L // 4 hours
    }

    private var currentActivity: Activity? = null
    private var lastAppOpenShownAt = 0L

    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                    onAppForegrounded()
                }
            }
        )
    }

    private fun onAppForegrounded() {
        val now = System.currentTimeMillis()
        if (now - lastAppOpenShownAt < MIN_APP_OPEN_INTERVAL_MS) return

        currentActivity?.let { activity ->
            try {
                // Try AdMob first, then fall back to Wortise
                if (AdManager.isInterstitialReady) {
                    Log.i(TAG, "Showing AdMob app open")
                    AdManager.showInterstitial(activity) { }
                } else {
                    Log.i(TAG, "Falling back to Wortise app open")
                    WortiseAdManager.showAppOpenIfAvailable(activity)
                }
                lastAppOpenShownAt = now
            } catch (e: Exception) {
                Log.w(TAG, "App open show failed", e)
            }
        }
    }

    // ── ActivityLifecycleCallbacks ────────────────────────────────────────

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityStopped(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
