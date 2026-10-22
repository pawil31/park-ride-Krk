package com.pawil31.parkridekrk.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/** Official ZTP page with live P+R availability. */
const val AVAILABILITY_URL = "https://ztp.krakow.pl/parkingi-pr/sprawdz-wolne-miejsca-pr"

/** Strips site chrome (header, footer, menus, cookie banners) and turns the remaining content into text lines. */
object PageContentExtractor {

    /** Readable text of the page without header, footer, menus and cookie banners, one line per block. */
    fun lines(html: String): List<String> = lines(contentRoot(Jsoup.parse(html)))

    private val ALWAYS_REMOVE = listOf(
        "script", "style", "noscript", "template", "svg", "nav", "aside",
        "[role=navigation]", "[role=search]",
        "[class*=breadcrumb]", "[id*=breadcrumb]",
        "[class*=cookie]", "[id*=cookie]",
        "[class*=skip-link]", "[class*=skiplink]",
    ).joinToString(", ")

    private val SITE_CHROME = "header, footer, [role=banner], [role=contentinfo]"

    /** Containers that legitimately use header/footer tags for their own content, e.g. a card per car park. */
    private val CONTENT_CONTAINERS = setOf("article", "li", "td", "th", "table")

    private val MAIN_CANDIDATES = listOf(
        "main", "[role=main]", "#main-content", ".main-content", "#content", "#main", "article",
    )

    private val DIGIT = Regex("""\d""")
    private val WHITESPACE = Regex("""\s+""")
    private val LINE_BREAKS = setOf("br", "tr", "li", "dt", "dd")

    private fun contentRoot(document: Document): Element {
        val body = document.body()
        val root = MAIN_CANDIDATES.asSequence()
            .mapNotNull { document.selectFirst(it) }
            .firstOrNull { DIGIT.containsMatchIn(it.text()) }
            ?: body

        removeKeeping(root, document.select(ALWAYS_REMOVE))
        if (root === body) {
            val chrome = document.select(SITE_CHROME)
                .filter { el -> el.parents().none { it.normalName() in CONTENT_CONTAINERS } }
            removeKeeping(root, chrome)
        }
        return root
    }

    /** Removes [elements] unless they are (or contain) [root] itself. */
    private fun removeKeeping(root: Element, elements: Iterable<Element>) {
        for (el in elements) {
            if (el === root || el.normalName() == "body" || el.normalName() == "html") continue
            if (root.parents().any { it === el }) continue
            el.remove()
        }
    }

    private fun lines(root: Element): List<String> {
        val text = StringBuilder()
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                when (node) {
                    is TextNode -> text.append(node.text())
                    is Element -> text.append(if (breaksLine(node)) '\n' else ' ')
                }
            }

            override fun tail(node: Node, depth: Int) {
                if (node is Element) text.append(if (breaksLine(node)) '\n' else ' ')
            }
        }, root)
        return text.lines()
            .map { it.replace(WHITESPACE, " ").trim() }
            .filter { it.isNotEmpty() }
    }

    private fun breaksLine(element: Element) =
        element.isBlock || element.normalName() in LINE_BREAKS
}
