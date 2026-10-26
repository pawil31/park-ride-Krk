package com.pawil31.parkridekrk.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Renders a page in an off-screen WebView so that content filled in by JavaScript is present,
 * then returns the resulting DOM as HTML.
 */
object WebViewHtmlLoader {

    private const val POLL_INTERVAL_MS = 1_000L
    private const val TIMEOUT_MS = 20_000L

    /**
     * Returns the rendered HTML as soon as [isComplete] accepts it, or the last snapshot on timeout.
     * Throws [IOException] when the page itself fails to load.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun load(context: Context, url: String, isComplete: (String) -> Boolean): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                val webView = WebView(context.applicationContext)
                val startedAt = System.currentTimeMillis()
                var lastHtml = ""
                var released = false

                fun release() {
                    if (released) return
                    released = true
                    handler.removeCallbacksAndMessages(null)
                    webView.stopLoading()
                    webView.destroy()
                }

                val finish = Runnable {
                    release()
                    if (continuation.isActive) continuation.resume(lastHtml)
                }

                lateinit var poll: Runnable
                poll = Runnable {
                    if (released) return@Runnable
                    webView.evaluateJavascript("document.documentElement.outerHTML") { result ->
                        if (released) return@evaluateJavascript
                        // The callback receives a JSON-encoded string literal.
                        lastHtml = runCatching { JSONArray("[$result]").getString(0) }.getOrDefault("")
                        val timedOut = System.currentTimeMillis() - startedAt > TIMEOUT_MS
                        if (timedOut || (lastHtml.isNotEmpty() && isComplete(lastHtml))) {
                            finish.run()
                        } else {
                            handler.postDelayed(poll, POLL_INTERVAL_MS)
                        }
                    }
                }

                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        // Redirects can finish more than once; keep a single polling loop.
                        handler.removeCallbacks(poll)
                        handler.post(poll)
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (!request.isForMainFrame) return
                        release()
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException(error.description.toString()))
                        }
                    }
                }
                // Safety net in case the page never finishes loading.
                handler.postDelayed(finish, TIMEOUT_MS + 5 * POLL_INTERVAL_MS)
                webView.loadUrl(url)

                continuation.invokeOnCancellation { handler.post { release() } }
            }
        }
}
