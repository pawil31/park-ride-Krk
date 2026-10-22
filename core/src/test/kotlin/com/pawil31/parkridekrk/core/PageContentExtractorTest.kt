package com.pawil31.parkridekrk.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PageContentExtractorTest {

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
    fun `keeps only the main content, one line per block`() {
        val content = PageContentExtractor.extract(page("""
            <main>
              <h1>Sprawdź wolne miejsca P+R</h1>
              <div class="card"><h3>Czerwone Maki</h3><p>Wolne miejsca: <strong>87</strong></p></div>
              <table>
                <tr><th>Parking</th><th>Wolne</th></tr>
                <tr><td>Kurdwanów</td><td>12</td></tr>
              </table>
            </main>
        """))

        assertEquals(
            listOf(
                "Sprawdź wolne miejsca P+R",
                "Czerwone Maki",
                "Wolne miejsca: 87",
                "Parking", "Wolne",
                "Kurdwanów", "12",
            ),
            content.lines,
        )
    }

    @Test
    fun `strips site chrome when there is no main element`() {
        val content = PageContentExtractor.extract(page("""
            <div id="app"><div class="tile"><span>Bieżanów</span> <span>45</span> wolnych miejsc</div></div>
        """))

        assertEquals(listOf("Bieżanów 45 wolnych miejsc"), content.lines)
    }

    @Test
    fun `keeps header tags that belong to content cards`() {
        val content = PageContentExtractor.extract(page("""
            <div><article><header>Mały Płaszów</header><p>Wolne: 3</p></article></div>
        """))

        assertEquals(listOf("Mały Płaszów", "Wolne: 3"), content.lines)
    }

    @Test
    fun `reports embedded data frames`() {
        val content = PageContentExtractor.extract(page("""
            <main><p>Zobacz poniżej: 1</p>
              <iframe src="https://parking.example.com/pr/widget"></iframe>
              <iframe src="https://www.youtube.com/embed/abc"></iframe>
            </main>
        """))

        assertEquals(listOf("https://parking.example.com/pr/widget"), content.frameUrls)
    }
}
