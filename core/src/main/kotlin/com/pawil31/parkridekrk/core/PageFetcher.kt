package com.pawil31.parkridekrk.core

import org.jsoup.Jsoup

/** Downloads a page the way a mobile browser would. Blocking: call from a background thread. */
object PageFetcher {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val TIMEOUT_MS = 15_000

    fun fetch(url: String): String =
        Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .header("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8")
            .header("Cache-Control", "no-cache")
            .timeout(TIMEOUT_MS)
            .get()
            .outerHtml()
}
