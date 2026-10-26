package com.pawil31.parkridekrk.core

import org.jsoup.Jsoup

data class ParkingAvailability(
    val name: String,
    val freeSpaces: Int,
    /** How full the car park is, from the colour of the page's progress bar. */
    val occupancy: Occupancy?,
)

enum class Occupancy { LOW, HIGH, FULL }

/**
 * Reads the car parks from the ZTP page. Each one is a card like:
 *
 * ```html
 * <div class="col-xl-6 ..."><p class="fw-bold">P+R Kurdwanów</p><h5>Wolnych: 102</h5>
 *   <div class="progress"><div class="progress-bar bg-success" style="width:34.6%"></div></div></div>
 * ```
 */
object ParkingParser {

    private val NUMBER = Regex("""\d+""")

    fun parse(html: String): List<ParkingAvailability> {
        val document = Jsoup.parse(html)
        val scope = document.selectFirst("main") ?: document
        return scope.select("p.fw-bold").mapNotNull { title ->
            val card = title.parent() ?: return@mapNotNull null
            val free = card.selectFirst("h5")?.text()?.let(NUMBER::find)?.value?.toInt()
                ?: return@mapNotNull null
            val bar = card.selectFirst(".progress-bar")
            val occupancy = when {
                bar == null -> null
                bar.hasClass("bg-danger") -> Occupancy.FULL
                bar.hasClass("bg-warning") -> Occupancy.HIGH
                bar.hasClass("bg-success") -> Occupancy.LOW
                else -> null
            }
            ParkingAvailability(title.text().trim(), free, occupancy)
        }
    }
}
