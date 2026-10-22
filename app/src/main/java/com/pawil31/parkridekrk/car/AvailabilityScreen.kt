package com.pawil31.parkridekrk.car

import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pawil31.parkridekrk.R
import com.pawil31.parkridekrk.core.AVAILABILITY_URL
import com.pawil31.parkridekrk.core.AvailabilitySnapshot
import com.pawil31.parkridekrk.core.ParkingAvailability
import com.pawil31.parkridekrk.data.AvailabilityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Lists free spaces per P+R car park. Refreshes every minute while it is on the car screen.
 *
 * The template type and title never change between refreshes, so hosts treat updates as refreshes
 * rather than new steps in the task flow.
 */
class AvailabilityScreen(
    carContext: CarContext,
    private val repository: AvailabilityRepository,
) : Screen(carContext) {

    private var snapshot: AvailabilitySnapshot? = null
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
            snapshot = repository.load()
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

        val data = snapshot
        if (data == null) {
            val error = lastError ?: return template.setLoading(true).build()
            val list = ItemList.Builder()
                .setNoItemsMessage(carContext.getString(R.string.load_error, error))
                .build()
            return template.setSingleList(list).build()
        }

        val rows = if (data.parkings.isNotEmpty()) data.parkings.map(this::parkingRow) else pageTextRows(data)
        val list = ItemList.Builder()
        // Keep the last slot for the status row so the driver always sees how fresh the data is.
        rows.take(listLimit() - 1).forEach(list::addItem)
        list.addItem(statusRow(data))
        return template.setSingleList(list.build()).build()
    }

    private fun refreshAction() = Action.Builder()
        .setTitle(carContext.getString(R.string.refresh))
        .setOnClickListener { lifecycleScope.launch { refresh() } }
        .build()

    private fun parkingRow(parking: ParkingAvailability): Row {
        val row = Row.Builder()
            .setTitle(parking.name)
            .addText(freeSpacesText(parking))
            .setOnClickListener { navigateTo(parking.name) }
        if (parking.details.isNotEmpty()) row.addText(parking.details.joinToString(" · "))
        return row.build()
    }

    private fun freeSpacesText(parking: ParkingAvailability): CharSequence {
        val free = parking.freeSpaces ?: return carContext.getString(R.string.no_free_count)
        val capacity = parking.capacity
        val text = if (capacity != null) {
            carContext.getString(R.string.free_of_capacity, free, capacity)
        } else {
            carContext.getString(R.string.free_only, free)
        }
        return SpannableString(text).apply {
            setSpan(ForegroundCarColorSpan.create(colorFor(free, capacity)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun colorFor(free: Int, capacity: Int?): CarColor {
        val share = if (capacity != null && capacity > 0) free.toDouble() / capacity else null
        return when {
            free == 0 || (share != null && share < 0.05) || free < 5 -> CarColor.RED
            (share != null && share < 0.2) || free < 20 -> CarColor.YELLOW
            else -> CarColor.GREEN
        }
    }

    /** The page layout was not recognised: show its text, two lines per row. */
    private fun pageTextRows(data: AvailabilitySnapshot): List<Row> =
        data.contentLines.chunked(2).map { lines ->
            Row.Builder().setTitle(lines[0]).apply { lines.getOrNull(1)?.let { addText(it) } }.build()
        }

    private fun statusRow(data: AvailabilitySnapshot): Row {
        val time = updatedAt?.format(TIME_FORMAT).orEmpty()
        val title = if (lastError == null) {
            carContext.getString(R.string.status_ok, time)
        } else {
            carContext.getString(R.string.status_stale, time)
        }
        val detail = when {
            data.parkings.isEmpty() && data.contentLines.isEmpty() -> carContext.getString(R.string.no_content)
            data.parkings.isEmpty() -> carContext.getString(R.string.raw_content_hint)
            else -> data.sourceUpdated ?: carContext.getString(R.string.status_source, Uri.parse(AVAILABILITY_URL).host)
        }
        return Row.Builder().setTitle(title).addText(detail).build()
    }

    private fun navigateTo(parkingName: String) {
        val query = if (parkingName.contains("P+R", ignoreCase = true) || parkingName.contains("P&R", ignoreCase = true)) {
            "$parkingName, Kraków"
        } else {
            "P+R $parkingName, Kraków"
        }
        val intent = Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:0,0?q=" + Uri.encode(query)))
        try {
            carContext.startCarApp(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start navigation", e)
        }
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
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
