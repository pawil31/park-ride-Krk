package com.pawil31.parkridekrk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvailabilityParserTest {

    private fun page(content: String) = """
        <html><head><title>ZTP</title><script>var x = 123;</script></head>
        <body>
          <header><a href="/">ZTP Kraków</a> tel. 12 616 70 00</header>
          <nav><ul><li>Parkingi P+R</li><li>Strefa 1</li></ul></nav>
          <div class="breadcrumbs">Start / Parkingi P+R</div>
          $content
          <footer>Zarząd Transportu Publicznego, ul. Wielicka 28a, 30-552 Kraków</footer>
          <div id="cookie-banner">Ta strona używa 3 rodzajów cookies</div>
        </body></html>
    """.trimIndent()

    @Test
    fun `table with labelled columns`() {
        val snapshot = AvailabilityParser.parse(page("""
            <main>
              <h1>Sprawdź wolne miejsca P+R</h1>
              <table>
                <tr><th>Parking</th><th>Pojemność</th><th>Wolne miejsca</th></tr>
                <tr><td>P+R Czerwone Maki</td><td>400</td><td>123</td></tr>
                <tr><td>P+R Kurdwanów</td><td>160</td><td>0</td></tr>
              </table>
              <p>Ostatnia aktualizacja: 22.10.2026 07:41</p>
            </main>
        """))

        assertEquals(
            listOf(
                ParkingAvailability("P+R Czerwone Maki", freeSpaces = 123, capacity = 400),
                ParkingAvailability("P+R Kurdwanów", freeSpaces = 0, capacity = 160),
            ),
            snapshot.parkings,
        )
        assertEquals("Ostatnia aktualizacja: 22.10.2026 07:41", snapshot.sourceUpdated)
    }

    @Test
    fun `cards with label and value lines`() {
        val snapshot = AvailabilityParser.parse(page("""
            <div class="content">
              <h1>Sprawdź wolne miejsca P+R</h1>
              <div class="card"><h3>Czerwone Maki</h3>
                <p>Wolne miejsca: <strong>87</strong></p>
                <p>Pojemność: 400</p>
                <p>Miejsca dla osób z niepełnosprawnością: 8</p>
              </div>
              <div class="card"><h3>Bieżanów</h3>
                <p>Wolne miejsca: 12</p>
                <p>Pojemność: 120</p>
              </div>
            </div>
        """))

        assertEquals(
            listOf(
                ParkingAvailability(
                    "Czerwone Maki", 87, 400,
                    listOf("Miejsca dla osób z niepełnosprawnością: 8"),
                ),
                ParkingAvailability("Bieżanów", 12, 120),
            ),
            snapshot.parkings,
        )
    }

    @Test
    fun `numbers followed by their labels`() {
        val snapshot = AvailabilityParser.parse(page("""
            <section>
              <div class="tile"><div class="name">Mały Płaszów</div>
                <div class="count">45</div><div>wolnych miejsc</div>
                <div class="count">250</div><div>wszystkich miejsc</div>
              </div>
              <div class="tile"><div class="name">Górka Narodowa</div>
                <div class="count">3</div><div>wolne</div>
              </div>
            </section>
        """))

        assertEquals(
            listOf(
                ParkingAvailability("Mały Płaszów", 45, 250),
                ParkingAvailability("Górka Narodowa", 3, null),
            ),
            snapshot.parkings,
        )
    }

    @Test
    fun `labels before bare numbers in definition lists`() {
        val snapshot = AvailabilityParser.parse(page("""
            <main><h2>Bronowice</h2>
              <dl><dt>Zajęte miejsca</dt><dd>30</dd><dt>Pojemność</dt><dd>100</dd></dl>
            </main>
        """))

        assertEquals(listOf(ParkingAvailability("Bronowice", 70, 100)), snapshot.parkings)
    }

    @Test
    fun `fractions and inline names`() {
        val snapshot = AvailabilityParser.parse(page("""
            <main>
              <h2>Parkingi P+R</h2>
              <ul>
                <li>P+R Czerwone Maki: 123 / 400</li>
                <li>P+R Kurdwanów – wolne miejsca: 7</li>
              </ul>
            </main>
        """))

        assertEquals(
            listOf(
                ParkingAvailability("P+R Czerwone Maki", 123, 400),
                ParkingAvailability("P+R Kurdwanów", 7, null),
            ),
            snapshot.parkings,
        )
    }

    @Test
    fun `site chrome is not part of the content`() {
        val snapshot = AvailabilityParser.parse(page("<main><p>Nie znaleziono danych 0</p></main>"))

        val text = snapshot.contentLines.joinToString("\n")
        assertTrue("Wielicka" !in text, text)
        assertTrue("Strefa 1" !in text, text)
        assertTrue("cookies" !in text, text)
        assertTrue("12 616" !in text, text)
        assertTrue("var x" !in text, text)
    }

    @Test
    fun `unknown layout falls back to content lines`() {
        val snapshot = AvailabilityParser.parse(page("""
            <div id="app"><p>Dane są ładowane, prosimy czekać.</p></div>
        """))

        assertTrue(snapshot.parkings.isEmpty())
        assertEquals(listOf("Dane są ładowane, prosimy czekać."), snapshot.contentLines)
        assertNull(snapshot.sourceUpdated)
    }

    @Test
    fun `embedded data frames are reported`() {
        val snapshot = AvailabilityParser.parse(page("""
            <main><p>Zobacz poniżej:</p>
              <iframe src="https://parking.example.com/pr/widget"></iframe>
              <iframe src="https://www.youtube.com/embed/abc"></iframe>
            </main>
        """))

        assertEquals(listOf("https://parking.example.com/pr/widget"), snapshot.frameUrls)
    }
}
