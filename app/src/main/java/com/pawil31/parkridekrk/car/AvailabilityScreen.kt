package com.pawil31.parkridekrk.car

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pawil31.parkridekrk.R
import com.pawil31.parkridekrk.core.Occupancy
import com.pawil31.parkridekrk.core.ParkingAvailability
import com.pawil31.parkridekrk.data.Availability
import com.pawil31.parkridekrk.data.AvailabilityRepository
import com.pawil31.parkridekrk.data.Favorites
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Lists free spaces per P+R car park, colour-coded like the ZTP page. Refreshes every minute while it
 * is on the car screen; the refresh button shows the time of the last update.
 *
 * The template type and title never change between refreshes, so hosts treat updates as refreshes
 * rather than new steps in the task flow.
 */
class AvailabilityScreen(
    carContext: CarContext,
    private val repository: AvailabilityRepository,
    private val favorites: Favorites,
) : Screen(carContext) {

    private var availability: Availability? = null
    private var updatedAt: LocalTime? = null
    private var lastError: String? = null
    private var refreshing = false

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val startedAt = System.currentTimeMillis()
                    refresh()
                    delay((REFRESH_INTERVAL_MS - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0))
                }
            }
        }
    }

    private suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        try {
            availability = repository.load()
            updatedAt = LocalTime.now()
            lastError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Refresh failed", e)
            lastError = e.message ?: e.javaClass.simpleName
        } finally {
            refreshing = false
        }
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val template = ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_title))
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(ActionStrip.Builder().addAction(refreshAction()).build())

        val data = availability
        if (data == null) {
            val error = lastError ?: return template.setLoading(true).build()
            val list = ItemList.Builder()
                .setNoItemsMessage(carContext.getString(R.string.load_error, error))
                .build()
            return template.setSingleList(list).build()
        }

        val rows = when {
            data.parkings.isNotEmpty() -> parkingRows(data.parkings.sortedByDescending { it.name in favorites }, listLimit())
            else -> pageTextRows(data.pageText).take(listLimit())
        }
        val list = ItemList.Builder()
        if (rows.isEmpty()) list.setNoItemsMessage(carContext.getString(R.string.no_content))
        rows.forEach(list::addItem)
        return template.setSingleList(list.build()).build()
    }

    private fun refreshAction(): Action {
        val time = updatedAt?.format(TIME_FORMAT)
        val title = when {
            time == null -> carContext.getString(R.string.refresh)
            lastError != null -> carContext.getString(R.string.refresh_failed_at, time)
            else -> carContext.getString(R.string.refresh_at, time)
        }
        return Action.Builder()
            .setTitle(title)
            .setOnClickListener { lifecycleScope.launch { refresh() } }
            .build()
    }

    /** One row per car park; if they don't all fit, the last row lists the rest. */
    private fun parkingRows(parkings: List<ParkingAvailability>, limit: Int): List<Row> {
        if (parkings.size <= limit) return parkings.map(this::parkingRow)
        val rest = parkings.drop(limit - 1)
        val overflow = Row.Builder().setTitle(carContext.getString(R.string.other_parkings))
        rest.chunked((rest.size + 1) / 2).forEach { line ->
            val text = SpannableStringBuilder()
            line.forEachIndexed { i, parking ->
                if (i > 0) text.append(" · ")
                appendColored(text, carContext.getString(R.string.free_short, shortName(parking), parking.freeSpaces), parking)
            }
            overflow.addText(text)
        }
        return parkings.take(limit - 1).map(this::parkingRow) + overflow.build()
    }

    /**
     * The star button on the right stars or unstars a car park. Hosts older than API level 6 have no
     * row buttons, so there tapping the row toggles it and a star in the title marks it.
     */
    private fun parkingRow(parking: ParkingAvailability): Row {
        val text = SpannableStringBuilder()
        appendColored(text, carContext.getString(R.string.free, parking.freeSpaces), parking)
        val starred = parking.name in favorites
        val toggle = { favorites.toggle(parking.name); invalidate() }
        val row = Row.Builder().addText(text)
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_6) {
            row.setTitle(shortName(parking)).addAction(starAction(starred, toggle))
        } else {
            row.setTitle(if (starred) "$STAR ${shortName(parking)}" else shortName(parking)).setOnClickListener(toggle)
        }
        return row.build()
    }

    private fun starAction(starred: Boolean, onClick: () -> Unit): Action {
        val icon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, if (starred) R.drawable.ic_star else R.drawable.ic_star_border),
        )
        if (starred) icon.setTint(CarColor.YELLOW)
        return Action.Builder().setIcon(icon.build()).setOnClickListener(onClick).build()
    }

    private fun appendColored(text: SpannableStringBuilder, part: String, parking: ParkingAvailability) {
        val start = text.length
        text.append(part)
        text.setSpan(ForegroundCarColorSpan.create(colorFor(parking)), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun colorFor(parking: ParkingAvailability): CarColor = when (parking.occupancy) {
        Occupancy.LOW -> CarColor.GREEN
        Occupancy.HIGH -> CarColor.YELLOW
        Occupancy.FULL -> CarColor.RED
        null -> when {
            parking.freeSpaces == 0 -> CarColor.RED
            parking.freeSpaces < 20 -> CarColor.YELLOW
            else -> CarColor.GREEN
        }
    }

    /** The screen title already says "P+R", so drop it from each name. */
    private fun shortName(parking: ParkingAvailability) = parking.name.removePrefix("P+R").trim()

    /** Fallback when the car parks can't be read: the page text, three lines per row. */
    private fun pageTextRows(lines: List<String>): List<Row> =
        lines.chunked(LINES_PER_ROW).map { chunk ->
            Row.Builder().setTitle(chunk[0]).apply { chunk.drop(1).forEach { addText(it) } }.build()
        }

    private fun listLimit(): Int {
        val limit = if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_2) {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } else {
            DEFAULT_LIST_LIMIT
        }
        return limit.coerceAtLeast(2)
    }

    private companion object {
        const val TAG = "AvailabilityScreen"
        const val REFRESH_INTERVAL_MS = 60_000L
        const val DEFAULT_LIST_LIMIT = 6
        const val LINES_PER_ROW = 3
        const val STAR = "★"
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
