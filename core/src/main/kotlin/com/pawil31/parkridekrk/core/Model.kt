package com.pawil31.parkridekrk.core

/** Official ZTP page with live P+R availability. */
const val AVAILABILITY_URL = "https://ztp.krakow.pl/parkingi-pr/sprawdz-wolne-miejsca-pr"

data class ParkingAvailability(
    val name: String,
    val freeSpaces: Int?,
    val capacity: Int?,
    /** Other facts listed for this car park, e.g. spaces for disabled drivers. */
    val details: List<String> = emptyList(),
)

data class AvailabilitySnapshot(
    /** Car parks recognised on the page, in page order. Empty when the layout was not understood. */
    val parkings: List<ParkingAvailability>,
    /** Readable text of the page body without header, footer and navigation. Used as a fallback. */
    val contentLines: List<String>,
    /** "Last updated" text shown by the page, if any. */
    val sourceUpdated: String?,
    /** Embedded frames that may hold the actual data widget. */
    val frameUrls: List<String> = emptyList(),
)
