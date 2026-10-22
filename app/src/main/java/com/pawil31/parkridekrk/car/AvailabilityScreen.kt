package com.pawil31.parkridekrk.car

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pawil31.parkridekrk.R
import com.pawil31.parkridekrk.data.AvailabilityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Shows the text of the ZTP availability page (without header and footer) on the car screen.
 * Refreshes every minute while it is on screen.
 *
 * The template type and title never change between refreshes, so hosts treat updates as refreshes
 * rather than new steps in the task flow.
 */
class AvailabilityScreen(
    carContext: CarContext,
    private val repository: AvailabilityRepository,
) : Screen(carContext) {

    private var lines: List<String>? = null
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
            lines = repository.load()
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

        val content = lines
        if (content == null) {
            val error = lastError ?: return template.setLoading(true).build()
            val list = ItemList.Builder()
                .setNoItemsMessage(carContext.getString(R.string.load_error, error))
                .build()
            return template.setSingleList(list).build()
        }

        val list = ItemList.Builder()
        // Keep the last slot for the status row so the driver always sees how fresh the data is.
        contentRows(content).take(listLimit() - 1).forEach(list::addItem)
        list.addItem(statusRow(content.isEmpty()))
        return template.setSingleList(list.build()).build()
    }

    private fun refreshAction() = Action.Builder()
        .setTitle(carContext.getString(R.string.refresh))
        .setOnClickListener { lifecycleScope.launch { refresh() } }
        .build()

    /** Page text packed three lines per row: the row title plus its two text lines. */
    private fun contentRows(content: List<String>): List<Row> =
        content.chunked(LINES_PER_ROW).map { chunk ->
            Row.Builder().setTitle(chunk[0]).apply { chunk.drop(1).forEach { addText(it) } }.build()
        }

    private fun statusRow(empty: Boolean): Row {
        val time = updatedAt?.format(TIME_FORMAT).orEmpty()
        val title = if (lastError == null) {
            carContext.getString(R.string.status_ok, time)
        } else {
            carContext.getString(R.string.status_stale, time)
        }
        val row = Row.Builder().setTitle(title)
        if (empty) row.addText(carContext.getString(R.string.no_content))
        return row.build()
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
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
