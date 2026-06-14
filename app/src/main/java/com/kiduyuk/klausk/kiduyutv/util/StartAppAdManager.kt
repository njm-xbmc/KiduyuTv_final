package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener

/**
 * StartApp (Start.io) Ad Manager — singleton.
 *
 * Handles banner, interstitial, rewarded video, and splash ads from StartApp.
 * All public methods are safe to call even if the SDK failed to initialise:
 * they simply no-op and invoke the callback so the app flow continues.
 *
 * Interstitial frequency is guarded by [MIN_INTERSTITIAL_INTERVAL_MS] (3 min).
 */
object StartAppAdManager {

    private const val TAG = "StartAppAdManager"
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 3 * 60 * 1000L

    @Volatile
    var isInitialised = false
        private set

    @Volatile
    private var lastInterstitialShownAt = 0L

    /**
     * Pre-load StartApp ads. Call once from [KiduyuTvApp.onCreate].
     */
    fun preloadAds(context: Context) {
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled - skipping StartApp preload")
            return
        }
        try {
            StartAppAd.init(context, StartAppAd.AUTOMATIC_LOAD)
            isInitialised = true
            Log.i(TAG, "StartApp ads pre-loaded")
        } catch (e: Exception) {
            Log.e(TAG, "StartApp preload failed", e)
        }
    }

    private fun shouldShowAds(context: Context): Boolean = try {
        !SettingsManager(context).isAdsDisabled()
    } catch (e: Exception) {
        true
    }

    // ── Banner ────────────────────────────────────────────────────────────

    /**
     * Loads a StartApp banner into the supplied [container].
     * The caller is responsible for placing the container in the layout.
     */
    fun loadBanner(activity: Activity, container: ViewGroup) {
        if (!shouldShowAds(activity)) return
        try {
            container.removeAllViews()
            val banner = Banner(activity)
            banner.setBannerListener(object : BannerListener {
                override fun onReceiveAd(view: Banner) {
                    Log.i(TAG, "StartApp banner received")
                }

                override fun onFailedToReceiveAd(view: Banner) {
                    Log.w(TAG, "StartApp banner failed")
                }

                override fun onClick(view: Banner) {
                    Log.i(TAG, "StartApp banner clicked")
                }

                override fun onImpression(view: Banner) {
                    Log.i(TAG, "StartApp banner impression")
                }
            })
            container.addView(
                banner,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load StartApp banner", e)
        }
    }

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Shows a StartApp interstitial if ads are enabled and the cooldown has elapsed.
     * Always calls [onDismissed] when done (or immediately if skipped).
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        if (!shouldShowAds(activity)) {
            onDismissed()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAt < MIN_INTERSTITIAL_INTERVAL_MS) {
            onDismissed()
            return
        }

        try {
            val startAppAd = StartAppAd(activity)
            startAppAd.setAdDisplayListener(object : AdDisplayListener {
                override fun onHidden(ad: Ad?) {
                    Log.i(TAG, "StartApp interstitial hidden")
                    onDismissed()
                }
            })
            startAppAd.loadAd(object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    Log.i(TAG, "StartApp interstitial loaded")
                    startAppAd.showAd()
                    lastInterstitialShownAt = System.currentTimeMillis()
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    Log.w(TAG, "StartApp interstitial failed to load")
                    onDismissed()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show StartApp interstitial", e)
            onDismissed()
        }
    }

    // ── Rewarded ──────────────────────────────────────────────────────────

    /**
     * Shows a StartApp rewarded video ad.
     * [onRewarded] fires when the user completes the video.
     * [onDismissed] always fires when the ad closes.
     */
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        if (!shouldShowAds(activity)) {
            onDismissed()
            return
        }
        try {
            val rewardedVideo = StartAppAd(activity)
            rewardedVideo.setVideoListener(object :
                com.startapp.sdk.adsbase.VideoListener {
                override fun onVideoCompleted() {
                    Log.i(TAG, "StartApp rewarded video completed")
                    onRewarded()
                }
            })
            rewardedVideo.setAdDisplayListener(object : AdDisplayListener {
                override fun onHidden(ad: Ad?) {
                    Log.i(TAG, "StartApp rewarded hidden")
                    onDismissed()
                }
            })
            rewardedVideo.loadAd(object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    Log.i(TAG, "StartApp rewarded loaded")
                    rewardedVideo.showAd()
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    Log.w(TAG, "StartApp rewarded failed")
                    onDismissed()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show StartApp rewarded", e)
            onDismissed()
        }
    }

    // ── Splash ────────────────────────────────────────────────────────────

    /**
     * Shows a StartApp splash ad. Call from [SplashActivity.onCreate].
     */
    fun showSplash(activity: Activity) {
        if (!shouldShowAds(activity)) return
        try {
            StartAppAd.showSplash(activity, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    Log.i(TAG, "StartApp splash shown")
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    Log.w(TAG, "StartApp splash failed")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "StartApp splash failed", e)
        }
    }

    // ── Lifecycle hooks ───────────────────────────────────────────────────

    fun onResume(activity: Activity) {
        if (shouldShowAds(activity)) try {
            StartAppAd.onResume(activity)
        } catch (_: Exception) {
        }
    }

    fun onPause(activity: Activity) {
        if (shouldShowAds(activity)) try {
            StartAppAd.onPause(activity)
        } catch (_: Exception) {
        }
    }

    fun onBackPressed(activity: Activity) {
        if (shouldShowAds(activity)) try {
            StartAppAd.onBackPressed(activity)
        } catch (_: Exception) {
        }
    }
}
