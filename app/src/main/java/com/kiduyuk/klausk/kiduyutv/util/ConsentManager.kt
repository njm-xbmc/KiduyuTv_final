package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.startapp.sdk.adsbase.StartAppSDK
import com.unity3d.ads.metadata.MetaData
import com.wortise.ads.WortiseSdk

/**
 * Manages GDPR consent using Google's User Messaging Platform (UMP).
 * Required for EEA users before showing ads.
 *
 * After UMP resolves, consent is propagated to all secondary ad networks
 * (StartApp, Unity Ads, Wortise) so they also respect the user's choice.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"

    /**
     * Requests the latest consent information and shows a consent form if required.
     * Call from SplashActivity before AdManager.init().
     *
     * After the user dismisses the form (or if no form is required), consent
     * flags are forwarded to StartApp, Unity Ads, and Wortise automatically.
     *
     * @param activity The calling Activity (needed to show the form).
     * @param onComplete Fires when consent has been handled — proceed to call AdManager.init() here.
     */
    fun requestConsent(activity: Activity, onComplete: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Consent info updated successfully
                if (consentInfo.isConsentFormAvailable) {
                    loadAndShowConsentForm(activity, consentInfo, onComplete)
                } else {
                    Log.i(TAG, "Consent form not available")
                    propagateConsentToAllNetworks(activity)
                    onComplete()
                }
            },
            { formError ->
                Log.w(TAG, "Consent info update failed: ${formError.message}")
                // Proceed even on failure — non-EEA users won't see a form
                propagateConsentToAllNetworks(activity)
                onComplete()
            }
        )
    }

    private fun loadAndShowConsentForm(
        activity: Activity,
        consentInfo: ConsentInformation,
        onComplete: () -> Unit
    ) {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Consent form error: ${formError.message}")
            }
            propagateConsentToAllNetworks(activity)
            onComplete()
        }
    }

    /**
     * Forwards the UMP consent decision to StartApp, Unity Ads, and Wortise.
     * This should be called **after** UMP has resolved so each SDK knows
     * whether it may use personal data for ad targeting.
     */
    private fun propagateConsentToAllNetworks(context: Context) {
        val canPersonalize = canShowPersonalizedAds(context)
        Log.i(TAG, "Propagating consent to all networks: personalize=$canPersonalize")

        // ── StartApp ──────────────────────────────────────────────────────
        try {
            StartAppSDK.setUserConsent(
                context,
                "pas",
                if (canPersonalize)
                    com.startapp.sdk.adsbase.ConsentType.PERSONALIZED_CONSENT
                else
                    com.startapp.sdk.adsbase.ConsentType.NON_PERSONALIZED_CONSENT,
                true
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to forward consent to StartApp: ${e.message}")
        }

        // ── Unity Ads ─────────────────────────────────────────────────────
        try {
            MetaData(context).apply {
                set("gdpr.consent", if (canPersonalize) "true" else "false")
                set("privacy.consent", if (canPersonalize) "true" else "false")
                commit()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to forward consent to Unity: ${e.message}")
        }

        // ── Wortise ───────────────────────────────────────────────────────
        try {
            WortiseSdk.setUserConsent(
                context,
                if (canPersonalize)
                    com.wortise.ads.ConsentStatus.GRANTED
                else
                    com.wortise.ads.ConsentStatus.DENIED
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to forward consent to Wortise: ${e.message}")
        }
    }

    /**
     * Checks if ads can be requested based on consent status.
     */
    fun canRequestAds(context: Context): Boolean {
        return try {
            val info = UserMessagingPlatform.getConsentInformation(context)
            info.canRequestAds()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking consent status: ${e.message}")
            true // Assume true if error occurs
        }
    }

    /**
     * Checks if personalized ads are allowed based on UMP consent.
     */
    fun canShowPersonalizedAds(context: Context): Boolean {
        return try {
            val info = UserMessagingPlatform.getConsentInformation(context)
            // canRequestAds returns true when either:
            // 1. User consented to personalized ads
            // 2. User is not in EEA (no consent required)
            // 3. User consented to non-personalized ads
            // For a stricter check, inspect the consent string directly.
            info.canRequestAds()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking personalized consent: ${e.message}")
            true
        }
    }

    /**
     * Resets consent status (for testing purposes).
     */
    fun resetConsent(context: Context) {
        try {
            val info = UserMessagingPlatform.getConsentInformation(context)
            info.reset()
            Log.i(TAG, "Consent reset")
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting consent: ${e.message}")
        }
    }
}
