package com.example.contactapp.ui.features.recents

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.domain.model.CallLogItem
import com.example.contactapp.domain.model.CallType
import com.example.contactapp.domain.repository.CallLogRepository
import com.example.contactapp.util.PreferenceManager
import com.example.contactapp.util.SpamDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject


data class RecentsUiState(
    val groupedCalls: Map<String, List<CallLogItem>> = emptyMap(),
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val selectedFilter: CallFilter = CallFilter.ALL,
    val selectedCallItem: CallLogItem? = null,
    val spamNumbers: Set<String> = emptySet(),
    val showClearSpamDialog: Boolean = false
)

enum class CallFilter {
    ALL, MISSED, CONTACTS, INCOMING, OUTGOING, SPAM
}

@HiltViewModel
class RecentsViewModel @Inject constructor(
    private val repository: CallLogRepository,
    private val preferenceManager: PreferenceManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecentsUiState())
    val uiState: StateFlow<RecentsUiState> = _uiState.asStateFlow()

    private val _expandedCallId = MutableStateFlow<Long?>(null)
    val expandedCallId = _expandedCallId.asStateFlow()

    init {
        checkPermissionAndFetch()
    }

    fun checkPermissionAndFetch() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        _uiState.value = _uiState.value.copy(hasPermission = hasPermission)

        if (hasPermission) {
            fetchCallLogs()
        }
    }

    private var fetchJob: kotlinx.coroutines.Job? = null

    private fun fetchCallLogs() {
        if (fetchJob != null) return
        
        _uiState.value = _uiState.value.copy(isLoading = true)
        fetchJob = viewModelScope.launch {
            combine(
                repository.fetchCallLogs(),
                repository.getBlockedNumbers(),
                _uiState.map { it.selectedFilter }.distinctUntilChanged(),
                // Re-runs this block when any preference changes (including the Caller ID &
                // Spam toggle) so flipping it takes effect immediately, not just on the next
                // unrelated call-log/filter change.
                preferenceManager.preferencesFlow
            ) { logs, blockedNumbers, filter, _ ->
                val normalizedBlocked = blockedNumbers.map { it.replace(Regex("[^0-9]"), "").takeLast(10) }
                val spamNumbers = if (preferenceManager.isCallerIdSpamProtectionEnabled()) {
                    SpamDetector.detectSpamNumbers(logs)
                } else {
                    emptySet()
                }

                val mapped = logs.map { log ->
                    val cleanNum = log.number.replace(Regex("[^0-9]"), "").takeLast(10)
                    val isSpam = cleanNum.isNotEmpty() && spamNumbers.contains(cleanNum)
                    log.copy(
                        isBlocked = cleanNum.isNotEmpty() && normalizedBlocked.contains(cleanNum),
                        type = if (isSpam) CallType.SPAM else log.type
                    )
                }

                val filtered = when (filter) {
                    CallFilter.ALL -> mapped
                    CallFilter.MISSED -> mapped.filter { it.type == CallType.MISSED || it.type == CallType.REJECTED }
                    CallFilter.CONTACTS -> mapped.filter { !it.name.isNullOrBlank() }
                    CallFilter.INCOMING -> mapped.filter { it.type == CallType.INCOMING }
                    CallFilter.OUTGOING -> mapped.filter { it.type == CallType.OUTGOING }
                    CallFilter.SPAM -> mapped.filter { it.type == CallType.BLOCKED || it.type == CallType.SPAM }
                }

                // Raw (as-stored) numbers of the flagged entries — deleteCallLogsByNumbers does
                // an exact match against CallLog.Calls.NUMBER, so the normalized digits-only
                // form SpamDetector works with internally would never actually match anything.
                val spamRawNumbers = mapped.filter { it.type == CallType.SPAM }.map { it.number }.toSet()

                groupLogs(filtered) to spamRawNumbers
            }
            .flowOn(Dispatchers.Default)
            .conflate()
            .collect { (grouped, spamRawNumbers) ->
                _uiState.value = _uiState.value.copy(
                    groupedCalls = grouped,
                    spamNumbers = spamRawNumbers,
                    isLoading = false
                )
            }
        }
    }

    fun setFilter(filter: CallFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    private fun groupLogs(logs: List<CallLogItem>): Map<String, List<CallLogItem>> {
        return logs.groupBy { item ->
            when {
                DateUtils.isToday(item.timestamp) -> "today"
                isYesterday(item.timestamp) -> "yesterday"
                else -> "older"
            }
        }
    }

    private fun isYesterday(timestamp: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = calendar.timeInMillis
        
        val itemCalendar = Calendar.getInstance()
        itemCalendar.timeInMillis = timestamp
        
        val yesterdayCalendar = Calendar.getInstance()
        yesterdayCalendar.timeInMillis = yesterday
        
        return itemCalendar.get(Calendar.YEAR) == yesterdayCalendar.get(Calendar.YEAR) &&
               itemCalendar.get(Calendar.DAY_OF_YEAR) == yesterdayCalendar.get(Calendar.DAY_OF_YEAR)
    }

    fun onCallClicked(id: Long) {
        _expandedCallId.value =
            if (_expandedCallId.value == id) null else id
    }

    fun onCallLongClick(item: CallLogItem) {
        _uiState.value = _uiState.value.copy(selectedCallItem = item)
    }

    fun dismissActionSheet() {
        _uiState.value = _uiState.value.copy(selectedCallItem = null)
    }

    fun deleteCall(id: Long) {
        viewModelScope.launch {
            repository.deleteCallLog(id)
        }
    }

    fun showClearSpamConfirmation(show: Boolean) {
        _uiState.value = _uiState.value.copy(showClearSpamDialog = show)
    }

    /** Deletes every call log entry from every number currently flagged as suspected spam. */
    fun deleteAllSpamCalls() {
        viewModelScope.launch {
            repository.deleteCallLogsByNumbers(_uiState.value.spamNumbers.toList())
            _uiState.value = _uiState.value.copy(showClearSpamDialog = false)
        }
    }

    fun toggleBlock(number: String, isBlocked: Boolean) {
        viewModelScope.launch {
            repository.blockNumber(number, !isBlocked)
        }
    }
}
