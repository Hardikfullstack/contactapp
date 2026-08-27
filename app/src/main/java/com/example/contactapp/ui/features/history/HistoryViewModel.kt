package com.example.contactapp.ui.features.history

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.R
import com.example.contactapp.domain.model.CallLogItem
import com.example.contactapp.domain.repository.CallLogRepository
import com.example.contactapp.util.AnalyticsManager
import com.example.contactapp.util.QrUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class HistoryUiState(
    val contactId: String = "",
    val name: String = "",
    val number: String = "",
    val photoUri: String? = null,
    val groupedCalls: Map<String, List<CallLogItem>> = emptyMap(),
    val isLoading: Boolean = false,
    val isFavorite: Boolean = false,
    val isBlocked: Boolean = false,
    val showBlockDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showClearHistoryDialog: Boolean = false,
    val pendingDeleteCallId: Long? = null,
    val isDeleted: Boolean = false,
    val showEditSheet: Boolean = false,
    val showQrDialog: Boolean = false,
    val qrBitmap: Bitmap? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val callLogRepository: CallLogRepository,
    private val contactRepository: com.example.contactapp.domain.repository.ContactRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val name: String = savedStateHandle["name"] ?: ""
    private val number: String = savedStateHandle["number"] ?: ""

    private val _uiState = MutableStateFlow(HistoryUiState(name = name, number = number))
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        fetchContactDetails()
        fetchHistory()
        observeFavoriteStatus()
        observeBlockedStatus()
    }

    /** Re-fetches name/photo — call when returning from More Details, where they can change. */
    fun refreshContactDetails() = fetchContactDetails()

    private fun fetchContactDetails() {
        if (number.isNotBlank()) {
            viewModelScope.launch {
                contactRepository.findContactByNumber(number)?.let { contact ->
                    _uiState.value = _uiState.value.copy(
                        contactId = contact.id,
                        name = contact.name,
                        photoUri = contact.photoUri
                    )
                }
            }
        }
    }

    fun showQr(show: Boolean) {
        _uiState.value = _uiState.value.copy(showQrDialog = show)
        if (show && _uiState.value.qrBitmap == null) {
            generateQr()
        }
    }

    private fun generateQr() {
        viewModelScope.launch {
            val state = _uiState.value
            val vCard = buildString {
                append("BEGIN:VCARD\n")
                append("VERSION:3.0\n")
                append("FN:${state.name.ifBlank { state.number }}\n")
                append("TEL;TYPE=CELL:${state.number}\n")
                append("END:VCARD\n")
            }
            val bitmap = QrUtils.generateContactQr(vCard)
            _uiState.value = _uiState.value.copy(qrBitmap = bitmap)
        }
    }

    fun showEditSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showEditSheet = show)
    }

    fun updateContact(newName: String, newNumber: String) {
        val contactId = _uiState.value.contactId
        if (contactId.isBlank()) return
        
        viewModelScope.launch {
            contactRepository.updateContact(contactId, newName, newNumber)
            _uiState.value = _uiState.value.copy(
                name = newName,
                number = newNumber,
                showEditSheet = false
            )
        }
    }

    private fun observeFavoriteStatus() {
        if (number.isNotBlank()) {
            viewModelScope.launch {
                contactRepository.isFavorite(number).collect { isFavorite ->
                    _uiState.value = _uiState.value.copy(isFavorite = isFavorite)
                }
            }
        }
    }

    private fun observeBlockedStatus() {
        if (number.isNotBlank()) {
            val last10Target = number.replace(Regex("[^0-9]"), "").takeLast(10)
            viewModelScope.launch {
                callLogRepository.getBlockedNumbers().collect { blockedList ->
                    val isBlocked = blockedList.any { 
                        val cleanItem = it.replace(Regex("[^0-9]"), "").takeLast(10)
                        cleanItem == last10Target && last10Target.isNotEmpty()
                    }
                    _uiState.value = _uiState.value.copy(isBlocked = isBlocked)
                }
            }
        }
    }

    private var fetchJob: kotlinx.coroutines.Job? = null

    private fun fetchHistory() {
        if (number.isBlank() || fetchJob != null) return
        
        _uiState.value = _uiState.value.copy(isLoading = true)
        fetchJob = viewModelScope.launch {
            callLogRepository.fetchCallHistory(number).collect { logs ->
                _uiState.value = _uiState.value.copy(
                    groupedCalls = groupLogs(logs),
                    isLoading = false
                )
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            contactRepository.toggleFavorite(number, !_uiState.value.isFavorite)
        }
    }

    fun showBlockConfirmation(show: Boolean) {
        _uiState.value = _uiState.value.copy(showBlockDialog = show)
    }

    fun blockNumber() {
        viewModelScope.launch {
            val willBeBlocked = !_uiState.value.isBlocked
            // Optimistic update: instantly change the UI state
            _uiState.value = _uiState.value.copy(isBlocked = willBeBlocked)

            callLogRepository.blockNumber(number, willBeBlocked)
            AnalyticsManager.logEventWithAction(
                "number_blocked", "History", if (willBeBlocked) "block" else "unblock"
            )
            showBlockConfirmation(false)
        }
    }

    fun showDeleteConfirmation(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDeleteDialog = show)
    }

    fun deleteHistoryAndContact() {
        viewModelScope.launch {
            // Delete all call logs for this number from the system log
            callLogRepository.deleteCallLogsByNumber(number)
            // Delete the contact itself
            contactRepository.deleteContact(number)
            _uiState.value = _uiState.value.copy(
                showDeleteDialog = false,
                isDeleted = true
            )
        }
    }

    fun showClearHistoryConfirmation(show: Boolean) {
        _uiState.value = _uiState.value.copy(showClearHistoryDialog = show)
    }

    /** Clears call history for this number only — the contact itself is left untouched.
     *  groupedCalls updates on its own since fetchHistory's flow is ContentObserver-backed. */
    fun clearHistory() {
        viewModelScope.launch {
            callLogRepository.deleteCallLogsByNumber(number)
            _uiState.value = _uiState.value.copy(showClearHistoryDialog = false)
        }
    }

    /** Long-press on a single call entry — deletes just that one call, not the whole history. */
    fun requestDeleteCall(id: Long) {
        _uiState.value = _uiState.value.copy(pendingDeleteCallId = id)
    }

    fun cancelDeleteCall() {
        _uiState.value = _uiState.value.copy(pendingDeleteCallId = null)
    }

    fun confirmDeleteCall() {
        val id = _uiState.value.pendingDeleteCallId ?: return
        callLogRepository.deleteCallLog(id)
        _uiState.value = _uiState.value.copy(pendingDeleteCallId = null)
    }

    fun shareHistory(context: android.content.Context, header: String, entryFormat: String) {
        val shareText = header + 
            _uiState.value.groupedCalls.map { (date, logs) ->
                "$date:\n" + logs.joinToString("\n") { log ->
                    String.format(entryFormat, log.type.name, log.duration)
                }
            }.joinToString("\n\n")
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_history_chooser_title)))
    }

    private fun groupLogs(logs: List<CallLogItem>): Map<String, List<CallLogItem>> {
        return logs.groupBy { item ->
            when {
                DateUtils.isToday(item.timestamp) -> "Today"
                isYesterday(item.timestamp) -> "Yesterday"
                else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(item.timestamp))
            }
        }
    }

    private fun isYesterday(timestamp: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        
        val itemCalendar = Calendar.getInstance()
        itemCalendar.timeInMillis = timestamp
        
        return itemCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
               itemCalendar.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
    }
}
