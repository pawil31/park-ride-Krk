package com.pawil31.parkridekrk.data

import android.content.Context
import android.util.Log
import com.pawil31.parkridekrk.core.AVAILABILITY_URL
import com.pawil31.parkridekrk.core.PageContent
import com.pawil31.parkridekrk.core.PageContentExtractor
import kotlinx.coroutines.CancellationException

/**
 * Loads the text content of the ZTP availability page. The page is rendered with JavaScript so that
 * numbers filled in client-side are included; embedded frames (a possible data widget) are appended.
 */
class AvailabilityRepository(private val context: Context) {

    suspend fun load(): List<String> {
        val page = render(AVAILABILITY_URL)
        val frameLines = page.frameUrls.take(MAX_FRAMES).flatMap { frame ->
            try {
                render(frame).lines
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not load frame $frame", e)
                emptyList()
            }
        }
        return page.lines + frameLines
    }

    /** Renders [url] and returns its content once it has stopped changing. */
    private suspend fun render(url: String): PageContent {
        var previous: List<String>? = null
        var stablePolls = 0
        val html = WebViewHtmlLoader.load(context, url) { html ->
            val lines = PageContentExtractor.extract(html, url).lines
            stablePolls = if (lines.isNotEmpty() && lines == previous) stablePolls + 1 else 0
            previous = lines
            stablePolls >= STABLE_POLLS_NEEDED
        }
        return PageContentExtractor.extract(html, url)
    }

    private companion object {
        const val TAG = "AvailabilityRepository"
        const val MAX_FRAMES = 2
        const val STABLE_POLLS_NEEDED = 2
    }
}
