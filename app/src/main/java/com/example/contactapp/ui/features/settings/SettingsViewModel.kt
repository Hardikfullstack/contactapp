package com.example.contactapp.ui.features.settings

import android.accounts.AccountManager
import android.app.role.RoleManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.R
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isDefaultDialer: Boolean = false,
    val contactSortOrder: String = "First Name",
    val appTheme: String = "System",
    val isSyncing: Boolean = false,
    val currentLanguage: String = "System Language"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refreshState()
    }

    fun refreshState() {
        val languageTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val languageDisplayName = if (languageTags.isEmpty()) {
            context.getString(R.string.settings_system_language)
        } else {
            java.util.Locale.forLanguageTag(languageTags).getDisplayLanguage(java.util.Locale.forLanguageTag(languageTags)).replaceFirstChar { it.uppercase() }
        }

        _uiState.value = _uiState.value.copy(
            isDefaultDialer = checkDefaultDialer(),
            contactSortOrder = preferenceManager.getContactSortOrder(),
            appTheme = preferenceManager.getAppTheme(),
            currentLanguage = languageDisplayName
        )
    }

    /**
     * Triggers a real account sync (e.g. Google Contacts) via the OS sync framework —
     * contacts displayed in this app are always live-reflected from the system Contacts
     * Provider (see ContactRepositoryImpl's ContentObserver), so "syncing" doesn't mean
     * refreshing the local list; it means asking the account's sync adapter to reconcile
     * with the server. [onComplete] receives false when there's no syncable account
     * (e.g. purely local/SIM contacts with no signed-in account).
     */
    fun syncContacts(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val accountManager = AccountManager.get(context)
            val syncableAccounts = try {
                accountManager.accounts.filter { account ->
                    // getIsSyncable() returns -1 (not just 0) when Android hasn't resolved the
                    // syncable state for this account+authority yet — a genuinely syncable Google
                    // account can report -1 right after being added. Only an explicit 0 means the
                    // sync adapter itself declared this account non-syncable; requestSync() below
                    // is a safe no-op for any account it can't actually sync.
                    ContentResolver.getIsSyncable(account, ContactsContract.AUTHORITY) != 0
                }
            } catch (e: SecurityException) {
                emptyList()
            }

            if (syncableAccounts.isEmpty()) {
                onComplete(false)
                return@launch
            }

            _uiState.value = _uiState.value.copy(isSyncing = true)

            syncableAccounts.forEach { account ->
                val extras = Bundle().apply {
                    putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                }
                ContentResolver.requestSync(account, ContactsContract.AUTHORITY, extras)
            }

            // requestSync() is fire-and-forget, so poll for it actually finishing
            // (capped so a stuck sync adapter can't hang the spinner forever).
            var waited = 0L
            while (waited < 15_000L && syncableAccounts.any { ContentResolver.isSyncActive(it, ContactsContract.AUTHORITY) }) {
                delay(500)
                waited += 500
            }

            _uiState.value = _uiState.value.copy(isSyncing = false)
            onComplete(true)
        }
    }

    fun openSoundSettings() {
        val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun checkDefaultDialer(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            false
        }
    }

    fun setSortOrder(order: String) {
        preferenceManager.setContactSortOrder(order)
        refreshState()
    }

    fun setTheme(theme: String) {
        preferenceManager.setAppTheme(theme)
        refreshState()
    }
}
