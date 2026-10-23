package com.pawil31.parkridekrk.data

import android.content.Context
import android.util.Log
import com.pawil31.parkridekrk.core.AVAILABILITY_URL
import com.pawil31.parkridekrk.core.PageContentExtractor
import com.pawil31.parkridekrk.core.PageFetcher
import com.pawil31.parkridekrk.core.ParkingAvailability
import com.pawil31.parkridekrk.core.ParkingParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Availability(
    /** Car parks read from the page, in page order. */
    val parkings: List<ParkingAvailability>,
    /** The page's text, filled only when no car parks could be read, so the driver still sees something. */
    val pageText: List<String>,
)

/**
 * Loads the ZTP availability page. The car parks are in the page's HTML, so a plain download is
 * enough; if that ever stops being the case, the page is rendered with JavaScript instead.
 */
class AvailabilityRepository(private val context: Context) {

    /** Set once the downloaded HTML turned out not to contain the car parks, to skip that request next time. */
    @Volatile
    private var needsJavaScript = false

    suspend fun load(): Availability {
        if (!needsJavaScript) {
            val html = try {
                withContext(Dispatchers.IO) { PageFetcher.fetch(AVAILABILITY_URL) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Download failed, trying the rendered page", e)
                null
            }
            if (html != null) {
                val parkings = ParkingParser.parse(html)
                if (parkings.isNotEmpty()) {
                    Log.i(TAG, "Downloaded page: ${parkings.size} car parks")
                    return Availability(parkings, emptyList())
                }
                Log.i(TAG, "Downloaded page has no car parks, switching to the rendered page")
                needsJavaScript = true
            }
        }

        val html = WebViewHtmlLoader.load(context, AVAILABILITY_URL) { ParkingParser.parse(it).isNotEmpty() }
        val parkings = ParkingParser.parse(html)
        Log.i(TAG, "Rendered page: ${parkings.size} car parks")
        return Availability(parkings, if (parkings.isEmpty()) PageContentExtractor.lines(html) else emptyList())
    }

    private companion object {
        const val TAG = "AvailabilityRepository"
    }
}
