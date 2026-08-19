package com.example.contactapp.ui.features.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.domain.model.Contact
import com.example.contactapp.domain.repository.CallLogRepository
import com.example.contactapp.domain.repository.ContactRepository
import com.example.contactapp.util.PreferenceManager
import com.example.contactapp.util.SimpleVcfParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class ContactsUiState(
    val groupedContacts: Map<Char, List<Contact>> = emptyMap(),
    val isLoading: Boolean = false,
    val showAddContactSheet: Boolean = false,
    val duplicateContact: Contact? = null,
    val pendingContact: Pair<String, String>? = null,
    val selectedIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val callLogRepository: CallLogRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private var allContacts: List<Contact> = emptyList()

    init {
        fetchContacts()
    }

    fun showAddContactSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddContactSheet = show)
    }

    fun toggleSelection(id: String) {
        val currentSelected = _uiState.value.selectedIds.toMutableSet()
        if (currentSelected.contains(id)) {
            currentSelected.remove(id)
        } else {
            currentSelected.add(id)
        }
        
        _uiState.value = _uiState.value.copy(
            selectedIds = currentSelected,
            isSelectionMode = currentSelected.isNotEmpty()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedIds = emptySet(),
            isSelectionMode = false
        )
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedIds
            // 1. Get phone numbers for selected contacts to wipe history
            val selectedNumbers = allContacts.filter { selectedIds.contains(it.id) }.map { it.number }
            
            // 2. Delete call logs for these numbers
            callLogRepository.deleteCallLogsByNumbers(selectedNumbers)
            
            // 3. Delete the contacts themselves
            repository.deleteContactsByIds(selectedIds.toList())
            
            clearSelection()
        }
    }

    fun validateAndSaveContact(name: String, number: String) {
        viewModelScope.launch {
            val existing = repository.findContactByNumber(number)
            if (existing != null && existing.name.lowercase() != name.lowercase()) {
                _uiState.value = _uiState.value.copy(
                    duplicateContact = existing,
                    pendingContact = Pair(name, number)
                )
            } else {
                saveContact(name, number)
            }
        }
    }

    fun confirmSaveDuplicate() {
        val pending = _uiState.value.pendingContact ?: return
        saveContact(pending.first, pending.second)
        clearDuplicateState()
    }

    fun clearDuplicateState() {
        _uiState.value = _uiState.value.copy(
            duplicateContact = null,
            pendingContact = null
        )
    }

    private fun saveContact(name: String, number: String) {
        viewModelScope.launch {
            repository.saveContact(name, number)
            showAddContactSheet(false)
        }
    }

    fun importContactsFromVcf(inputStream: InputStream, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val parser = SimpleVcfParser()
            val contacts = parser.parse(inputStream)
            var count = 0
            contacts.forEach { vContact ->
                val name = vContact.fullName
                val phone = vContact.phoneNumbers.firstOrNull()
                if (!name.isNullOrBlank() && !phone.isNullOrBlank()) {
                    repository.saveContact(name, phone)
                    count++
                }
            }
            onComplete(count)
        }
    }

    suspend fun exportContactsToVcf(outputStream: OutputStream) {
        val contacts = repository.fetchDetailedContacts()
        val vcfBuilder = StringBuilder()

        contacts.forEach { contact ->
            vcfBuilder.append("BEGIN:VCARD\n")
            vcfBuilder.append("VERSION:3.0\n")
            vcfBuilder.append("FN:${contact.name}\n")
            
            // Organizations
            contact.organizations.forEach { org ->
                vcfBuilder.append("ORG:$org")
                if (!contact.department.isNullOrBlank()) {
                    vcfBuilder.append(";${contact.department}")
                }
                vcfBuilder.append("\n")
            }
            if (!contact.jobTitle.isNullOrBlank()) {
                vcfBuilder.append("TITLE:${contact.jobTitle}\n")
            }

            // Phones
            contact.phoneNumbers.forEach { phone ->
                vcfBuilder.append("TEL;TYPE=CELL:$phone\n")
            }

            // Emails
            contact.emails.forEach { email ->
                vcfBuilder.append("EMAIL;TYPE=INTERNET:$email\n")
            }

            // Addresses
            contact.addresses.forEach { addr ->
                vcfBuilder.append("ADR;TYPE=HOME:;;${addr.replace("\n", " ")};;;\n")
            }

            // Other fields
            if (!contact.birthday.isNullOrBlank()) {
                vcfBuilder.append("BDAY:${contact.birthday}\n")
            }
            if (!contact.notes.isNullOrBlank()) {
                vcfBuilder.append("NOTE:${contact.notes.replace("\n", "\\n")}\n")
            }
            contact.nicknames.forEach { nick ->
                vcfBuilder.append("NICKNAME:$nick\n")
            }
            contact.websites.forEach { web ->
                vcfBuilder.append("URL:$web\n")
            }

            vcfBuilder.append("END:VCARD\n")
        }

        try {
            outputStream.write(vcfBuilder.toString().toByteArray())
        } finally {
            outputStream.close()
        }
    }

    private fun fetchContacts() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            combine(
                repository.fetchContacts(),
                callLogRepository.getBlockedNumbers(),
                preferenceManager.sortOrderFlow
            ) { contacts, blockedNumbers, sortOrder ->
                val normalizedBlocked = blockedNumbers.map { it.replace(Regex("[^0-9]"), "").takeLast(10) }
                
                val processed = contacts.map { contact ->
                    val cleanNum = contact.number.replace(Regex("[^0-9]"), "").takeLast(10)
                    contact.copy(isBlocked = cleanNum.isNotEmpty() && normalizedBlocked.contains(cleanNum))
                }
                
                if (sortOrder == "Last Name") {
                    processed.sortedBy { it.name.split(" ").last().lowercase() }
                } else {
                    processed.sortedBy { it.name.lowercase() }
                }
            }.map { sorted ->
                val sortOrder = preferenceManager.getContactSortOrder()
                Pair(sorted, groupContacts(sorted, sortOrder))
            }
            .flowOn(Dispatchers.Default)
            .conflate()
            .collect { (sorted, grouped) ->
                allContacts = sorted
                _uiState.value = _uiState.value.copy(
                    groupedContacts = grouped,
                    isLoading = false
                )
            }
        }
    }

    private fun groupContacts(contacts: List<Contact>, sortOrder: String): Map<Char, List<Contact>> {
        return contacts.groupBy { contact ->
            val name = if (sortOrder == "Last Name") {
                contact.name.split(" ").last()
            } else {
                contact.name
            }
            name.firstOrNull()?.uppercaseChar() ?: '#'
        }.toSortedMap()
    }
}
