package com.example.contactapp.ui.features.ringtone

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.R
import com.example.contactapp.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [uri] is null for the "Silent" entry. */
data class RingtoneItem(val title: String, val uri: Uri?)

data class RingtoneUiState(
    val ringtones: List<RingtoneItem> = emptyList(),
    val selectedUri: Uri? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class RingtoneViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactRepository: ContactRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val contactNumber: String? = savedStateHandle.get<String>("number")?.takeIf { it.isNotBlank() }
    val contactName: String? = savedStateHandle.get<String>("name")?.takeIf { it.isNotBlank() }
    val isContactMode: Boolean = contactNumber != null
    private var contactId: String? = null

    private val _uiState = MutableStateFlow(RingtoneUiState())
    val uiState: StateFlow<RingtoneUiState> = _uiState.asStateFlow()

    private var previewRingtone: Ringtone? = null

    init {
        loadRingtones()
    }

    /** Re-reads the actual system default — call when returning from the WRITE_SETTINGS screen. */
    fun refreshSelection() {
        if (isContactMode) return
        val current = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
        _uiState.value = _uiState.value.copy(selectedUri = current)
    }

    // Contact mode writes to the Contacts provider's own CUSTOM_RINGTONE column via
    // WRITE_CONTACTS (already held) — it never touches system settings.
    fun canWriteSystemSettings(): Boolean = isContactMode || Settings.System.canWrite(context)

    private fun loadRingtones() {
        viewModelScope.launch(Dispatchers.IO) {
            val manager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_RINGTONE)
            }
            val items = mutableListOf<RingtoneItem>()
            if (isContactMode) {
                items.add(RingtoneItem(context.getString(R.string.default_ringtone), USE_DEFAULT_SENTINEL))
            }
            items.add(RingtoneItem(context.getString(R.string.silent), null))
            val cursor = manager.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = manager.getRingtoneUri(cursor.position)
                items.add(RingtoneItem(title, uri))
            }

            val current: Uri? = if (contactNumber != null) {
                val contact = contactRepository.findContactByNumber(contactNumber)
                contactId = contact?.id
                when (val stored = contactRepository.getContactRingtone(contactNumber)) {
                    null -> USE_DEFAULT_SENTINEL
                    Uri.EMPTY -> null // our stored "Silent" marker
                    else -> stored
                }
            } else {
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            }
            _uiState.value = RingtoneUiState(ringtones = items, selectedUri = current, isLoading = false)
        }
    }

    /** Selecting a row also plays a short preview of it, replacing whatever was previewing before. */
    fun selectRingtone(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedUri = uri)
        stopPreview()
        if (uri != null && uri != USE_DEFAULT_SENTINEL) {
            previewRingtone = RingtoneManager.getRingtone(context, uri)?.apply { play() }
        }
    }

    fun stopPreview() {
        previewRingtone?.stop()
        previewRingtone = null
    }

    /** Returns false only in global mode when WRITE_SETTINGS hasn't been granted. */
    fun applySelected(): Boolean {
        val selected = _uiState.value.selectedUri
        if (isContactMode) {
            val id = contactId ?: return false
            val valueToStore = when (selected) {
                USE_DEFAULT_SENTINEL -> null
                null -> Uri.EMPTY // explicit "Silent" marker, distinct from "no override"
                else -> selected
            }
            viewModelScope.launch { contactRepository.updateContactRingtone(id, valueToStore) }
            return true
        }
        if (!Settings.System.canWrite(context)) return false
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, selected)
        return true
    }

    override fun onCleared() {
        super.onCleared()
        stopPreview()
    }

    companion object {
        /**
         * Distinct from both a real ringtone Uri and from null (which means "Silent" — an
         * explicit, playable choice). Only used in contact mode to represent "no override,
         * follow the system default", matching what an absent CUSTOM_RINGTONE column means.
         */
        val USE_DEFAULT_SENTINEL: Uri = Uri.parse("ringtone-sentinel://use-default")
    }
}
