package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.util.Log
import android.webkit.JavascriptInterface

/**
 * JavascriptInterface bridge between WebView and Android for player events.
 * Receives watch progress data from the injected tracking script in the iframe HTML.
 */
class PlayerBridge(
    private val onEvent: (provider: String, positionSec: Double, season: Int?, episode: Int?) -> Unit
) {
    companion object {
        private const val TAG = "PlayerBridge"
    }

    @JavascriptInterface
    fun onPlayerEvent(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            val pos = if (obj.isNull("currentTime")) null else obj.getDouble("currentTime")
            val season = if (obj.isNull("season")) null else obj.getInt("season")
            val episode = if (obj.isNull("episode")) null else obj.getInt("episode")
            val provider = obj.optString("provider", "")

            if (pos != null) {
                Log.d(TAG, "[PlayerBridge] Event: provider=$provider, pos=${pos}s, season=$season, episode=$episode")
                onEvent(provider, pos, season, episode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[PlayerBridge] Failed to parse event: ${e.message}")
        }
    }
}
