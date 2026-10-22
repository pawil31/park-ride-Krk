package com.pawil31.parkridekrk.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Extracts P+R availability from the ZTP page.
 *
 * The page layout is not a documented API, so the parser is deliberately tolerant: it understands
 * tables with labelled columns as well as "name, then label/value lines" layouts (cards, lists,
 * definition lists). Whatever it cannot structure is still returned as [AvailabilitySnapshot.contentLines].
 */
object AvailabilityParser {

    fun parse(html: String, baseUri: String = AVAILABILITY_URL): AvailabilitySnapshot {
        val document = Jsoup.parse(html, baseUri)
        val frameUrls = document.select("iframe[src]")
            .map { it.absUrl("src") }
            .filter { it.startsWith("http") && !IGNORED_FRAMES.containsMatchIn(it) }
            .distinct()

        val root = ContentExtractor.contentRoot(document)
        val lines = ContentExtractor.lines(root)
        val parkings = parseTables(root).ifEmpty { parseLines(lines) }

        return AvailabilitySnapshot(
            parkings = parkings.distinctBy { it.name.lowercase() },
            contentLines = lines,
            sourceUpdated = lines.firstOrNull(::isUpdateLine),
            frameUrls = frameUrls,
        )
    }

    private fun parseTables(root: Element): List<ParkingAvailability> {
        val result = mutableListOf<ParkingAvailability>()
        for (table in root.select("table")) {
            var header: List<String> = emptyList()
            for (row in table.select("tr")) {
                val cells = row.select("th, td").map { it.text().trim() }
                if (cells.size < 2) continue
                if (header.isEmpty() && cells.none { hasNumber(it) }) {
                    header = cells
                    continue
                }
                val nameIndex = cells.indexOfFirst { LETTER.containsMatchIn(it) && !hasNumber(it) }
                if (nameIndex < 0) continue
                val entry = EntryBuilder(cells[nameIndex])
                cells.forEachIndexed { i, cell ->
                    if (i != nameIndex && hasNumber(cell)) entry.accept(cell, header.getOrNull(i))
                }
                entry.build()?.let(result::add)
            }
        }
        return result
    }

    private fun parseLines(lines: List<String>): List<ParkingAvailability> {
        val result = mutableListOf<ParkingAvailability>()
        var current: EntryBuilder? = null
        var pendingLabel: String? = null
        var previousWasBareNumber = false

        fun flush() {
            current?.build()?.let(result::add)
            current = null
            pendingLabel = null
        }

        for (line in lines) {
            val wasBareNumber = previousWasBareNumber
            previousWasBareNumber = false

            if (isUpdateLine(line)) continue
            if (line.length > MAX_LINE) {
                flush()
                continue
            }

            if (!hasNumber(line)) {
                val entry = current
                when {
                    !isLabel(line) -> {
                        if (line.length <= MAX_NAME && LETTER.containsMatchIn(line)) {
                            flush()
                            current = EntryBuilder(line)
                        }
                    }
                    // "123" followed by "wolnych miejsc": the label belongs to the number above it.
                    wasBareNumber && entry != null -> entry.relabelLast(line)
                    else -> pendingLabel = line
                }
                continue
            }

            // Name and value on one line, e.g. "P+R Czerwone Maki: 123". Inside a car park block only
            // unlabelled lines qualify, so "Obecnie wolne: 12" stays a value of that block.
            val entry = current
            val inlineName = when {
                entry == null -> nameBeforeValue(line)
                entry.isEmpty() && !LABEL_WORD.containsMatchIn(line) -> nameBeforeValue(line)
                else -> null
            }
            if (inlineName != null) {
                flush()
                EntryBuilder(inlineName).apply { accept(line.substring(inlineName.length), null) }
                    .build()?.let(result::add)
                continue
            }
            if (entry == null) continue
            previousWasBareNumber = entry.accept(line, pendingLabel)
            pendingLabel = null
        }
        flush()
        return result
    }

    private fun nameBeforeValue(line: String): String? {
        val firstNumber = NUMBER.find(stripTimestamps(line)) ?: return null
        val labelStart = LABEL_WORD.find(line)?.range?.first ?: Int.MAX_VALUE
        val name = line.substring(0, minOf(firstNumber.range.first, labelStart)).trimEnd(' ', ':', '-', '–', ',', '|')
        return name.takeIf { it.count(Char::isLetter) >= 3 && name.length <= MAX_NAME }
    }

    private class EntryBuilder(val name: String) {
        private var free: Int? = null
        private var capacity: Int? = null
        private var occupied: Int? = null
        private val details = mutableListOf<String>()
        private var lastBare: Pair<Field, Int>? = null

        /** Returns true when [text] was a bare number without any label. */
        fun accept(text: String, contextLabel: String?): Boolean {
            lastBare = null
            val values = stripTimestamps(text)
            val hasOwnLabel = LETTER.containsMatchIn(values)
            val label = if (hasOwnLabel) text else contextLabel.orEmpty()

            FRACTION.find(values)?.let { match ->
                val (a, b) = match.destructured
                if (OCCUPIED_LABEL.containsMatchIn(label)) occupied = a.toInt() else free = a.toInt()
                capacity = b.toInt()
                return false
            }

            val number = NUMBER.find(values)?.value?.toInt() ?: return false
            val field = fieldFor(label)
            when {
                field != null -> set(field, number)
                label.isNotBlank() -> details += if (hasOwnLabel) text.trim() else "$label: $number"
                else -> {
                    val target = if (free == null) Field.FREE else if (capacity == null) Field.CAPACITY else null
                    if (target == null) {
                        details += text.trim()
                    } else {
                        set(target, number)
                        lastBare = target to number
                        return true
                    }
                }
            }
            return false
        }

        fun isEmpty() = free == null && capacity == null && occupied == null && details.isEmpty()

        fun relabelLast(label: String) {
            val (field, value) = lastBare ?: return
            lastBare = null
            val target = fieldFor(label)
            set(field, null)
            if (target != null) set(target, value) else details += "$value $label"
        }

        private fun set(field: Field, value: Int?) = when (field) {
            Field.FREE -> free = value
            Field.CAPACITY -> capacity = value
            Field.OCCUPIED -> occupied = value
        }

        fun build(): ParkingAvailability? {
            val occ = occupied
            val cap = capacity
            val freeSpaces = free ?: if (occ != null && cap != null) (cap - occ).coerceAtLeast(0) else null
            if (freeSpaces == null && cap == null && details.isEmpty()) return null
            return ParkingAvailability(name.trim(), freeSpaces, cap, details.toList())
        }
    }

    private enum class Field { FREE, CAPACITY, OCCUPIED }

    private fun fieldFor(label: String): Field? = when {
        label.isBlank() || SPECIAL_SPACES.containsMatchIn(label) -> null
        FREE_LABEL.containsMatchIn(label) -> Field.FREE
        OCCUPIED_LABEL.containsMatchIn(label) -> Field.OCCUPIED
        CAPACITY_LABEL.containsMatchIn(label) -> Field.CAPACITY
        else -> null
    }

    private fun isLabel(line: String) = line.length <= MAX_LABEL && LABEL_WORD.containsMatchIn(line)

    private fun isUpdateLine(line: String) =
        UPDATE_LABEL.containsMatchIn(line) && (TIME.containsMatchIn(line) || DATE.containsMatchIn(line))

    private fun hasNumber(text: String) = NUMBER.containsMatchIn(stripTimestamps(text))

    private fun stripTimestamps(text: String) = text.replace(DATE, " ").replace(TIME, " ")

    private const val MAX_LINE = 80
    private const val MAX_NAME = 60
    private const val MAX_LABEL = 40

    private val IGNORE = RegexOption.IGNORE_CASE
    private val LETTER = Regex("""\p{L}""")
    private val NUMBER = Regex("""\d+""")
    private val FRACTION = Regex("""(\d+)\s*/\s*(\d+)""")
    private val TIME = Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b""")
    private val DATE = Regex("""\b\d{1,4}[.\-/]\d{1,2}[.\-/]\d{1,4}\b""")

    private val FREE_LABEL = Regex("""woln|dostępn|free|available|vacant""", IGNORE)
    private val OCCUPIED_LABEL = Regex("""zajęt|occupied""", IGNORE)
    private val CAPACITY_LABEL = Regex("""pojemn|wszystk|łącznie|ogółem|razem|liczba miejsc|capacity|total""", IGNORE)
    private val LABEL_WORD = Regex("""${FREE_LABEL.pattern}|${OCCUPIED_LABEL.pattern}|${CAPACITY_LABEL.pattern}|miejsc|liczba|ilość|spaces""", IGNORE)
    private val SPECIAL_SPACES = Regex("""niepełnospr|inwalid|elektr|ładowa|\bEV\b|rodzin|motocykl|rower|disab|electric|charging""", IGNORE)
    private val UPDATE_LABEL = Regex("""aktualiz|stan na|odświeżon|updated|last update""", IGNORE)
    private val IGNORED_FRAMES = Regex("""youtube|google\.com/maps|googletagmanager|facebook|recaptcha""", IGNORE)
}
