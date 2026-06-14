import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.wortise.ads.WortiseError
import com.wortise.ads.AdError
import com.wortise.ads.RevenueData
import com.wortise.ads.appopen.AppOpenAd
import com.wortise.ads.banner.AdSize
import com.wortise.ads.banner.BannerAd
import com.wortise.ads.interstitial.InterstitialAd
import com.wortise.ads.rewarded.Reward
import com.wortise.ads.rewarded.RewardedAd

/**
 * Wortise Ad Manager — singleton.
 *
 * **IMPORTANT:** Replace the placeholder [AD_UNIT_…] constants with your
 * actual Wortise dashboard ad unit IDs before release.
 * are `BannerAd`, `InterstitialAd`, `RewardedAd`, and `AppOpenAd`, each with
 * a nested `Listener` abstract class. Errors are reported via `AdError` and
 * revenue events via `RevenueData`. Double-check the import package paths
 * for `AdError`, `AdSize`, `RevenueData`, and `Reward` against the SDK
 * version you have installed (use Android Studio's auto-import if any of
 * these don't resolve).
 */
object WortiseAdManager {

    private var lastInterstitialShownAt = 0L

    @Volatile
    private var interstitialAd: WortiseInterstitial? = null
    @Volatile
    private var rewardedAd: RewardedAd? = null

    @Volatile

    @Volatile

    private fun shouldShowAds(context: Context): Boolean = try {
        !SettingsManager(context).isAdsDisabled()
            return
        }
        try {
            interstitialAd = WortiseInterstitial(context, AD_UNIT_INTERSTITIAL).apply {
                listener = createInterstitialListener(context as? Activity, null)
                load()
                loadAd()
            }
            rewardedAd = WortiseRewarded(context, AD_UNIT_REWARDED).apply {
            rewardedAd = RewardedAd(context, AD_UNIT_REWARDED).apply {
                listener = createRewardedListener(null, null)
                load()
                loadAd()
            }
            appOpenAd = WortiseAppOpen(context, AD_UNIT_APP_OPEN).apply {
            appOpenAd = AppOpenAd(context, AD_UNIT_APP_OPEN).apply {
                autoReload = true
                listener = createAppOpenListener(null)
                loadAd()
            }
            isInitialised = true
            Log.i(TAG, "Wortise ads pre-loaded")
    fun loadBanner(activity: Activity, container: ViewGroup) {
        if (!shouldShowAds(activity)) return
        try {
            container.removeAllViews()
            val bannerView = WortiseBannerAdView(activity).apply {
                setAdUnitId(AD_UNIT_BANNER)
                setAdSize(WortiseBannerAdSize.BANNER)
                    override fun onWortiseBannerLoaded(view: WortiseBannerAdView) {

            val bannerAd = BannerAd(activity).apply {
                adUnitId = AD_UNIT_BANNER
                adSize = AdSize.HEIGHT_50
                listener = object : BannerAd.Listener() {
                    override fun onBannerLoaded(ad: BannerAd) {
                        Log.i(TAG, "Wortise banner loaded")
                    }

                        error: WortiseError
                    ) {
                        Log.w(TAG, "Wortise banner failed: ${error.message}")
                    }

                    override fun onBannerClicked(ad: BannerAd) {
                        Log.i(TAG, "Wortise banner clicked")
                    }

                    }

                    override fun onBannerRevenuePaid(ad: BannerAd, data: RevenueData) {
                    }
                }
            }

            currentBannerAd = bannerAd
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

    /** Call from the host Activity's onResume(). */
    fun resumeBanner() {
        currentBannerAd?.resume()
    }

    /** Call from the host Activity's onDestroy(). */
    fun destroyBanner() {
        currentBannerAd?.destroy()
        currentBannerAd = null
    }

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
        }
        try {
            ad.listener = createInterstitialListener(activity, onDismissed)
            ad.show()
            lastInterstitialShownAt = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Wortise interstitial", e)

    private fun loadInterstitialAd(context: Context) {
        try {
            interstitialAd = WortiseInterstitial(context, AD_UNIT_INTERSTITIAL).apply {
                listener = createInterstitialListener(context as? Activity, null)
                load()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wortise interstitial", e)
    private fun createInterstitialListener(
        activity: Activity?,
        onDismissed: (() -> Unit)?
        return object : WortiseInterstitialListener {
            override fun onWortiseInterstitialLoaded(ad: WortiseInterstitial) {
                Log.i(TAG, "Wortise interstitial loaded")
            }

            override fun onInterstitialFailedToLoad(ad: InterstitialAd, error: AdError) {
                Log.w(TAG, "Wortise interstitial failed: ${error.message}")
            }

            override fun onInterstitialShown(ad: InterstitialAd) {
                Log.i(TAG, "Wortise interstitial shown")
            }

            override fun onWortiseInterstitialFailedToShow(
                ad: WortiseInterstitial,
                error: WortiseError
            override fun onInterstitialImpression(ad: InterstitialAd) {
            override fun onInterstitialRevenuePaid(ad: InterstitialAd, data: RevenueData) {
                Log.i(TAG, "Wortise interstitial revenue paid")
            }

            override fun onInterstitialFailedToShow(ad: InterstitialAd, error: AdError) {
                Log.w(TAG, "Wortise interstitial failed to show: ${error.message}")
                onDismissed?.invoke()
            }

            override fun onInterstitialClicked(ad: InterstitialAd) {
                Log.i(TAG, "Wortise interstitial clicked")
            }

            override fun onWortiseInterstitialDismissed(ad: WortiseInterstitial) {
                Log.i(TAG, "Wortise interstitial dismissed")
                interstitialAd = null
                activity?.let { loadInterstitialAd(it) }
        }
        try {
            ad.listener = createRewardedListener(onRewarded, onDismissed)
            ad.showAd(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Wortise rewarded", e)
            onDismissed()

    private fun loadRewardedAd(context: Context) {
        try {
            rewardedAd = WortiseRewarded(context, AD_UNIT_REWARDED).apply {
            rewardedAd = RewardedAd(context, AD_UNIT_REWARDED).apply {
                listener = createRewardedListener(null, null)
                load()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wortise rewarded", e)
    private fun createRewardedListener(
        onRewarded: (() -> Unit)?,
        onDismissed: (() -> Unit)?
        return object : WortiseRewardedListener {
    ): RewardedAd.Listener {
                Log.i(TAG, "Wortise rewarded loaded")
            }

            override fun onWortiseRewardedFailedToLoad(
                ad: WortiseRewarded,
            override fun onRewardedFailedToLoad(ad: RewardedAd, error: AdError) {
                Log.w(TAG, "Wortise rewarded failed: ${error.message}")
            }

            override fun onWortiseRewardedShown(ad: WortiseRewarded) {
                Log.i(TAG, "Wortise rewarded shown")
                Log.i(TAG, "Wortise rewarded impression")
            }

            override fun onRewardedRevenuePaid(ad: RewardedAd, data: RevenueData) {
                Log.i(TAG, "Wortise rewarded revenue paid")
            }

            override fun onWortiseRewardedFailedToShow(
            override fun onRewardedFailedToShow(ad: RewardedAd, error: AdError) {
                Log.w(TAG, "Wortise rewarded failed to show: ${error.message}")
                onDismissed?.invoke()
            }

            override fun onWortiseRewardedClicked(ad: WortiseRewarded) {
                Log.i(TAG, "Wortise rewarded clicked")
            }

            override fun onRewardedDismissed(ad: RewardedAd) {
                Log.i(TAG, "Wortise rewarded dismissed")
                rewardedAd = null
                onDismissed?.invoke()
            }

                Log.i(TAG, "Wortise rewarded completed")
                onRewarded?.invoke()
            }
    // ── App Open ──────────────────────────────────────────────────────────

    /**
     * Shows a Wortise app open ad if one is available.
     * Best used via [AppOpenAdObserver] for automatic foreground detection.
     * load so it's ready next time. [AppOpenAd.tryToShowAd] handles both
     * cases internally.
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

    private fun loadAppOpenAd(context: Context) {
        try {
            appOpenAd = WortiseAppOpen(context, AD_UNIT_APP_OPEN).apply {
                listener = createAppOpenListener(null)
                loadAd()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wortise app open", e)
        }
    }

    private fun createAppOpenListener(onDismissed: (() -> Unit)?): WortiseAppOpenListener {
    private fun createAppOpenListener(onDismissed: (() -> Unit)?): AppOpenAd.Listener {
            override fun onAppOpenLoaded(ad: AppOpenAd) {
                Log.i(TAG, "Wortise app open loaded")
            }

            override fun onWortiseAppOpenFailedToLoad(
                ad: WortiseAppOpen,
                error: WortiseError
            ) {
            override fun onAppOpenFailedToLoad(ad: AppOpenAd, error: AdError) {
                Log.w(TAG, "Wortise app open failed: ${error.message}")
            }

            override fun onAppOpenShown(ad: AppOpenAd) {
                Log.i(TAG, "Wortise app open shown")
            }

            override fun onAppOpenImpression(ad: AppOpenAd) {
            override fun onAppOpenRevenuePaid(ad: AppOpenAd, data: RevenueData) {
                Log.i(TAG, "Wortise app open revenue paid")
                Log.w(TAG, "Wortise app open failed to show: ${error.message}")
                onDismissed?.invoke()
            }

            override fun onAppOpenClicked(ad: AppOpenAd) {
                Log.i(TAG, "Wortise app open clicked")
            }

            override fun onAppOpenDismissed(ad: AppOpenAd) {
                Log.i(TAG, "Wortise app open dismissed")
                onDismissed?.invoke()
            }
        }
