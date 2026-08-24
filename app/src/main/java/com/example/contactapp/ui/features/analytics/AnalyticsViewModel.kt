package com.example.contactapp.ui.features.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.domain.model.CallLogItem
import com.example.contactapp.domain.model.CallType
import com.example.contactapp.domain.repository.CallLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

data class AnalyticsUiState(
    val totalCalls: Int = 0,
    val totalDuration: String = "0m",
    val averageDuration: String = "0m",
    val incomingCount: Int = 0,
    val outgoingCount: Int = 0,
    val missedCount: Int = 0,
    val topCallers: List<TopCaller> = emptyList(),
    val hourlyStats: List<PeriodStat> = emptyList(),
    val dailyStats: List<PeriodStat> = emptyList(),
    val monthlyStats: List<PeriodStat> = emptyList(),
    val isLoading: Boolean = false,
    val hasData: Boolean = false
)

enum class ActivityViewMode { HOUR, DAY, MONTH }

/** One bucket of the Daily Activity chart (one hour-of-day, day-of-week, or month), carrying
 *  enough of a breakdown that tapping its bar can show real detail instead of just a count. */
data class PeriodStat(
    val label: String,
    val fullLabel: String,
    val totalCalls: Int = 0,
    val incomingCount: Int = 0,
    val outgoingCount: Int = 0,
    val missedCount: Int = 0,
    val totalDuration: String = "0m"
)

data class TopCaller(
    val name: String,
    val number: String,
    val callCount: Int,
    val totalDuration: String,
    val photoUri: String? = null
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: CallLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState(isLoading = true))
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * One-shot load rather than a live-reactive flow: analytics aggregates the whole
     * call history, so recomputing it on every call-log/contact change (which the
     * shared reactive fetchCallLogs() flow does) would repeatedly re-scan potentially
     * thousands of rows while this screen is open. A snapshot on open/refresh is both
     * fast and plenty accurate for a stats view.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Fast path: no per-row contact lookups (see fetchCallLogsForAnalytics docs).
            val logs = withContext(Dispatchers.IO) { repository.fetchCallLogsForAnalytics() }
            val aggregate = withContext(Dispatchers.Default) { aggregate(logs) }
            val topCallers = withContext(Dispatchers.IO) { resolveTopCallerDisplayInfo(aggregate.topCallerStats) }

            _uiState.value = aggregate.state.copy(
                topCallers = topCallers,
                isLoading = false
            )
        }
    }

    private data class Aggregate(val state: AnalyticsUiState, val topCallerStats: List<CallerStat>)

    private fun aggregate(logs: List<CallLogItem>): Aggregate {
        if (logs.isEmpty()) {
            return Aggregate(AnalyticsUiState(hasData = false), emptyList())
        }

        var totalSec = 0L
        var incoming = 0
        var outgoing = 0
        var missed = 0
        val hourlyAcc = Array(24) { BucketAcc() }
        val dayOfWeekAcc = Array(7) { BucketAcc() }
        val monthAcc = Array(12) { BucketAcc() }
        val callersMap = LinkedHashMap<String, CallerStat>()

        for (log in logs) {
            totalSec += log.durationSeconds

            when (log.type) {
                CallType.INCOMING -> incoming++
                CallType.OUTGOING -> outgoing++
                CallType.MISSED, CallType.REJECTED -> missed++
                else -> {}
            }

            val calendar = Calendar.getInstance().apply { timeInMillis = log.timestamp }
            val hour = calendar.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6) // Calendar.SUNDAY(1) -> 0
            val month = calendar.get(Calendar.MONTH).coerceIn(0, 11)

            hourlyAcc[hour].add(log)
            dayOfWeekAcc[dayOfWeek].add(log)
            monthAcc[month].add(log)

            val stat = callersMap.getOrPut(log.number) {
                CallerStat(log.name ?: log.number, log.number, 0, 0L)
            }
            stat.count++
            stat.duration += log.durationSeconds
        }

        val topCallerStats = callersMap.values
            .sortedByDescending { it.count }
            .take(5)

        val state = AnalyticsUiState(
            totalCalls = logs.size,
            totalDuration = formatDuration(totalSec),
            averageDuration = formatDuration(if (logs.isNotEmpty()) totalSec / logs.size else 0L),
            incomingCount = incoming,
            outgoingCount = outgoing,
            missedCount = missed,
            hourlyStats = hourlyAcc.mapIndexed { i, acc -> acc.toPeriodStat(HOUR_LABELS[i], HOUR_LABELS[i]) },
            dailyStats = dayOfWeekAcc.mapIndexed { i, acc -> acc.toPeriodStat(DAY_LABELS[i], DAY_FULL_LABELS[i]) },
            monthlyStats = monthAcc.mapIndexed { i, acc -> acc.toPeriodStat(MONTH_LABELS[i], MONTH_FULL_LABELS[i]) },
            hasData = true
        )

        return Aggregate(state, topCallerStats)
    }

    /** Mutable per-bucket accumulator used only while scanning the log once in [aggregate]. */
    private inner class BucketAcc {
        var total = 0
        var incoming = 0
        var outgoing = 0
        var missed = 0
        var durationSeconds = 0L

        fun add(log: CallLogItem) {
            total++
            durationSeconds += log.durationSeconds
            when (log.type) {
                CallType.INCOMING -> incoming++
                CallType.OUTGOING -> outgoing++
                CallType.MISSED, CallType.REJECTED -> missed++
                else -> {}
            }
        }

        fun toPeriodStat(label: String, fullLabel: String) = PeriodStat(
            label = label,
            fullLabel = fullLabel,
            totalCalls = total,
            incomingCount = incoming,
            outgoingCount = outgoing,
            missedCount = missed,
            totalDuration = formatDuration(durationSeconds)
        )
    }

    /** Only the (at most 5) top-caller numbers get a real contact lookup — never the whole log. */
    private suspend fun resolveTopCallerDisplayInfo(stats: List<CallerStat>): List<TopCaller> = coroutineScope {
        stats.map { stat ->
            async {
                val (resolvedName, photoUri) = repository.resolveContactDisplayInfo(stat.number)
                TopCaller(
                    name = resolvedName ?: stat.fallbackName,
                    number = stat.number,
                    callCount = stat.count,
                    totalDuration = formatDuration(stat.duration),
                    photoUri = photoUri
                )
            }
        }.awaitAll()
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    private data class CallerStat(
        val fallbackName: String,
        val number: String,
        var count: Int,
        var duration: Long
    )

    private companion object {
        val HOUR_LABELS = listOf(
            "12 AM", "1 AM", "2 AM", "3 AM", "4 AM", "5 AM", "6 AM", "7 AM", "8 AM", "9 AM", "10 AM", "11 AM",
            "12 PM", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM", "6 PM", "7 PM", "8 PM", "9 PM", "10 PM", "11 PM"
        )
        val DAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val DAY_FULL_LABELS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val MONTH_LABELS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val MONTH_FULL_LABELS = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
}
