package com.pawil31.parkridekrk.data

import android.content.Context
import android.util.Log
import com.pawil31.parkridekrk.core.AVAILABILITY_URL
import com.pawil31.parkridekrk.core.AvailabilityParser
import com.pawil31.parkridekrk.core.AvailabilitySnapshot
import com.pawil31.parkridekrk.core.PageFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads the ZTP availability page. Tries the cheap static download first, then a frame embedded in
 * the page, and finally renders the page with JavaScript in case the numbers are filled in client-side.
 */
class AvailabilityRepository(private val context: Context) {

    /** Set once static HTML turned out not to contain the data, to skip that request next time. */
    @Volatile
    private var needsJavaScript = false

    suspend fun load(): AvailabilitySnapshot {
        var fallback: AvailabilitySnapshot? = null

        if (!needsJavaScript) {
            val static = attempt("Static fetch") { fetchAndParse(AVAILABILITY_URL) }
            if (static != null) {
                if (static.parkings.isNotEmpty()) return static
                for (frame in static.frameUrls.take(MAX_FRAMES)) {
                    val framed = attempt("Frame fetch") { fetchAndParse(frame) }
                    if (framed != null && framed.parkings.isNotEmpty()) return framed
                }
                fallback = static
            }
        }

        val rendered = attempt("Rendered fetch") {
            val html = WebViewHtmlLoader.load(context, AVAILABILITY_URL) {
                AvailabilityParser.parse(it).parkings.isNotEmpty()
            }
            AvailabilityParser.parse(html)
        }

        if (rendered != null && rendered.parkings.isNotEmpty()) {
            needsJavaScript = true
            return rendered
        }
        return listOfNotNull(rendered, fallback).maxByOrNull { it.contentLines.size }
            ?: throw IllegalStateException("No response from $AVAILABILITY_URL")
    }

    /** Runs [block], logging and swallowing failures but never cancellation. */
    private inline fun <T> attempt(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what failed", e)
            null
        }

    private suspend fun fetchAndParse(url: String) = withContext(Dispatchers.IO) {
        AvailabilityParser.parse(PageFetcher.fetch(url), url)
    }

    private companion object {
        const val TAG = "AvailabilityRepository"
        const val MAX_FRAMES = 2
    }
}
