package com.kiduyuk.klausk.kiduyutv.ui.screens.trakt

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kiduyuk.klausk.kiduyutv.databinding.ActivityTraktAuthBinding
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import kotlinx.coroutines.launch

/**
 * Activity that handles Trakt.tv OAuth 2.0 authentication.
 * Uses a WebView to load the Trakt authorization page and captures the OAuth callback.
 */
class TraktAuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTraktAuthBinding
    private lateinit var traktAuthManager: TraktAuthManager

    companion object {
        const val EXTRA_RESULT = "result"
        const val RESULT_SUCCESS = "success"
        const val RESULT_CANCELLED = "cancelled"
        const val RESULT_ERROR = "error"

        // OAuth callback URL - must match the one registered on Trakt.tv API
        private const val REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTraktAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        traktAuthManager = TraktAuthManager.getInstance(this)

        setupViews()
        startAuthentication()
    }

    private fun setupViews() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Retry button
        binding.btnRetry.setOnClickListener {
            startAuthentication()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startAuthentication() {
        showLoading("Preparing authentication...")

        // Build the authorization URL
        val authUrl = traktAuthManager.getAuthorizationUrl(REDIRECT_URI)

        // Configure WebView
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.userAgentString = "KiduyuTV Android"

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false

                    // Check if this is our callback URL
                    if (url.startsWith(REDIRECT_URI) || url.contains("code=")) {
                        // Extract the authorization code
                        val code = extractCodeFromUrl(url)
                        if (code != null) {
                            handleAuthorizationCode(code)
                        } else {
                            // User might have denied access
                            handleCancellation()
                        }
                        return true
                    }

                    // Allow navigation to Trakt.tv
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    showWebView()
                }
            }
        }

        // Load the authorization URL
        binding.webView.loadUrl(authUrl)
    }

    private fun extractCodeFromUrl(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            uri.getQueryParameter("code")
        } catch (e: Exception) {
            null
        }
    }

    private fun handleAuthorizationCode(code: String) {
        showLoading("Exchanging code for tokens...")

        lifecycleScope.launch {
            try {
                val success = traktAuthManager.exchangeCodeForTokens(code)

                if (success) {
                    Toast.makeText(
                        this@TraktAuthActivity,
                        "Successfully connected to Trakt.tv!",
                        Toast.LENGTH_SHORT
                    ).show()

                    setResult(RESULT_OK, Intent().apply {
                        putExtra(EXTRA_RESULT, RESULT_SUCCESS)
                    })
                } else {
                    showError("Failed to exchange authorization code for tokens.")
                }
            } catch (e: Exception) {
                showError("Authentication error: ${e.message}")
            } finally {
                finish()
            }
        }
    }

    private fun handleCancellation() {
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_RESULT, RESULT_CANCELLED)
        })
        finish()
    }

    private fun showLoading(message: String) {
        binding.loadingContainer.visibility = android.view.View.VISIBLE
        binding.webView.visibility = android.view.View.GONE
        binding.errorContainer.visibility = android.view.View.GONE
        binding.tvLoadingMessage.text = message
    }

    private fun showWebView() {
        binding.loadingContainer.visibility = android.view.View.GONE
        binding.webView.visibility = android.view.View.VISIBLE
        binding.errorContainer.visibility = android.view.View.GONE
    }

    private fun showError(message: String) {
        binding.loadingContainer.visibility = android.view.View.GONE
        binding.webView.visibility = android.view.View.GONE
        binding.errorContainer.visibility = android.view.View.VISIBLE
        binding.tvErrorMessage.text = message

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onBackPressed() {
        // If WebView can go back, go back; otherwise cancel the auth
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            handleCancellation()
        }
    }
}