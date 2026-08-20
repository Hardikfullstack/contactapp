package com.example.contactapp.ui.features.callreminder

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.domain.model.Contact
import com.example.contactapp.domain.repository.ContactRepository
import com.example.contactapp.service.CallReminderScheduler
import com.example.contactapp.util.CallReminder
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CallReminderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager,
    contactRepository: ContactRepository
) : ViewModel() {

    val reminders: StateFlow<List<CallReminder>> = preferenceManager.callRemindersFlow
        .map { list -> list.sortedBy { it.timeMillis } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<Contact>> = contactRepository.fetchContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addReminder(contact: Contact, timeMillis: Long) {
        val reminder = CallReminder(
            id = System.currentTimeMillis(),
            contactName = contact.name,
            contactNumber = contact.number,
            photoUri = contact.photoUri,
            timeMillis = timeMillis
        )
        preferenceManager.setCallReminders(preferenceManager.getCallReminders() + reminder)
        CallReminderScheduler.schedule(context, reminder)
    }

    fun cancelReminder(reminder: CallReminder) {
        preferenceManager.setCallReminders(preferenceManager.getCallReminders().filterNot { it.id == reminder.id })
        CallReminderScheduler.cancel(context, reminder)
    }
}
