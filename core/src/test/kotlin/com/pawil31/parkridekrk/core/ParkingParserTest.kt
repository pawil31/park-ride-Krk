package com.pawil31.parkridekrk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParkingParserTest {

    /** The ZTP page as saved from Chrome on 2026-10-22. */
    private val savedPage = javaClass.getResource("/sprawdz-wolne-miejsca-pr.html")!!.readText()

    @Test
    fun `reads all car parks from the saved ZTP page`() {
        assertEquals(
            listOf(
                ParkingAvailability("P+R Górka Narodowa", 316, Occupancy.LOW),
                ParkingAvailability("P+R Pachońskiego", 0, Occupancy.FULL),
                ParkingAvailability("P+R Krowodrza Górka", 3, Occupancy.FULL),
                ParkingAvailability("P+R Kurdwanów", 102, Occupancy.LOW),
                ParkingAvailability("P+R Czerwone Maki", 112, Occupancy.LOW),
                ParkingAvailability("P+R Bieżanów", 12, Occupancy.HIGH),
                ParkingAvailability("P+R Mały Płaszów", 120, Occupancy.LOW),
            ),
            ParkingParser.parse(savedPage),
        )
    }

    @Test
    fun `saved page text keeps the availability and drops header and footer`() {
        val lines = PageContentExtractor.lines(savedPage)

        assertEquals("P+R Górka Narodowa", lines[1])
        assertEquals("Wolnych: 316", lines[2])
        assertTrue(lines.none { "Znajdź nas" in it }, lines.toString())
        assertTrue(lines.none { "Strona główna" in it }, lines.toString())
    }

    @Test
    fun `skips cards without a number and tolerates a missing bar`() {
        val html = """
            <main>
              <div><p class="fw-bold">P+R Bronowice</p><h5>Wolnych: brak danych</h5></div>
              <div><p class="fw-bold">P+R Nowy Bieżanów</p><h5>Wolnych: 7</h5></div>
            </main>
        """

        assertEquals(listOf(ParkingAvailability("P+R Nowy Bieżanów", 7, null)), ParkingParser.parse(html))
    }
}
