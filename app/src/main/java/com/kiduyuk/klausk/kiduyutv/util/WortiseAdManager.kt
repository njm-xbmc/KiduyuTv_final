package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.wortise.ads.WortiseError
import com.wortise.ads.banner.WortiseBannerAdSize
import com.wortise.ads.banner.WortiseBannerAdView
import com.wortise.ads.banner.WortiseBannerListener
import com.wortise.ads.interstitial.WortiseInterstitial
import com.wortise.ads.interstitial.WortiseInterstitialListener
import com.wortise.ads.rewarded.WortiseRewarded
import com.wortise.ads.rewarded.WortiseRewardedListener
import com.wortise.ads.appopen.WortiseAppOpen
import com.wortise.ads.appopen.WortiseAppOpenListener

/**
 * Wortise Ad Manager — singleton.
 *
 * Handles banner, interstitial, rewarded video, and app open ads from Wortise.
 * Safe to call even if the SDK failed to initialise: methods no-op and
 * invoke callbacks so the app flow continues.
 *
 * Interstitial frequency is guarded by [MIN_INTERSTITIAL_INTERVAL_MS] (3 min).
 *
 * **IMPORTANT:** Replace the placeholder [AD_UNIT_…] constants with your
 * actual Wortise dashboard ad unit IDs before release.
 */
object WortiseAdManager {

    private const val TAG = "WortiseAdManager"
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 3 * 60 * 1000L

    // TODO: Replace these placeholders with your real Wortise ad unit IDs
    private const val AD_UNIT_BANNER = "wortise-banner-id"
    private const val AD_UNIT_INTERSTITIAL = "wortise-interstitial-id"
    private const val AD_UNIT_REWARDED = "wortise-rewarded-id"
    private const val AD_UNIT_APP_OPEN = "wortise-app-open-id"

    @Volatile
    var isInitialised = false
        private set

    @Volatile
    private var lastInterstitialShownAt = 0L

    @Volatile
    private var interstitialAd: WortiseInterstitial? = null

    @Volatile
    private var rewardedAd: WortiseRewarded? = null

    @Volatile
    private var appOpenAd: WortiseAppOpen? = null

    private fun shouldShowAds(context: Context): Boolean = try {
        !SettingsManager(context).isAdsDisabled()
    } catch (e: Exception) {
        true
    }

    /**
     * Pre-load Wortise ads. Call once from [KiduyuTvApp] after Wortise SDK init.
     */
    fun preloadAds(context: Context) {
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled - skipping Wortise preload")
            return
        }
        try {
            interstitialAd = WortiseInterstitial(context, AD_UNIT_INTERSTITIAL).apply {
                listener = createInterstitialListener(context as? Activity, null)
                load()
            }
            rewardedAd = WortiseRewarded(context, AD_UNIT_REWARDED).apply {
                listener = createRewardedListener(null, null)
                load()
            }
            appOpenAd = WortiseAppOpen(context, AD_UNIT_APP_OPEN).apply {
                listener = createAppOpenListener(null)
                load()
            }
            isInitialised = true
            Log.i(TAG, "Wortise ads pre-loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Wortise preload failed", e)
        }
    }

    // ── Banner ────────────────────────────────────────────────────────────

    /**
     * Loads a Wortise banner into the supplied [container].
     */
    fun loadBanner(activity: Activity, container: ViewGroup) {
        if (!shouldShowAds(activity)) return
        try {
            container.removeAllViews()
            val bannerView = WortiseBannerAdView(activity).apply {
                setAdUnitId(AD_UNIT_BANNER)
                setAdSize(WortiseBannerAdSize.BANNER)
                listener = object : WortiseBannerListener {
                    override fun onWortiseBannerLoaded(view: WortiseBannerAdView) {
                        Log.i(TAG, "Wortise banner loaded")
                    }

                    override fun onWortiseBannerFailedToLoad(
                        view: WortiseBannerAdView,
                        error: WortiseError
                    ) {
                        Log.w(TAG, "Wortise banner failed: ${error.message}")
                    }

                    override fun onWortiseBannerClicked(view: WortiseBannerAdView) {
                        Log.i(TAG, "Wortise banner clicked")
                    }

                    override fun onWortiseBannerShown(view: WortiseBannerAdView) {
                        Log.i(TAG, "Wortise banner shown")
                    }

                    override fun onWortiseBannerDismissed(view: WortiseBannerAdView) {
                        Log.i(TAG, "Wortise banner dismissed")
                    }
                }
            }
            container.addView(
                bannerView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            bannerView.load()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wortise banner", e)
        }
    }

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Shows a Wortise interstitial if ready and the cooldown has elapsed.
     * Always calls [onDismissed] when done.
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

        val ad = interstitialAd
        if (ad == null || !ad.isAvailable) {
            Log.i(TAG, "Wortise interstitial not ready")
            onDismissed()
            loadInterstitialAd(activity)
            return
        }
        try {
            ad.listener = createInterstitialListener(activity, onDismissed)
            ad.show()
            lastInterstitialShownAt = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Wortise interstitial", e)
            onDismissed()
        }
    }

    private fun loadInterstitialAd(context: Context) {
        try {
            interstitialAd = WortiseInterstitial(context, AD_UNIT_INTERSTITIAL).apply {
                listener = createInterstitialListener(context as? Activity, null)
                load()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wortise interstitial", e)
        }
    }

    private fun createInterstitialListener(
        activity: Activity?,
        onDismissed: (() -> Unit)?
    ): WortiseInterstitialListener {
        return object : WortiseInterstitialListener {
            override fun onWortiseInterstitialLoaded(ad: WortiseInterstitial) {
                Log.i(TAG, "Wortise interstitial loaded")
            }

            override fun onWortiseInterstitialFailedToLoad(
                ad: WortiseInterstitial,
                error: WortiseError
            ) {
                Log.w(TAG, "Wortise interstitial failed: ${error.message}")
            }

            override fun onWortiseInterstitialShown(ad: WortiseInterstitial) {
                Log.i(TAG, "Wortise interstitial shown")
            }

            override fun onWortiseInterstitialFailedToShow(
                ad: WortiseInterstitial,
                error: WortiseError
            ) {
                Log.w(TAG, "Wortise interstitial failed to show: ${error.message}")
                onDismissed?.invoke()
            }

            override fun onWortiseInterstitialClicked(ad: WortiseInterstitial) {
                Log.i(TAG, "Wortise interstitial clicked")
            }

            override fun onWortiseInterstitialDismissed(ad: WortiseInterstitial) {
                Log.i(TAG, "Wortise interstitial dismissed")
                interstitialAd = null
                activity?.let { loadInterstitialAd(it) }
                onDismissed?.invoke()
            }
        }
    }

    // ── Rewarded ──────────────────────────────────────────────────────────

    /**
     * Shows a Wortise rewarded video ad.
     * [onRewarded] fires when the user earns the reward.
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
        val ad = rewardedAd
        if (ad == null || !ad.isAvailable) {
            Log.i(TAG, "Wortise rewarded not ready")
            onDismissed()
            loadRewardedAd(activity)
            return
        }
        try {
            ad.listener = createRewardedListener(onRewarded, onDismissed)
            ad.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Wortise rewarded", e)
            onDismissed()
        }
    }

    private fun loadRewardedAd(context: Context) {
        try {
            rewardedAd = WortiseRewarded(context, AD_UNIT_REWARDED).apply {
                listener = createRewardedListener(null, null)
                load()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wortise rewarded", e)
        }
    }

    private fun createRewardedListener(
        onRewarded: (() -> Unit)?,
        onDismissed: (() -> Unit)?
    ): WortiseRewardedListener {
        return object : WortiseRewardedListener {
            override fun onWortiseRewardedLoaded(ad: WortiseRewarded) {
                Log.i(TAG, "Wortise rewarded loaded")
            }

            override fun onWortiseRewardedFailedToLoad(
                ad: WortiseRewarded,
                error: WortiseError
            ) {
                Log.w(TAG, "Wortise rewarded failed: ${error.message}")
            }

            override fun onWortiseRewardedShown(ad: WortiseRewarded) {
                Log.i(TAG, "Wortise rewarded shown")
            }

            override fun onWortiseRewardedFailedToShow(
                ad: WortiseRewarded,
                error: WortiseError
            ) {
                Log.w(TAG, "Wortise rewarded failed to show: ${error.message}")
                onDismissed?.invoke()
            }

            override fun onWortiseRewardedClicked(ad: WortiseRewarded) {
                Log.i(TAG, "Wortise rewarded clicked")
            }

            override fun onWortiseRewardedDismissed(ad: WortiseRewarded) {
                Log.i(TAG, "Wortise rewarded dismissed")
                rewardedAd = null
                onDismissed?.invoke()
            }

            override fun onWortiseRewardedCompleted(ad: WortiseRewarded) {
                Log.i(TAG, "Wortise rewarded completed")
                onRewarded?.invoke()
            }
        }
    }

    // ── App Open ──────────────────────────────────────────────────────────

    /**
     * Shows a Wortise app open ad if one is available.
     * Best used via [AppOpenAdObserver] for automatic foreground detection.
     */
    fun showAppOpenIfAvailable(activity: Activity) {
        if (!shouldShowAds(activity)) return
        val ad = appOpenAd
        if (ad == null || !ad.isAvailable) {
            Log.i(TAG, "Wortise app open not ready")
            loadAppOpenAd(activity)
            return
        }
        try {
            ad.listener = createAppOpenListener(null)
            ad.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Wortise app open", e)
        }
    }

    private fun loadAppOpenAd(context: Context) {
        try {
            appOpenAd = WortiseAppOpen(context, AD_UNIT_APP_OPEN).apply {
                listener = createAppOpenListener(null)
                load()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wortise app open", e)
        }
    }

    private fun createAppOpenListener(onDismissed: (() -> Unit)?): WortiseAppOpenListener {
        return object : WortiseAppOpenListener {
            override fun onWortiseAppOpenLoaded(ad: WortiseAppOpen) {
                Log.i(TAG, "Wortise app open loaded")
            }

            override fun onWortiseAppOpenFailedToLoad(
                ad: WortiseAppOpen,
                error: WortiseError
            ) {
                Log.w(TAG, "Wortise app open failed: ${error.message}")
            }

            override fun onWortiseAppOpenShown(ad: WortiseAppOpen) {
                Log.i(TAG, "Wortise app open shown")
            }

            override fun onWortiseAppOpenFailedToShow(
                ad: WortiseAppOpen,
                error: WortiseError
            ) {
                Log.w(TAG, "Wortise app open failed to show: ${error.message}")
                onDismissed?.invoke()
            }

            override fun onWortiseAppOpenClicked(ad: WortiseAppOpen) {
                Log.i(TAG, "Wortise app open clicked")
            }

            override fun onWortiseAppOpenDismissed(ad: WortiseAppOpen) {
                Log.i(TAG, "Wortise app open dismissed")
                appOpenAd = null
                onDismissed?.invoke()
            }
        }
    }

    // ── Readiness checks ──────────────────────────────────────────────────

    val isInterstitialReady: Boolean
        get() = interstitialAd?.isAvailable == true

    val isRewardedReady: Boolean
        get() = rewardedAd?.isAvailable == true

    val isAppOpenReady: Boolean
        get() = appOpenAd?.isAvailable == true
}
