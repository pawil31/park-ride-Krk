package com.pawil31.parkridekrk

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pawil31.parkridekrk.core.AVAILABILITY_URL

/**
 * Phone screen: the ZTP availability page with header, footer and menus hidden, reloaded every minute.
 * The car screen lives in [com.pawil31.parkridekrk.car.ParkingCarAppService].
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private val cleanPageScript by lazy { assets.open("clean_page.js").bufferedReader().use { it.readText() } }

    private val reload = object : Runnable {
        override fun run() {
            webView.loadUrl(AVAILABILITY_URL)
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private val clean = Runnable { webView.evaluateJavascript(cleanPageScript, null) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // Run again after scripts that build the page late have had a chance to finish.
                handler.removeCallbacks(clean)
                clean.run()
                for (delay in CLEAN_RETRY_DELAYS_MS) handler.postDelayed(clean, delay)
            }
        }
        setContentView(webView)
    }

    override fun onResume() {
        super.onResume()
        handler.post(reload)
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 60_000L
        val CLEAN_RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L)
    }
}
